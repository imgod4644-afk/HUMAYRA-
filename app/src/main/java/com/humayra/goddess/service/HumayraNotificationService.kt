package com.humayra.goddess.service

import android.app.*
import android.content.*
import android.os.Bundle
import android.service.notification.*
import androidx.core.app.NotificationCompat
import com.humayra.goddess.*
import com.humayra.goddess.ai.HybridBrain
import com.humayra.goddess.security.CyberShield
import com.humayra.goddess.storage.*
import com.humayra.goddess.ui.MainActivity
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

data class CachedReply(val sender: String, val msg: String, val pi: PendingIntent, val ri: RemoteInput, val isGroup: Boolean)

class HumayraNotificationService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val cfg = AppConfig(this); if (!cfg.masterEnabled) return
        val n = sbn?.notification ?: return; val pkg = sbn.packageName ?: return; if (pkg == packageName) return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0 || (n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        val ex = n.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString() ?: ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() || text.isBlank()) return
        if (CyberShield.isSensitiveScreen(pkg, "$title $text", cfg.blacklistSet()) || AppPresetStore(this).find(pkg)?.blindPrivacy == true) return
        val appLbl = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        NotificationVault(this).add(VaultNotif(pkg, appLbl, title, text, System.currentTimeMillis()))
        CyberShield.checkPhishingUrl(text)?.let { MemoryStore.pushEvent("$it ($appLbl)") }
        if (AppPresetStore(this).find(HumayraAccessibilityService.activePackage)?.hideNotifs == true) runCatching { cancelNotification(sbn.key) }
        MemoryStore.pushEvent("Notif [$appLbl]: $title")
        if ((pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") || !cfg.whatsappAssistant) return
        val act = n.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() } ?: return
        val id = "wa_${System.currentTimeMillis()}"; pending[id] = CachedReply(title, text, act.actionIntent, act.remoteInputs.first(), ex.containsKey(Notification.EXTRA_CONVERSATION_TITLE))
        scope.launch { val d = HybridBrain.draftWhatsAppReply(this@HumayraNotificationService, title, text, pending[id]?.isGroup == true); if (d.isNotBlank()) showDraftNotif(id, title, text, d, pkg == "com.whatsapp.w4b") }
    }
    private fun showDraftNotif(id: String, sender: String, msg: String, draft: String, isBiz: Boolean) {
        val nid = (id.hashCode() and 0x7FFF) + 5000
        val sendPi = PendingIntent.getBroadcast(this, nid + 1, Intent(this, WhatsAppActionReceiver::class.java).setAction("SEND").putExtra("id", id).putExtra("draft", draft).putExtra("nid", nid), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val ignPi = PendingIntent.getBroadcast(this, nid + 2, Intent(this, WhatsAppActionReceiver::class.java).setAction("IGNORE").putExtra("id", id).putExtra("nid", nid), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val editPi = PendingIntent.getActivity(this, nid + 3, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("edit_id", id).putExtra("edit_sender", sender).putExtra("edit_draft", draft), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val vis = if (AppConfig(this).lockScreenPrivacy) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC
        val nb = NotificationCompat.Builder(this, HumayraApp.CH_WHATSAPP).setSmallIcon(R.drawable.ic_humayra_emblem)
            .setContentTitle("${if (isBiz) "WA Biz" else "WhatsApp"}: $sender").setContentText(draft).setVisibility(vis)
            .addAction(0, "✅ Send", sendPi).addAction(0, "✏️ Edit", editPi).addAction(0, "❌ Ignore", ignPi).setAutoCancel(true).build()
        try { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(nid, nb) } catch (_: SecurityException) {}
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    companion object {
        val pending = ConcurrentHashMap<String, CachedReply>()
        fun sendReplyNow(ctx: Context, id: String, text: String): Boolean {
            val c = pending.remove(id) ?: return false
            return runCatching { val fill = Intent(); RemoteInput.addResultsToIntent(arrayOf(c.ri), fill, Bundle().apply { putCharSequence(c.ri.resultKey, text) }); c.pi.send(ctx, 0, fill); true }.getOrDefault(false)
        }
    }
}
