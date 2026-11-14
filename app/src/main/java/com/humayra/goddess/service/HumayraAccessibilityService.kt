package com.humayra.goddess.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream

class HumayraAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onDestroy() { instance = null; super.onDestroy() }
    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        activePackage = pkg
        val now = System.currentTimeMillis()
        if (now - lastReadMs < 350) return
        lastReadMs = now
        val sb = StringBuilder()
        collectText(rootInActiveWindow, sb, 0)
        if (sb.isNotEmpty()) activeScreenText = sb.toString().take(900)
    }

    private fun collectText(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null || depth > 14 || sb.length > 850) return
        val t = node.text?.toString()?.trim().orEmpty()
        val d = node.contentDescription?.toString()?.trim().orEmpty()
        if (t.isNotEmpty()) sb.append(t).append(" | ") else if (d.isNotEmpty()) sb.append(d).append(" | ")
        for (i in 0 until node.childCount) collectText(node.getChild(i), sb, depth + 1)
    }
    fun tapByText(target: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNode(root, target.trim(), false) ?: findNode(root, target.trim(), true) ?: return false
        var cur: AccessibilityNodeInfo? = node
        repeat(4) {
            if (cur?.isClickable == true && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            cur = cur?.parent
        }
        val r = Rect(); node.getBoundsInScreen(r)
        if (r.width() <= 0 || r.height() <= 0) return false
        val path = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 60)).build(), null, null)
    }

    private fun findNode(n: AccessibilityNodeInfo?, q: String, partial: Boolean): AccessibilityNodeInfo? {
        if (n == null || q.isEmpty()) return null
        val t = n.text?.toString().orEmpty(); val d = n.contentDescription?.toString().orEmpty()
        val match = if (partial) t.contains(q, true) || d.contains(q, true) else t.equals(q, true) || d.equals(q, true)
        if (match) return n
        for (i in 0 until n.childCount) findNode(n.getChild(i), q, partial)?.let { return it }
        return null
    }
    fun typeText(txt: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditable(root) ?: return false
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, txt) }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findEditable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null; if (n.isEditable) return n
        for (i in 0 until n.childCount) findEditable(n.getChild(i))?.let { return it }
        return null
    }

    fun scroll(dir: String): Boolean {
        val dm = resources.displayMetrics; val x = dm.widthPixels * 0.5f
        val down = dir.equals("down", true)
        val path = Path().apply { moveTo(x, dm.heightPixels * (if (down) 0.72f else 0.28f)); lineTo(x, dm.heightPixels * (if (down) 0.28f else 0.72f)) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 280)).build(), null, null)
    }

    fun globalAct(name: String): Boolean = performGlobalAction(when (name.lowercase()) {
        "home" -> GLOBAL_ACTION_HOME; "back" -> GLOBAL_ACTION_BACK; "recents" -> GLOBAL_ACTION_RECENTS
        "notifications" -> GLOBAL_ACTION_NOTIFICATIONS; else -> GLOBAL_ACTION_QUICK_SETTINGS
    })
    fun captureScreenJpeg(maxW: Int, cb: (ByteArray?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) { cb(null); return }
        takeScreenshot(Display.DEFAULT_DISPLAY, ContextCompat.getMainExecutor(this), object : TakeScreenshotCallback {
            override fun onSuccess(res: ScreenshotResult) {
                try {
                    val hw = Bitmap.wrapHardwareBuffer(res.hardwareBuffer, res.colorSpace)
                    // free after copy
                    val soft = hw?.copy(Bitmap.Config.ARGB_8888, false); hw?.recycle(); res.hardwareBuffer.close()
                    if (soft == null) { cb(null); return }
                    val ratio = if (soft.width > maxW) maxW.toFloat() / soft.width else 1f
                    val scaled = if (ratio < 1f) Bitmap.createScaledBitmap(soft, maxW, (soft.height * ratio).toInt(), true).also { soft.recycle() } else soft
                    val out = ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 72, it) }
                    scaled.recycle(); cb(out.toByteArray())
                } catch (_: Throwable) { cb(null) }
            }
            override fun onFailure(err: Int) = cb(null)
        })
    }

    companion object {
        @Volatile var instance: HumayraAccessibilityService? = null
        @Volatile var activePackage: String = ""
        @Volatile var activeScreenText: String = ""
        private var lastReadMs = 0L
        fun getActiveScreenSummary(): String =
            if (activePackage.isBlank()) "Accessibility off or idle"
            else "App: $activePackage | Screen: ${activeScreenText.take(400)}"
    }
}
