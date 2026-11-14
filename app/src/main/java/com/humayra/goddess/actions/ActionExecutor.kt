package com.humayra.goddess.actions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.humayra.goddess.service.HumayraAccessibilityService
import com.humayra.goddess.storage.MemoryStore
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

interface ActionCallbacks {
    fun onPose(pose: String)
    fun onWatchScreen(prompt: String)
    fun onConfirmRequired(label: String, executeConfirmed: () -> Unit)
    fun onWhatsAppDraftUpdate(replyId: String, message: String)
}
object ActionExecutor {
    private var torchOn = false

    suspend fun executeAll(ctx: Context, actions: List<JSONObject>, cb: ActionCallbacks): List<String> {
        val logs = mutableListOf<String>()
        for (a in actions) {
            logs.add(runOne(ctx, a, cb))
            delay(350)
        }
        return logs
    }

    private fun findPackage(ctx: Context, query: String): String? {
        val q = query.trim().lowercase()
        val aliases = mapOf("firefox" to "org.mozilla.firefox", "chrome" to "com.android.chrome", "whatsapp" to "com.whatsapp", "whatsapp business" to "com.whatsapp.w4b", "youtube" to "com.google.android.youtube")
        aliases[q]?.let { if (ctx.packageManager.getLaunchIntentForPackage(it) != null) return it }
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
        return apps.firstOrNull { it.loadLabel(pm).toString().equals(q, true) }?.activityInfo?.packageName
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) || it.activityInfo.packageName.lowercase().contains(q) }?.activityInfo?.packageName
    }

    private fun openApp(ctx: Context, name: String): String {
        val pkg = findPackage(ctx, name) ?: return "অ্যাপ পাওয়া যায়নি: $name"
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg)?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) } ?: return "লঞ্চ ব্যর্থ: $pkg"
        ctx.startActivity(i); return "Opened $name"
    }
    private suspend fun runOne(ctx: Context, a: JSONObject, cb: ActionCallbacks): String = when (a.optString("type")) {
        "open_app" -> openApp(ctx, a.optString("app"))
        "vpn_toggle" -> {
            val vpnApp = a.optString("app").ifBlank { listOf("warp", "proton", "vpn", "windscribe", "psiphon", "nekobox").firstOrNull { findPackage(ctx, it) != null } ?: "vpn" }
            val r = openApp(ctx, vpnApp); delay(1400)
            val acc = HumayraAccessibilityService.instance
            val btns = if (a.optString("state", "on") == "off") listOf("Disconnect", "Stop", "Turn off") else listOf("Connect", "Quick Connect", "Start", "Tap to connect", "Enable")
            val tapped = btns.any { acc?.tapByText(it) == true }
            "$r (${if (tapped) "VPN button tapped" else "tap manually"})"
        }
        "browser_search" -> {
            val q = a.optString("query"); val url = if (q.startsWith("http") || (!q.contains(" ") && q.contains("."))) (if (q.startsWith("http")) q else "https://$q") else "https://www.google.com/search?q=${URLEncoder.encode(q, "UTF-8")}"
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            when (a.optString("browser", "default").lowercase()) {
                "firefox" -> findPackage(ctx, "firefox")?.let { i.setPackage(it) }
                "chrome" -> findPackage(ctx, "chrome")?.let { i.setPackage(it) }
            }
            ctx.startActivity(i); "Searched: $q"
        }
        "ui_tap" -> if (HumayraAccessibilityService.instance?.tapByText(a.optString("text")) == true) "Tapped" else "Not found"
        "ui_type" -> if (HumayraAccessibilityService.instance?.typeText(a.optString("text")) == true) "Typed" else "No input field"
        "ui_scroll" -> if (HumayraAccessibilityService.instance?.scroll(a.optString("direction", "down")) == true) "Scrolled" else "Accessibility off"
        "ui_global" -> if (HumayraAccessibilityService.instance?.globalAct(a.optString("action")) == true) "Global OK" else "Accessibility off"
        "flashlight" -> toggleTorch(ctx, a.optString("state", "toggle"))
        "volume" -> setVol(ctx, a.optString("stream", "media"), a.optInt("level_percent", 50))
        "media" -> mediaKey(ctx, a.optString("command", "play_pause"))
        "alarm" -> {
            ctx.startActivity(Intent(AlarmClock.ACTION_SET_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(AlarmClock.EXTRA_HOUR, a.optInt("hour", 7)).putExtra(AlarmClock.EXTRA_MINUTES, a.optInt("minute", 0))
                .putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label", "HUMAYRA")).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
            "Alarm set"
        }
        "timer" -> {
            ctx.startActivity(Intent(AlarmClock.ACTION_SET_TIMER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(AlarmClock.EXTRA_LENGTH, a.optInt("seconds", 60)).putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label", "HUMAYRA")).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
            "Timer set"
        }
        "call" -> {
            val target = a.optString("target"); val num = resolvePhone(ctx, target)
            cb.onConfirmRequired("কল করবেন: $target ($num)?") {
                val hasCall = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                ctx.startActivity(Intent(if (hasCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:$num")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }; "Waiting call confirm"
        }
        "reminder" -> {
            val m = a.optInt("delay_minutes", 15).coerceAtLeast(1); val msg = a.optString("message", "Reminder")
            MemoryStore(ctx).addReminder(m, msg)
            WorkManager.getInstance(ctx).enqueue(OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(m.toLong(), TimeUnit.MINUTES).setInputData(Data.Builder().putString("msg", msg).build()).build())
            "Reminder in ${m}m"
        }
        "remember" -> { MemoryStore(ctx).addMemory(a.optString("fact")); "Remembered" }
        "pose" -> { cb.onPose(a.optString("pose", "wave")); "Pose updated" }
        "watch_screen" -> { cb.onWatchScreen(a.optString("prompt", "Analyze screen")); "Watching screen" }
        "whatsapp_draft" -> { cb.onWhatsAppDraftUpdate(a.optString("reply_id"), a.optString("message")); "Draft ready" }
        else -> "Unknown action"
    }
    private fun toggleTorch(ctx: Context, st: String): String = try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } ?: return "No flash"
        torchOn = when (st.lowercase()) { "on" -> true; "off" -> false; else -> !torchOn }
        cm.setTorchMode(id, torchOn); "Torch ${if (torchOn) "ON" else "OFF"}"
    } catch (e: Exception) { "Torch error: ${e.message}" }

    private fun setVol(ctx: Context, s: String, pct: Int): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val st = when (s.lowercase()) { "ring" -> AudioManager.STREAM_RING; "alarm" -> AudioManager.STREAM_ALARM; else -> AudioManager.STREAM_MUSIC }
        val max = am.getStreamMaxVolume(st)
        am.setStreamVolume(st, ((pct.coerceIn(0, 100) / 100f) * max).toInt(), 0)
        return "Volume $s -> $pct%"
    }

    private fun mediaKey(ctx: Context, cmd: String): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val code = when (cmd.lowercase()) { "next" -> KeyEvent.KEYCODE_MEDIA_NEXT; "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS; "stop" -> KeyEvent.KEYCODE_MEDIA_STOP; else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE }
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return "Media: $cmd"
    }
    private fun resolvePhone(ctx: Context, q: String): String {
        val digits = q.replace(Regex("[^0-9+]"), "")
        if (digits.length >= 3 && q.none { it.isLetter() }) return digits
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return q
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%${q.trim()}%"), null
        )?.use { c -> if (c.moveToFirst()) return c.getString(0) ?: q }
        return q
    }
}
