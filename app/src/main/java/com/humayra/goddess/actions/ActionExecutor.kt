package com.humayra.goddess.actions

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.hardware.camera2.*
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.*
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import androidx.work.*
import com.humayra.goddess.service.HumayraAccessibilityService
import com.humayra.goddess.shizuku.ShizukuBridge
import com.humayra.goddess.storage.*
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

interface ActionCallbacks {
    fun onPose(pose: String)
    fun onWatchScreen(prompt: String)
    fun onConfirmRequired(label: String, executeConfirmed: () -> Unit)
    fun onWhatsAppDraftUpdate(replyId: String, message: String)
    fun onPreviewHtml(title: String, html: String)
    fun onDownloadAsset(kind: String)
}
object ActionExecutor {
    private var torchOn = false

    suspend fun executeAll(ctx: Context, actions: List<JSONObject>, cb: ActionCallbacks): List<String> =
        actions.map { a -> runOne(ctx, a, cb).also { delay(300) } }

    private fun findPackage(ctx: Context, query: String): String? { FuzzyMatch.bestPackage(ctx, query)?.let { return it };
        val q = query.trim().lowercase()
        val map = mapOf("firefox" to "org.mozilla.firefox", "chrome" to "com.android.chrome", "whatsapp" to "com.whatsapp",
            "whatsapp business" to "com.whatsapp.w4b", "youtube" to "com.google.android.youtube", "proton vpn" to "ch.protonvpn.android")
        map[q]?.let { if (ctx.packageManager.getLaunchIntentForPackage(it) != null) return it }
        val pm = ctx.packageManager; val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        return apps.firstOrNull { it.loadLabel(pm).toString().equals(q, true) }?.activityInfo?.packageName
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) || it.activityInfo.packageName.lowercase().contains(q) }?.activityInfo?.packageName
    }

    private fun openApp(ctx: Context, name: String): String {
        val pkg = findPackage(ctx, name) ?: return "❌ অ্যাপ পাওয়া যায়নি: $name"
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return "❌ খোলা যায়নি: $pkg"
        ctx.startActivity(i); return "✅ Opened $name"
    }
    private suspend fun runOne(ctx: Context, a: JSONObject, cb: ActionCallbacks): String = when (a.optString("type")) {
        "open_app" -> openApp(ctx, a.optString("app"))
        "vpn_toggle" -> {
            val app = a.optString("app").ifBlank { listOf("proton", "warp", "vpn", "windscribe", "psiphon").firstOrNull { findPackage(ctx, it) != null } ?: "proton vpn" }
            val r = openApp(ctx, app); if (r.startsWith("❌")) return r
            val acc = HumayraAccessibilityService.instance ?: return "$r — ⚠️ Accessibility বন্ধ থাকায় Connect বাটন চাপা যায়নি (Settings থেকে অন করুন)।"
            val btns = if (a.optString("state", "on") == "off") listOf("Disconnect", "Stop", "Cancel", "Turn off") else listOf("Quick Connect", "Connect", "Start", "Tap to connect", "Enable")
            val tapped = acc.waitAndTapAny(btns, 7500L)
            if (tapped != null) "✅ $app -> Tapped '$tapped'" else "⚠️ $app ওপেন হয়েছে, কিন্তু '${btns.first()}' বাটন স্ক্রিনে পাওয়া যায়নি।"
        }
        "youtube_play" -> {
            val q = a.optString("query"); val u = Uri.parse("https://www.youtube.com/results?search_query=${URLEncoder.encode(q, "UTF-8")}")
            val i = Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            findPackage(ctx, "youtube")?.let { i.setPackage(it) }; ctx.startActivity(i)
            "✅ YouTube-এ '$q' সার্চ করা হয়েছে"
        }
        "browser_search" -> {
            val q = a.optString("query"); val u = if (q.startsWith("http") || (!q.contains(" ") && q.contains("."))) (if (q.startsWith("http")) q else "https://$q") else "https://www.google.com/search?q=${URLEncoder.encode(q, "UTF-8")}"
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            when (a.optString("browser", "default").lowercase()) { "firefox" -> findPackage(ctx, "firefox")?.let { i.setPackage(it) }; "chrome" -> findPackage(ctx, "chrome")?.let { i.setPackage(it) } }
            ctx.startActivity(i); "✅ Searched: $q"
        }
        "wifi" -> {
            val on = a.optString("state", "on") != "off"; val ssid = a.optString("ssid")
            if (ShizukuBridge.hasPermission()) {
                val res = if (on && ssid.isNotBlank()) ShizukuBridge.connectSavedWifi(ctx, ssid, AppConfig(ctx).savedWifiPass) else ShizukuBridge.setWifi(ctx, on)
                if (res.isSuccess) "✅ Shizuku Wi-Fi ${if (on) "ON" else "OFF"}" else "❌ Shizuku err: ${res.exceptionOrNull()?.message}"
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ctx.startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "⚠️ Shizuku অফ থাকায় Wi-Fi প্যানেল খোলা হয়েছে।"
            }
        }
        "bluetooth" -> {
            val on = a.optString("state", "on") != "off"
            if (ShizukuBridge.hasPermission()) { ShizukuBridge.setBluetooth(ctx, on); "✅ Shizuku Bluetooth ${if (on) "ON" else "OFF"}" }
            else { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "⚠️ Shizuku অফ থাকায় Bluetooth সেটিংস খোলা হয়েছে।" }
        }
        "brightness" -> {
            val pct = a.optInt("level_percent", 60).coerceIn(5, 100)
            if (ShizukuBridge.hasPermission()) { ShizukuBridge.setBrightness(ctx, pct); "✅ Shizuku Brightness $pct%" }
            else if (Settings.System.canWrite(ctx)) {
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, ((pct / 100f) * 255).toInt())
                "✅ Brightness $pct%"
            } else { ctx.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "⚠️ Modify Settings অনুমতি দিন।" }
        }
        "shizuku_cmd" -> if (ShizukuBridge.hasPermission()) ShizukuBridge.runShell(ctx, a.optString("cmd")).map { it.ifBlank { "✅ Command Executed" } }.getOrElse { "❌ " + (it.message ?: "err") }.take(240) else "❌ Shizuku অফ।"
        "create_website" -> saveAndPreviewSite(ctx, a.optString("name", "site"), a.optString("html"), cb)
        "learn_skill" -> { MemoryStore(ctx).saveSkill(a.optString("trigger"), a.optString("actions_json", "[]")); "✅ Skill Learned: ${a.optString("trigger")}" }
        "auto_download" -> { cb.onDownloadAsset(a.optString("kind", "avatar")); "⏳ Download started" }
        "ui_tap" -> if (HumayraAccessibilityService.instance?.tapByText(a.optString("text")) == true) "✅ Tapped" else "❌ Not found (Accessibility/Text check)"
        "ui_type" -> if (HumayraAccessibilityService.instance?.typeText(a.optString("text")) == true) "✅ Typed" else "❌ No input field"
        "ui_scroll" -> if (HumayraAccessibilityService.instance?.scroll(a.optString("direction", "down")) == true) "✅ Scrolled" else "❌ Accessibility off"
        "ui_global" -> if (HumayraAccessibilityService.instance?.globalAct(a.optString("action")) == true) "✅ Global OK" else "❌ Accessibility off"
        "flashlight" -> toggleTorch(ctx, a.optString("state", "toggle"))
        "volume" -> setVol(ctx, a.optString("stream", "media"), a.optInt("level_percent", 50))
        "media" -> mediaKey(ctx, a.optString("command", "play_pause"))
        "alarm" -> { ctx.startActivity(Intent(AlarmClock.ACTION_SET_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(AlarmClock.EXTRA_HOUR, a.optInt("hour", 7)).putExtra(AlarmClock.EXTRA_MINUTES, a.optInt("minute", 0)).putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label", "HUMAYRA")).putExtra(AlarmClock.EXTRA_SKIP_UI, true)); "✅ Alarm set" }
        "timer" -> { ctx.startActivity(Intent(AlarmClock.ACTION_SET_TIMER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(AlarmClock.EXTRA_LENGTH, a.optInt("seconds", 60)).putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label", "HUMAYRA")).putExtra(AlarmClock.EXTRA_SKIP_UI, true)); "✅ Timer set" }
        "call" -> { val tg = a.optString("target"); val num = resolvePhone(ctx, tg); cb.onConfirmRequired("কল করবেন: $tg ($num)?") { val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED; ctx.startActivity(Intent(if (ok) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:$num")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; "⏳ Confirm call" }
        "reminder" -> { val m = a.optInt("delay_minutes", 15).coerceAtLeast(1); val msg = a.optString("message", "Reminder"); MemoryStore(ctx).addReminder(m, msg); WorkManager.getInstance(ctx).enqueue(OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(m.toLong(), TimeUnit.MINUTES).setInputData(Data.Builder().putString("msg", msg).build()).build()); "✅ Reminder in ${m}m" }
        "remember" -> { MemoryStore(ctx).addMemory(a.optString("fact")); "✅ Remembered" }
        "pose" -> { cb.onPose(a.optString("pose", "wave")); "✅ Pose updated" }
        "watch_screen" -> { cb.onWatchScreen(a.optString("prompt", "Analyze screen")); "✅ Watching screen" }
        "whatsapp_draft" -> { cb.onWhatsAppDraftUpdate(a.optString("reply_id"), a.optString("message")); "✅ Draft ready" }
        "device_control" -> deviceControl(ctx, a.optString("setting"), a.optString("value", "on"))
        "wait_ms" -> { delay(a.optLong("ms", 700L).coerceIn(100L, 5000L)); "✅ Waited" }
        "open_intent" -> runCatching {
            val act = a.optString("action").ifBlank { Intent.ACTION_VIEW }
            val i = Intent(act).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            a.optString("uri").takeIf { it.isNotBlank() }?.let { i.data = Uri.parse(it) }
            a.optString("pkg").takeIf { it.isNotBlank() }?.let { i.setPackage(it) }
            ctx.startActivity(i); "✅ Opened $act"
        }.getOrElse { "❌ Intent err: ${it.message}" }
        else -> "⚠️ Unknown tool: ${a.optString("type")}"
    }
    private fun saveAndPreviewSite(ctx: Context, name: String, html: String, cb: ActionCallbacks): String {
        val safe = name.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "page" }
        val body = html.ifBlank { "<html><body style='background:#000;color:#fff'><h2>HUMAYRA Site</h2></body></html>" }
        val dir = try { File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "HUMAYRA-Projects").apply { mkdirs() } } catch (_: Exception) { File(ctx.filesDir, "projects").apply { mkdirs() } }
        val f = File(dir, "$safe.html"); runCatching { f.writeText(body) }
        cb.onPreviewHtml("$safe.html", body); return "✅ Website saved: ${f.name}"
    }
    private fun toggleTorch(ctx: Context, st: String) = try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } ?: return "❌ No flash"
        torchOn = when (st.lowercase()) { "on" -> true; "off" -> false; else -> !torchOn }
        cm.setTorchMode(id, torchOn); "✅ Torch ${if (torchOn) "ON" else "OFF"}"
    } catch (e: Exception) { "❌ Torch err: ${e.message}" }
    private fun setVol(ctx: Context, s: String, pct: Int) = try {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val st = when (s.lowercase()) { "ring" -> AudioManager.STREAM_RING; "alarm" -> AudioManager.STREAM_ALARM; else -> AudioManager.STREAM_MUSIC }
        am.setStreamVolume(st, ((pct.coerceIn(0, 100) / 100f) * am.getStreamMaxVolume(st)).toInt(), 0); "✅ Volume $s -> $pct%"
    } catch (e: Exception) { "❌ Vol err: ${e.message}" }
    private fun mediaKey(ctx: Context, cmd: String): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val code = when (cmd.lowercase()) { "next" -> KeyEvent.KEYCODE_MEDIA_NEXT; "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS; "stop" -> KeyEvent.KEYCODE_MEDIA_STOP; else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE }
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code)); return "✅ Media: $cmd"
    }
    private fun resolvePhone(ctx: Context, q: String): String {
        val digits = q.replace(Regex("[^0-9+]"), ""); if (digits.length >= 3 && q.none { it.isLetter() }) return digits
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return q
        ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%${q.trim()}%"), null)?.use { c -> if (c.moveToFirst()) return c.getString(0) ?: q }
        return q
    }
    private suspend fun deviceControl(ctx: Context, s: String, v: String): String {
        val k = s.trim().lowercase(); val on = !v.equals("off", true) && !v.equals("false", true) && v != "0"
        val sh = ShizukuBridge.hasPermission()
        return when (k) {
            "mobile_data", "data", "cellular" -> if (sh) ShizukuBridge.setData(ctx, on).fold({ "✅ Mobile Data ${if (on) "ON" else "OFF"}" }, { "❌ ${it.message}" }) else { openSettingsFallback(ctx, Settings.ACTION_DATA_ROAMING_SETTINGS); "⚠️ Shizuku অফ — Data Settings খোলা হলো" }
            "wifi" -> if (sh) ShizukuBridge.setWifi(ctx, on).fold({ "✅ Wi-Fi ${if (on) "ON" else "OFF"}" }, { "❌ ${it.message}" }) else "⚠️ Shizuku অফ"
            "bluetooth", "bt" -> if (sh) ShizukuBridge.setBluetooth(ctx, on).fold({ "✅ Bluetooth ${if (on) "ON" else "OFF"}" }, { "❌ ${it.message}" }) else "⚠️ Shizuku অফ"
            "dark_mode", "dark" -> if (sh) ShizukuBridge.setDarkMode(ctx, on).fold({ "✅ Dark Mode ${if (on) "ON" else "OFF"}" }, { "❌ ${it.message}" }) else "⚠️ Shizuku অফ"
            "dnd" -> if (sh) ShizukuBridge.setDnd(ctx, on).fold({ "✅ DND ${if (on) "ON" else "OFF"}" }, { "❌ ${it.message}" }) else "⚠️ Shizuku অফ"
            "auto_rotate", "rotate" -> if (sh) ShizukuBridge.setAutoRotate(ctx, on).fold({ "✅ Auto-Rotate ${if (on) "ON" else "OFF"}" }, { "❌ ${it.message}" }) else "⚠️ Shizuku অফ"
            "battery_saver" -> if (sh) ShizukuBridge.setBatterySaver(ctx, on).fold({ "✅ Battery Saver ${if (on) "ON" else "OFF"}" }, { "❌ ${it.message}" }) else "⚠️ Shizuku অফ"
            "airplane" -> if (sh) ShizukuBridge.setAirplane(ctx, on).fold({ "✅ Airplane ${if (on) "ON" else "OFF"}" }, { "❌ ${it.message}" }) else "⚠️ Shizuku অফ"
            "hotspot" -> { openSettingsFallback(ctx, "android.settings.TETHER_SETTINGS"); "✅ Hotspot সেটিংস খোলা হয়েছে" }
            "brightness" -> if (sh) { ShizukuBridge.setBrightness(ctx, v.toIntOrNull() ?: 60); "✅ Brightness $v%" } else "⚠️ Shizuku অফ"
            "volume" -> setVol(ctx, "media", v.toIntOrNull() ?: 60)
            "timeout" -> if (sh) { ShizukuBridge.setScreenTimeoutSec(ctx, v.toIntOrNull() ?: 60); "✅ Timeout ${v}s" } else "⚠️ Shizuku অফ"
            "force_stop" -> if (sh) { val p = findPackage(ctx, v) ?: v; ShizukuBridge.forceStop(ctx, p); "✅ Stopped $p" } else "⚠️ Shizuku অফ"
            else -> if (sh) ShizukuBridge.runShell(ctx, "$s $v").getOrElse { it.message ?: "err" } else "⚠️ Shizuku অফ"
        }
    }
    private fun openSettingsFallback(ctx: Context, act: String) = runCatching { ctx.startActivity(Intent(act).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
