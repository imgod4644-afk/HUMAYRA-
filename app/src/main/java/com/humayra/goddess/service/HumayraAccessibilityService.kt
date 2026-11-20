package com.humayra.goddess.service

import android.accessibilityservice.*
import android.content.*
import android.graphics.*
import android.media.AudioManager
import android.net.*
import android.os.*
import android.provider.Settings
import android.view.Display
import android.view.accessibility.*
import androidx.core.content.ContextCompat
import com.humayra.goddess.security.CyberShield
import com.humayra.goddess.shizuku.ShizukuBridge
import com.humayra.goddess.storage.*
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

class HumayraAccessibilityService : AccessibilityService() {
    private val svcScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onServiceConnected() { instance = this }
    override fun onDestroy() { svcScope.cancel(); instance = null; super.onDestroy() }
    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { runCatching { handleAccEvent(event) } }
    private fun handleAccEvent(event: AccessibilityEvent?) {
        val cfg = AppConfig(this); if (!cfg.masterEnabled) return
        val pkg = event?.packageName?.toString() ?: return
        val pre = getCachedPreset(this, pkg)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !shouldIgnore(pkg) && pkg != lastFgPkg) {
            val prevPkg = lastFgPkg; val prevPre = lastFgPreset
            lastFgPkg = pkg; lastFgPreset = if (pre?.enabled == true) pre else null
            activePresetLabel = lastFgPreset?.label.orEmpty()
            svcScope.launch { onAppSwitch(this@HumayraAccessibilityService, cfg, prevPkg, prevPre, pkg, lastFgPreset) }
        }
        if (CyberShield.isSensitiveScreen(pkg, activeScreenText, cfg.blacklistSet()) || pre?.blindPrivacy == true) {
            activePackage = "PRIVATE_SHIELD"; activeScreenText = ""; return
        }
        activePackage = pkg
        if (shouldIgnore(pkg) || pkg == packageName) return
        val now = System.currentTimeMillis(); if (now - lastReadMs < 1200L) return; lastReadMs = now
        val sb = StringBuilder(); collectText(rootInActiveWindow, sb, 0)
        if (sb.isNotEmpty()) activeScreenText = sb.toString().take(900)
    }
    private fun shouldIgnore(pkg: String) = pkg == "com.android.systemui" || pkg.contains("inputmethod", true) || pkg.contains("keyboard", true) || pkg.contains("permissioncontroller", true) || pkg.contains("packageinstaller", true) || (pkg == packageName && FloatingOverlayService.isRunning)
    private fun collectText(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null || depth > 14 || sb.length > 850 || node.isPassword) return
        val t = node.text?.toString()?.trim().orEmpty(); val d = node.contentDescription?.toString()?.trim().orEmpty()
        if (t.isNotEmpty()) sb.append(t).append(" | ") else if (d.isNotEmpty()) sb.append(d).append(" | ")
        for (i in 0 until node.childCount) collectText(node.getChild(i), sb, depth + 1)
    }
    fun tapByText(target: String): Boolean {
        val root = rootInActiveWindow ?: return false; val q = target.trim(); if (q.isEmpty()) return false
        val node = findNode(root, q, false) ?: findNode(root, q, true) ?: return false
        var cur: AccessibilityNodeInfo? = node
        repeat(8) { if (cur?.isClickable == true && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true; cur = cur?.parent }
        val r = Rect(); node.getBoundsInScreen(r); if (r.width() <= 0 || r.height() <= 0) return false
        val path = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 70)).build(), null, null)
    }
    suspend fun waitAndTapAny(targets: List<String>, maxMs: Long = 7500L): String? {
        val end = System.currentTimeMillis() + maxMs
        while (System.currentTimeMillis() < end) { for (t in targets) if (tapByText(t)) return t; delay(400) }
        return null
    }
    private fun findNode(n: AccessibilityNodeInfo?, q: String, partial: Boolean): AccessibilityNodeInfo? {
        if (n == null) return null; val t = n.text?.toString().orEmpty(); val d = n.contentDescription?.toString().orEmpty()
        if (if (partial) t.contains(q, true) || d.contains(q, true) else t.equals(q, true) || d.equals(q, true)) return n
        for (i in 0 until n.childCount) findNode(n.getChild(i), q, partial)?.let { return it }
        return null
    }
    fun typeText(txt: String): Boolean {
        val root = rootInActiveWindow ?: return false; val target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditable(root) ?: return false
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, txt) }
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (ok && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        return ok
    }
    private fun findEditable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null; if (n.isEditable) return n
        for (i in 0 until n.childCount) findEditable(n.getChild(i))?.let { return it }; return null
    }
    fun scroll(dir: String): Boolean {
        val dm = resources.displayMetrics; val x = dm.widthPixels * 0.5f; val down = dir.equals("down", true)
        val path = Path().apply { moveTo(x, dm.heightPixels * (if (down) 0.72f else 0.28f)); lineTo(x, dm.heightPixels * (if (down) 0.28f else 0.72f)) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 280)).build(), null, null)
    }
    fun globalAct(name: String) = performGlobalAction(when (name.lowercase()) {
        "home" -> GLOBAL_ACTION_HOME; "back" -> GLOBAL_ACTION_BACK; "recents" -> GLOBAL_ACTION_RECENTS
        "notifications" -> GLOBAL_ACTION_NOTIFICATIONS; else -> GLOBAL_ACTION_QUICK_SETTINGS
    })
    fun captureScreenJpeg(maxW: Int, cb: (ByteArray?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) { cb(null); return }
        takeScreenshot(Display.DEFAULT_DISPLAY, ContextCompat.getMainExecutor(this), object : TakeScreenshotCallback {
            override fun onSuccess(res: ScreenshotResult) {
                try {
                    val hw = Bitmap.wrapHardwareBuffer(res.hardwareBuffer, res.colorSpace); val soft = hw?.copy(Bitmap.Config.ARGB_8888, false); hw?.recycle(); res.hardwareBuffer.close()
                    if (soft == null) { cb(null); return }; val ratio = if (soft.width > maxW) maxW.toFloat() / soft.width else 1f
                    val sc = if (ratio < 1f) Bitmap.createScaledBitmap(soft, maxW, (soft.height * ratio).toInt(), true).also { soft.recycle() } else soft
                    val out = ByteArrayOutputStream().also { sc.compress(Bitmap.CompressFormat.JPEG, 72, it) }; sc.recycle(); cb(out.toByteArray())
                } catch (_: Throwable) { cb(null) }
            }
            override fun onFailure(err: Int) = cb(null)
        })
    }
    companion object {
        @Volatile var instance: HumayraAccessibilityService? = null; @Volatile var activePackage = ""
        @Volatile var activeScreenText = ""; @Volatile var activePresetLabel = ""
        private var lastReadMs = 0L; private var lastFgPkg = ""; private var lastFgPreset: AppPreset? = null
        private var presetMap = emptyMap<String, AppPreset>(); private var presetCacheMs = 0L
        private var savedVol: Int? = null; private var savedBri: Int? = null; private var savedBriMode: Int? = null
        private var startedOverlay = false; private var changedDnd = false; private var vpnBusyUntil = 0L
        fun getActiveScreenSummary() = if (activePackage.isBlank()) "Idle" else "App: $activePackage | Screen: ${activeScreenText.take(360)}"
        fun invalidatePresetCache() { presetCacheMs = 0L }
        private fun getCachedPreset(ctx: Context, pkg: String): AppPreset? {
            val now = System.currentTimeMillis()
            if (now - presetCacheMs > 3000L || presetCacheMs == 0L) {
                presetMap = AppPresetStore(ctx).getAll().associateBy { it.pkg.lowercase() }; presetCacheMs = now
            }
            return presetMap[pkg.lowercase()]
        }
        fun applyPresetNow(ctx: Context, p: AppPreset) { CoroutineScope(Dispatchers.IO).launch { applyEnter(ctx, AppConfig(ctx), p, p.pkg) } }
        private suspend fun onAppSwitch(ctx: Context, cfg: AppConfig, prevPkg: String, prevPre: AppPreset?, newPkg: String, newPre: AppPreset?) {
            if (System.currentTimeMillis() < vpnBusyUntil) return
            val shOk = cfg.useShizuku && ShizukuBridge.hasPermission()
            if (prevPre != null) {
                savedVol?.let { v -> (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).setStreamVolume(AudioManager.STREAM_MUSIC, v, 0); savedVol = null }
                savedBri?.let { b -> val m = savedBriMode ?: 1; savedBri = null; savedBriMode = null
                    if (shOk) ShizukuBridge.runShell(ctx, "settings put system screen_brightness_mode $m && settings put system screen_brightness $b")
                    else if (Settings.System.canWrite(ctx)) { Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, m); Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, b) }
                }
                if (changedDnd) { changedDnd = false; if (shOk) ShizukuBridge.setDnd(ctx, false) }
                if (startedOverlay) { startedOverlay = false; ctx.stopService(Intent(ctx, FloatingOverlayService::class.java)) }
                if (prevPre.forceStopOnExit && shOk && prevPkg.isNotBlank() && prevPkg != ctx.packageName) { delay(350); ShizukuBridge.forceStop(ctx, prevPkg) }
                if (prevPre.customExitCmd.isNotBlank() && shOk) ShizukuBridge.runShell(ctx, prevPre.customExitCmd)
            }
            if (newPre != null) applyEnter(ctx, cfg, newPre, newPkg)
        }
        private suspend fun applyEnter(ctx: Context, cfg: AppConfig, p: AppPreset, returnPkg: String) {
            val shOk = cfg.useShizuku && ShizukuBridge.hasPermission()
            if (p.volumePct in 0..100) {
                val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (savedVol == null) savedVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                val mx = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, ((p.volumePct / 100f) * mx).toInt().coerceIn(0, mx), 0)
            }
            if (p.brightnessPct in 1..100) {
                if (savedBri == null) { savedBri = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128); savedBriMode = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 1) }
                if (shOk) ShizukuBridge.setBrightness(ctx, p.brightnessPct)
                else if (Settings.System.canWrite(ctx)) { Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0); Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, ((p.brightnessPct / 100f) * 255).toInt().coerceIn(1, 255)) }
            }
            if (shOk) {
                if (p.dndMode in 0..1) { ShizukuBridge.setDnd(ctx, p.dndMode == 1); if (p.dndMode == 1) changedDnd = true }
                if (p.darkMode in 0..1) ShizukuBridge.setDarkMode(ctx, p.darkMode == 1)
                if (p.autoRotate in 0..1) ShizukuBridge.setAutoRotate(ctx, p.autoRotate == 1)
                if (p.wifiState in 0..1) ShizukuBridge.setWifi(ctx, p.wifiState == 1)
                if (p.btState in 0..1) ShizukuBridge.setBluetooth(ctx, p.btState == 1)
                if (p.dataState in 0..1) ShizukuBridge.setData(ctx, p.dataState == 1)
                if (p.batterySaver in 0..1) ShizukuBridge.setBatterySaver(ctx, p.batterySaver == 1)
                if (p.timeoutSec >= 15) ShizukuBridge.setScreenTimeoutSec(ctx, p.timeoutSec)
                if (p.customEnterCmd.isNotBlank()) ShizukuBridge.runShell(ctx, p.customEnterCmd)
            }
            if (p.autoOverlay && Settings.canDrawOverlays(ctx) && !FloatingOverlayService.isRunning) {
                startedOverlay = true; ContextCompat.startForegroundService(ctx, Intent(ctx, FloatingOverlayService::class.java))
            }
            if (p.autoVpn) {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val on = cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                if (!on) {
                    val vpnPkg = listOf("ch.protonvpn.android", "com.cloudflare.onedotonedotonedotone", "com.windscribe.vpn").firstOrNull { ctx.packageManager.getLaunchIntentForPackage(it) != null }
                    if (vpnPkg != null) {
                        vpnBusyUntil = System.currentTimeMillis() + 9000L
                        ctx.packageManager.getLaunchIntentForPackage(vpnPkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { ctx.startActivity(it) }
                        instance?.waitAndTapAny(listOf("Quick Connect", "Connect", "Start"), 6000L)
                        if (returnPkg != vpnPkg) ctx.packageManager.getLaunchIntentForPackage(returnPkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { ctx.startActivity(it) }
                    }
                }
            }
        }
    }
}
