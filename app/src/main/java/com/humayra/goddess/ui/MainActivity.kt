package com.humayra.goddess.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.speech.*
import android.speech.tts.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.humayra.goddess.R
import com.humayra.goddess.actions.*
import com.humayra.goddess.ai.HybridBrain
import com.humayra.goddess.service.*
import com.humayra.goddess.storage.*
import com.humayra.goddess.telemetry.PhoneDoctorTelemetry
import kotlinx.coroutines.*
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var cfg: AppConfig; private lateinit var mem: MemoryStore; private lateinit var stage: StageController
    private lateinit var tvName: TextView; private lateinit var tvMode: TextView; private lateinit var tvHealth: TextView
    private lateinit var tvStatus: TextView; private lateinit var tvConvo: TextView; private lateinit var scrollConvo: ScrollView
    private lateinit var etInput: EditText; private lateinit var swRead: SwitchCompat
    private var tts: TextToSpeech? = null; private var sr: SpeechRecognizer? = null; private var activeJob: Job? = null
    private val pickTextGguf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u -> importFile(u, "models") { cfg.localTextModelPath = it } }
    private val pickVisionGguf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u -> importFile(u, "models") { cfg.localVisionModelPath = it } }
    private val pickMmproj = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u -> importFile(u, "models") { cfg.localVisionMmprojPath = it } }
    private val pickAvatar = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        importFile(u, "avatars") { p ->
            val n = java.io.File(p).nameWithoutExtension
            cfg.addAvatar(AvatarProfile(n, "Custom", p)); cfg.activeAvatarPath = p; stage.loadAvatar(p)
        }
    }
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    private fun importFile(uri: Uri?, dir: String, onReady: (String) -> Unit) {
        if (uri == null) return
        tvStatus.text = "Importing file..."
        lifecycleScope.launch {
            val f = FileImportUtils.copyToInternal(this@MainActivity, uri, dir)
            onReady(f.absolutePath); tvStatus.text = "Imported: ${f.name}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_main); val rv = findViewById<android.view.View>(R.id.rootLayout); androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rv) { v, i -> val b = i.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.ime()); val p = (8 * resources.displayMetrics.density).toInt(); v.setPadding(b.left + p, b.top + p / 2, b.right + p, b.bottom + p / 2); i }
        cfg = AppConfig(this); mem = MemoryStore(this); stage = StageController(this, findViewById(R.id.webStage)).apply { init() }
        tvName = findViewById(R.id.tvName); tvMode = findViewById(R.id.tvModeBadge); tvHealth = findViewById(R.id.tvHealthBadge)
        tvStatus = findViewById(R.id.tvStatus); tvConvo = findViewById(R.id.tvConversation); scrollConvo = findViewById(R.id.scrollConvo)
        etInput = findViewById(R.id.etInput); swRead = findViewById(R.id.switchReadAloud)
        initTts(); bindUi(); ensureCareRunning(); handleIntent(intent)
    }
    private fun bindUi() {
        refreshHeader(); swRead.isChecked = cfg.readAloud
        swRead.setOnCheckedChangeListener { _, on -> cfg.readAloud = on; if (!on) tts?.stop() }
        tvName.setOnClickListener {
            val et = EditText(this).apply { setText(cfg.companionName) }
            AlertDialog.Builder(this).setTitle("Rename Companion").setView(et).setPositiveButton("Save") { _, _ -> cfg.companionName = et.text.toString(); refreshHeader() }.show()
        }
        tvMode.setOnClickListener {
            val order = listOf("HYBRID", "ONLINE", "OFFLINE"); cfg.brainMode = order[(order.indexOf(cfg.brainMode) + 1) % 3]; refreshHeader()
        }
        tvHealth.setOnClickListener { PhoneDoctorSheet.showDoctor(this) { sendMsg("আমার ফোনের ব্যাটারি, তাপমাত্রা, RAM আর স্ক্রিন টাইম দেখে পরামর্শ দাও।") } }
        findViewById<TextView>(R.id.btnOverlay).setOnClickListener { toggleOverlay() }
        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            SettingsSheet.show(this, SettingsCallbacks(
                { pickTextGguf.launch(arrayOf("*/*")) }, { pickVisionGguf.launch(arrayOf("*/*")) },
                { pickMmproj.launch(arrayOf("*/*")) }, { pickAvatar.launch(arrayOf("*/*")) },
                { refreshHeader(); stage.setTheme(cfg.stageBgColor, cfg.stageAccentColor); stage.loadAvatar(cfg.activeAvatarPath) },
                { requestAllPerms() }
            ))
        }
        findViewById<TextView>(R.id.btnHistory).setOnClickListener { PhoneDoctorSheet.showHistoryAndMemory(this) }
        mapOf(R.id.btnPoseWave to "wave", R.id.btnPoseSit to "sit", R.id.btnPoseStand to "stand", R.id.btnPoseSmile to "smile", R.id.btnPosePortrait to "portrait")
            .forEach { (id, p) -> findViewById<TextView>(id).setOnClickListener { stage.setPose(p) } }
        findViewById<TextView>(R.id.btnTalk).setOnClickListener { startMic() }
        findViewById<TextView>(R.id.btnSend).setOnClickListener { val t = etInput.text.toString().trim(); if (t.isNotEmpty()) { etInput.setText(""); sendMsg(t) } }
        findViewById<TextView>(R.id.btnStop).setOnClickListener { activeJob?.cancel(); HybridBrain.stopAll(); tts?.stop(); stage.setSpeaking(false); tvStatus.text = "Stopped" }
    }
    private fun sendMsg(text: String, img: ByteArray? = null) {
        tvStatus.text = "Thinking with you..."; tvConvo.text = "You: $text\n\n${cfg.companionName}: ..."
        mem.appendHistory("user", text)
        activeJob?.cancel()
        activeJob = lifecycleScope.launch {
            val sb = StringBuilder()
            val (raw, engine) = HybridBrain.ask(this@MainActivity, text, img) { d ->
                sb.append(d); runOnUiThread { tvConvo.text = "You: $text\n\n${cfg.companionName}: $sb"; scrollConvo.fullScroll(ScrollView.FOCUS_DOWN) }
            }
            val parsed = ActionParser.parse(raw); mem.appendHistory("assistant", parsed.spokenText)
            tvConvo.text = "You: $text\n\n${cfg.companionName}: ${parsed.spokenText}"
            scrollConvo.fullScroll(ScrollView.FOCUS_DOWN); tvStatus.text = "Here with you • $engine"
            speak(parsed.spokenText)
            if (parsed.actions.isNotEmpty()) ActionExecutor.executeAll(this@MainActivity, parsed.actions, object : ActionCallbacks {
                override fun onPose(pose: String) { stage.setPose(pose) }
                override fun onWatchScreen(prompt: String) {
                    ScreenCaptureService.captureBestFrame(this@MainActivity, prompt) { b -> sendMsg(prompt, b) }
                }
                override fun onConfirmRequired(label: String, executeConfirmed: () -> Unit) {
                    AlertDialog.Builder(this@MainActivity).setTitle("Confirm Action").setMessage(label)
                        .setPositiveButton("Confirm") { _, _ -> executeConfirmed() }.setNegativeButton("Cancel", null).show()
                }
                override fun onWhatsAppDraftUpdate(replyId: String, message: String) {
                    HumayraNotificationService.sendReplyNow(this@MainActivity, replyId, message)
                }
            })
        }
    }
    private fun initTts() {
        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale("bn", "BD") }.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { runOnUiThread { stage.setSpeaking(true) } }
                override fun onDone(id: String?) { runOnUiThread { stage.setSpeaking(false) } }
                @Deprecated("Deprecated") override fun onError(id: String?) { runOnUiThread { stage.setSpeaking(false) } }
            })
        }
    }

    private fun speak(t: String) {
        if (!cfg.readAloud) return; tts?.setPitch(cfg.ttsPitch); tts?.setSpeechRate(cfg.ttsSpeed)
        tts?.speak(t, TextToSpeech.QUEUE_FLUSH, Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "u") }, "u")
    }

    private fun startMic() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestAllPerms(); return }
        sr?.destroy(); sr = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p0: Bundle?) { tvStatus.text = "Listening..." }
                override fun onResults(b: Bundle?) { b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { sendMsg(it) } }
                override fun onError(e: Int) { tvStatus.text = "Mic ready ($e)" }
                override fun onBeginningOfSpeech() {} override fun onRmsChanged(v: Float) {} override fun onBufferReceived(p0: ByteArray?) {}
                override fun onEndOfSpeech() {} override fun onPartialResults(p0: Bundle?) {} override fun onEvent(p0: Int, p1: Bundle?) {}
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
        }
    }
    private fun toggleOverlay() {
        if (!Settings.canDrawOverlays(this)) { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); return }
        val i = Intent(this, FloatingOverlayService::class.java)
        if (FloatingOverlayService.isRunning) stopService(i) else ContextCompat.startForegroundService(this, i)
    }

    private fun refreshHeader() {
        tvName.text = cfg.companionName
        val on = HybridBrain.isOnline(this)
        tvMode.text = "${cfg.brainMode} • ${if (on) "ON" else "OFF"}"
        val t = PhoneDoctorTelemetry.collect(this)
        tvHealth.text = "🔋 ${t.batteryPct}% • ${String.format(Locale.US, "%.1f", t.tempC)}°C"
    }

    private fun ensureCareRunning() {
        requestAllPerms()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("humayra_care_periodic", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<CareCheckWorker>(15, TimeUnit.MINUTES).build())
        try { ContextCompat.startForegroundService(this, Intent(this, CompanionCareService::class.java)) } catch (_: Throwable) {}
        lifecycleScope.launch { while (isActive) { refreshHeader(); delay(8000) } }
    }

    private fun requestAllPerms() {
        val list = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) list.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) list.add(Manifest.permission.BLUETOOTH_CONNECT)
        permLauncher.launch(list.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }.toTypedArray())
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }

    private fun handleIntent(i: Intent?) {
        val id = i?.getStringExtra("edit_id") ?: return
        val et = EditText(this).apply { setText(i.getStringExtra("edit_draft").orEmpty()) }
        AlertDialog.Builder(this).setTitle("✏️ Reply to ${i.getStringExtra("edit_sender")}").setView(et)
            .setPositiveButton("Send") { _, _ -> HumayraNotificationService.sendReplyNow(this, id, et.text.toString()) }
            .setNegativeButton("Cancel", null).show()
    }

    override fun onDestroy() { sr?.destroy(); tts?.shutdown(); super.onDestroy() }
}
