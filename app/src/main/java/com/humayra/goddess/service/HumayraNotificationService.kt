package com.humayra.goddess.service

import android.app.*
import android.content.*
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.humayra.goddess.*
import com.humayra.goddess.ai.HybridBrain
import com.humayra.goddess.storage.*
import com.humayra.goddess.ui.MainActivity
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

data class CachedReply(val sender: String, val msg: String, val pi: PendingIntent, val ri: RemoteInput, val isGroup: Boolean)

class HumayraNotificationService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn?.notification ?: return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0 || (n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        val ex = n.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() || text.isBlank()) return
        MemoryStore.pushEvent("Notif [$pkg]: $title - ${text.take(60)}")
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return
        if (!AppConfig(this).whatsappAssistant) return
        val replyAct = n.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() } ?: return
        val ri = replyAct.remoteInputs.first()
        val isGrp = ex.containsKey(Notification.EXTRA_CONVERSATION_TITLE) || text.contains(":")
        val id = "wa_${System.currentTimeMillis()}"
        pending[id] = CachedReply(title, text, replyAct.actionIntent, ri, isGrp)
        scope.launch {
            val draft = HybridBrain.draftWhatsAppReply(this@HumayraNotificationService, title, text, isGrp)
            if (draft.isNotBlank()) showDraftNotif(id, title, text, draft, pkg == "com.whatsapp.w4b")
        }
    }
    private fun showDraftNotif(id: String, sender: String, msg: String, draft: String, isBiz: Boolean) {
        val nid = (id.hashCode() and 0x7FFF) + 5000
        val sendPi = PendingIntent.getBroadcast(this, nid + 1, Intent(this, WhatsAppActionReceiver::class.java)
            .setAction("SEND").putExtra("id", id).putExtra("draft", draft).putExtra("nid", nid), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val ignPi = PendingIntent.getBroadcast(this, nid + 2, Intent(this, WhatsAppActionReceiver::class.java)
            .setAction("IGNORE").putExtra("id", id).putExtra("nid", nid), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val editPi = PendingIntent.getActivity(this, nid + 3, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("edit_id", id).putExtra("edit_sender", sender).putExtra("edit_msg", msg).putExtra("edit_draft", draft), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val nb = NotificationCompat.Builder(this, HumayraApp.CH_WHATSAPP).setSmallIcon(R.drawable.ic_humayra_emblem)
            .setContentTitle("${if (isBiz) "WA Biz" else "WhatsApp"}: $sender")
            .setContentText(draft).setStyle(NotificationCompat.BigTextStyle().bigText("Msg: $msg\n\nDraft: $draft"))
            .addAction(0, "✅ Send Reply", sendPi).addAction(0, "✏️ Edit", editPi).addAction(0, "❌ Ignore", ignPi).setAutoCancel(true).build()
        try { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(nid, nb) } catch (_: SecurityException) {}
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        val pending = ConcurrentHashMap<String, CachedReply>()
        fun sendReplyNow(ctx: Context, id: String, text: String): Boolean {
            val c = pending.remove(id) ?: return false
            return try {
                val fill = Intent(); val b = Bundle().apply { putCharSequence(c.ri.resultKey, text) }
                RemoteInput.addResultsToIntent(arrayOf(c.ri), fill, b); c.pi.send(ctx, 0, fill); true
            } catch (_: Throwable) { false }
        }
    }
}
