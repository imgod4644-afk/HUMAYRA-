package com.humayra.goddess.service

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.speech.*
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import com.humayra.goddess.*
import com.humayra.goddess.actions.*
import com.humayra.goddess.ai.HybridBrain
import com.humayra.goddess.storage.*
import com.humayra.goddess.ui.MainActivity
import kotlinx.coroutines.*
import java.util.Locale

class FloatingOverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var wm: WindowManager
    private var bubbleView: View? = null; private var cardView: View? = null
    private lateinit var bubbleLp: WindowManager.LayoutParams; private lateinit var cardLp: WindowManager.LayoutParams
    private var tts: android.speech.tts.TextToSpeech? = null; private var sr: SpeechRecognizer? = null
    private var replyTv: TextView? = null; private var inputEt: EditText? = null
    private var confirmRow: LinearLayout? = null; private var confirmTv: TextView? = null
    private var pendingConfirm: (() -> Unit)? = null
    override fun onBind(intent: Intent?): IBinder? = null
    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
    override fun onCreate() {
        super.onCreate(); isRunning = true
        val openPi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, HumayraApp.CH_CARE).setSmallIcon(R.drawable.ic_humayra_emblem)
            .setContentTitle("${AppConfig(this).companionName} Floating").setContentText("Tap to open app").setContentIntent(openPi).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) startForeground(403, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(403, n)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        tts = android.speech.tts.TextToSpeech(this) { if (it == android.speech.tts.TextToSpeech.SUCCESS) tts?.language = Locale("bn", "BD") }
        setupBubble(); setupCard()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupBubble() {
        val iv = ImageView(this).apply { setImageResource(R.drawable.ic_humayra_emblem); setPadding(dp(8), dp(8), dp(8), dp(8)); background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#E6131822")); setStroke(dp(2), Color.parseColor("#7C9CFF")) } }
        bubbleLp = WindowManager.LayoutParams(dp(54), dp(54), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 260 }
        var sx = 0f; var sy = 0f; var ix = 0; var iy = 0; var moved = false
        iv.setOnTouchListener { _, e -> when (e.action) {
            MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ix = bubbleLp.x; iy = bubbleLp.y; moved = false; true }
            MotionEvent.ACTION_MOVE -> { val dx = (e.rawX - sx).toInt(); val dy = (e.rawY - sy).toInt(); if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) { moved = true; bubbleLp.x = ix + dx; bubbleLp.y = iy + dy; wm.updateViewLayout(iv, bubbleLp) }; true }
            MotionEvent.ACTION_UP -> { if (!moved) toggleExpand(true); true }
            else -> false
        } }
        bubbleView = iv; wm.addView(iv, bubbleLp)
    }
    private fun setupCard() {
        val cfg = AppConfig(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)); background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.parseColor("#F0131822")); setStroke(dp(1), Color.parseColor("#7C9CFF")) } }
        val hdr = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply { text = "${cfg.companionName} ⚡"; setTextColor(Color.WHITE); textSize = 14f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val btnWatch = Button(this).apply { text = "👁 Watch"; textSize = 11f; setOnClickListener { watchNow() } }
        val btnMin = Button(this).apply { text = "—"; textSize = 12f; setOnClickListener { toggleExpand(false) } }
        val btnOff = Button(this).apply { text = "✕"; textSize = 12f; setOnClickListener { stopSelf() } }
        listOf(title, btnWatch, btnMin, btnOff).forEach { hdr.addView(it) }
        replyTv = TextView(this).apply { text = "বলো বস, স্ক্রিন দেখব নাকি কোনো কাজ করব?"; setTextColor(Color.parseColor("#E2E8F0")); textSize = 13f; maxLines = 6; setPadding(0, dp(6), 0, dp(6)) }
        confirmTv = TextView(this).apply { setTextColor(Color.parseColor("#F59E0B")); textSize = 12f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val btnYes = Button(this).apply { text = "✅"; setOnClickListener { pendingConfirm?.invoke(); pendingConfirm = null; confirmRow?.visibility = View.GONE } }
        val btnNo = Button(this).apply { text = "❌"; setOnClickListener { pendingConfirm = null; confirmRow?.visibility = View.GONE } }
        confirmRow = LinearLayout(this).apply { visibility = View.GONE; addView(confirmTv); addView(btnYes); addView(btnNo) }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        inputEt = EditText(this).apply { hint = "কমান্ড লিখুন..."; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); textSize = 13f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val btnMic = Button(this).apply { text = "🎤"; setOnClickListener { startMic() } }
        val btnSend = Button(this).apply { text = "➤"; setOnClickListener { val t = inputEt?.text?.toString()?.trim().orEmpty(); if (t.isNotEmpty()) { inputEt?.setText(""); askOverlay(t, null) } } }
        listOf(inputEt!!, btnMic, btnSend).forEach { row.addView(it) }
        listOf(hdr, replyTv!!, confirmRow!!, row).forEach { root.addView(it) }
        cardLp = WindowManager.LayoutParams(dp(310), -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 110 }
        cardView = root; root.visibility = View.GONE; wm.addView(root, cardLp)
    }
    private fun toggleExpand(show: Boolean) { bubbleView?.visibility = if (show) View.GONE else View.VISIBLE; cardView?.visibility = if (show) View.VISIBLE else View.GONE }

    private fun watchNow() {
        replyTv?.text = "স্ক্রিন দেখছি..."
        ScreenCaptureService.captureBestFrame(this, "Analyze screen") { bytes ->
            if (bytes == null && HumayraAccessibilityService.activeScreenText.isBlank()) replyTv?.text = "স্ক্রিন দেখা যায়নি—Accessibility বা ক্যাপচার পারমিশন দিন।"
            else askOverlay("এখন আমার স্ক্রিনে কী চলছে সংক্ষেপে মজার/কার্যকরীভাবে বলো।", bytes)
        }
    }

    private fun askOverlay(msg: String, img: ByteArray?) {
        replyTv?.text = "Thinking..."
        scope.launch {
            val (raw, _) = HybridBrain.ask(this@FloatingOverlayService, msg, img)
            val parsed = ActionParser.parse(raw); replyTv?.text = parsed.spokenText
            MemoryStore(this@FloatingOverlayService).apply { appendHistory("user", msg); appendHistory("assistant", parsed.spokenText) }
            speak(parsed.spokenText)
            if (parsed.actions.isNotEmpty()) ActionExecutor.executeAll(this@FloatingOverlayService, parsed.actions, object : ActionCallbacks {
                override fun onPose(pose: String) {}
                override fun onWatchScreen(prompt: String) { watchNow() }
                override fun onConfirmRequired(label: String, executeConfirmed: () -> Unit) {
                    confirmTv?.text = label; pendingConfirm = executeConfirmed; confirmRow?.visibility = View.VISIBLE
                }
                override fun onWhatsAppDraftUpdate(replyId: String, message: String) {}
                override fun onPreviewHtml(title: String, html: String) {}
                override fun onDownloadAsset(kind: String) {}
            })
        }
    }

    private fun speak(t: String) {
        val c = AppConfig(this); if (!c.readAloud) return
        val sp = com.humayra.goddess.ai.VoiceHelper.cleanForSpeech(t); if (sp.isBlank()) return; com.humayra.goddess.ai.VoiceHelper.applyFemaleVoice(tts, sp, c.ttsPitch, c.ttsSpeed, c.preferredVoiceName)
        tts?.speak(sp, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "ov")
    }

    private fun startMic() = try {
        sr?.destroy(); sr = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p0: Bundle?) { replyTv?.text = "শুনছি..." }
                override fun onResults(b: Bundle?) { b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { askOverlay(it, null) } }
                override fun onError(e: Int) { replyTv?.text = "Mic error $e" }
                override fun onBeginningOfSpeech() {} override fun onRmsChanged(v: Float) {} override fun onBufferReceived(p0: ByteArray?) {}
                override fun onEndOfSpeech() {} override fun onPartialResults(p0: Bundle?) {} override fun onEvent(p0: Int, p1: Bundle?) {}
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
        }
    } catch (e: Exception) { replyTv?.text = "Mic error: ${e.message}" }

    override fun onDestroy() {
        isRunning = false; scope.cancel(); sr?.destroy(); tts?.shutdown()
        bubbleView?.let { wm.removeView(it) }; cardView?.let { wm.removeView(it) }; super.onDestroy()
    }
    companion object { @Volatile var isRunning = false }
}
