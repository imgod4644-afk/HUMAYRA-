package com.humayra.goddess.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.speech.*
import android.speech.tts.*
import android.view.*
import android.webkit.WebView
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.*
import androidx.appcompat.widget.SwitchCompat
import androidx.biometric.*
import androidx.core.content.ContextCompat
import androidx.core.view.*
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.humayra.goddess.R
import com.humayra.goddess.actions.*
import com.humayra.goddess.ai.*
import com.humayra.goddess.security.*
import com.humayra.goddess.service.*
import com.humayra.goddess.storage.*
import com.humayra.goddess.telemetry.PhoneDoctorTelemetry
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var cfg: AppConfig; private lateinit var mem: MemoryStore; private lateinit var stage: StageController
    private lateinit var tvName: TextView; private lateinit var tvHealth: TextView; private lateinit var tvStatus: TextView
    private lateinit var tvConvo: TextView; private lateinit var tvActionLog: TextView; private lateinit var btnMaster: TextView
    private lateinit var tab3d: TextView; private lateinit var tabChat: TextView; private lateinit var btnActionOrb: TextView
    private lateinit var page3d: View; private lateinit var pageChat: View; private lateinit var pageVoiceOrb: View
    private lateinit var viewOrb: View; private lateinit var tvOrbState: TextView; private lateinit var swHands: SwitchCompat
    private lateinit var imgWall: ImageView; private lateinit var scrollConvo: ScrollView; private lateinit var etInput: EditText; private lateinit var card3dReply: View; private lateinit var tv3dReply: TextView; private lateinit var tvOrbReply: TextView
    private var tts: TextToSpeech? = null; private var ttsReady = false; private var sr: SpeechRecognizer? = null; private var activeJob: Job? = null
    private var unlocked = false; private var wasBg = false; private var pendingPass = ""
    private val pickTextGguf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u -> importFile(u, "models") { cfg.localTextModelPath = it } }
    private val pickVisionGguf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u -> importFile(u, "models") { cfg.localVisionModelPath = it } }
    private val pickMmproj = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u -> importFile(u, "models") { cfg.localVisionMmprojPath = it } }
    private val pickAvatar = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u -> importFile(u, "avatars") { p -> cfg.addAvatar(AvatarProfile(File(p).nameWithoutExtension, "Custom", p)); cfg.activeAvatarPath = p; stage.loadAvatar(p) } }
    private val pickWall = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u -> importFile(u, "walls") { p -> cfg.stageWallpaperPath = p; applyWallpaper() } }
    private val pickApk = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) lifecycleScope.launch { val f = FileImportUtils.copyToInternal(this@MainActivity, u, "tmp"); AlertDialog.Builder(this@MainActivity).setTitle("📦 APK Inspector").setMessage(CyberShield.inspectApk(this@MainActivity, f)).setPositiveButton("OK") { _, _ -> f.delete() }.show() } }
    private val exportVault = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { u ->
        if (u != null) { contentResolver.openOutputStream(u)?.use { it.write(CryptoVault.encryptWithPassphrase(mem.exportBundle(), pendingPass).toByteArray()) }; Toast.makeText(this, "✅ .hvault Saved", Toast.LENGTH_SHORT).show() }
    }
    private val importVault = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { val blob = contentResolver.openInputStream(u)?.bufferedReader()?.readText().orEmpty(); CryptoVault.decryptWithPassphrase(blob, pendingPass).onSuccess { mem.importBundle(it); Toast.makeText(this, "✅ Restored!", Toast.LENGTH_SHORT).show() }.onFailure { Toast.makeText(this, "❌ ভুল পাসওয়ার্ড", Toast.LENGTH_SHORT).show() } }
    }
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_main)
        cfg = AppConfig(this); mem = MemoryStore(this); applyFlags()
        val rv = findViewById<View>(R.id.rootLayout)
        ViewCompat.setOnApplyWindowInsetsListener(rv) { v, i ->
            val b = i.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            val p = (8 * resources.displayMetrics.density).toInt(); v.setPadding(b.left + p, b.top + p / 2, b.right + p, b.bottom + p / 2); i
        }
        stage = StageController(this, findViewById(R.id.webStage)).apply { init() }
        tvName = findViewById(R.id.tvName); tvHealth = findViewById(R.id.tvHealthBadge); tvStatus = findViewById(R.id.tvStatus)
        tvConvo = findViewById(R.id.tvConversation); tvActionLog = findViewById(R.id.tvActionLog); btnMaster = findViewById(R.id.btnMaster)
        tab3d = findViewById(R.id.tab3d); tabChat = findViewById(R.id.tabChat); btnActionOrb = findViewById(R.id.btnActionOrb)
        page3d = findViewById(R.id.page3d); pageChat = findViewById(R.id.pageChat); pageVoiceOrb = findViewById(R.id.pageVoiceOrb)
        viewOrb = findViewById(R.id.viewOrb); tvOrbState = findViewById(R.id.tvOrbState); swHands = findViewById(R.id.swHandsFree)
        imgWall = findViewById(R.id.imgStageWall); scrollConvo = findViewById(R.id.scrollConvo); etInput = findViewById(R.id.etInput); card3dReply = findViewById(R.id.card3dReply); tv3dReply = findViewById(R.id.tv3dReply); tvOrbReply = findViewById(R.id.tvOrbReply)
        initTts(); bindUi(); applyWallpaper(); ensureCareRunning(); handleIntent(intent); checkLockGate()
    }
    override fun onStop() { super.onStop(); wasBg = true; runCatching { findViewById<WebView>(R.id.webStage).onPause() } }
    override fun onStart() { super.onStart(); runCatching { findViewById<WebView>(R.id.webStage).onResume() }; if (wasBg && cfg.appLockEnabled && cfg.autoLockOnLeave) { wasBg = false; unlocked = false; checkLockGate() } }
    private fun applyFlags() { if (cfg.flagSecure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    private fun applyWallpaper() { val f = File(cfg.stageWallpaperPath); if (f.exists()) { imgWall.setImageBitmap(BitmapFactory.decodeFile(f.absolutePath)); imgWall.visibility = View.VISIBLE; stage.setWallpaper(true) } else { imgWall.visibility = View.GONE; stage.setWallpaper(false) } }
    private fun checkLockGate() {
        if (!cfg.appLockEnabled || unlocked || cfg.realPinHash.isBlank()) { unlocked = true; return }
        if (cfg.biometricEnabled && BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS) {
            val bp = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) { unlocked = true; cfg.decoyActive = false; cfg.failedAttempts = 0; refreshHeader() }
                override fun onAuthenticationError(c: Int, s: CharSequence) { showPinGate() }
            })
            bp.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("🔐 Unlock HUMAYRA").setNegativeButtonText("Use PIN").build())
        } else showPinGate()
    }
    private fun showPinGate() {
        val et = EditText(this).apply { hint = "Enter PIN" }
        AlertDialog.Builder(this).setTitle("🔐 HUMAYRA Vault").setCancelable(false).setView(et).setPositiveButton("Unlock") { _, _ ->
            val h = CryptoVault.hashPin(et.text.toString().trim())
            when {
                h == cfg.realPinHash -> { unlocked = true; cfg.decoyActive = false; cfg.failedAttempts = 0; refreshHeader() }
                cfg.decoyPinHash.isNotBlank() && h == cfg.decoyPinHash -> { unlocked = true; cfg.decoyActive = true; tvConvo.text = "Welcome! Ask me anything."; refreshHeader() }
                else -> {
                    cfg.failedAttempts++; if (cfg.intruderSelfie) IntruderCamera.captureSilent(this)
                    if (cfg.autoWipeOn5Fails && cfg.failedAttempts >= 5) { mem.panicWipeAll(true); Toast.makeText(this, "🧹 Auto-Wiped!", Toast.LENGTH_LONG).show(); finish() }
                    else { Toast.makeText(this, "❌ Wrong PIN (${cfg.failedAttempts})", Toast.LENGTH_SHORT).show(); showPinGate() }
                }
            }
        }.show()
    }
    private fun bindUi() {
        refreshHeader(); switchTab(cfg.uiTab); swHands.isChecked = cfg.handsFreeLoop
        swHands.setOnCheckedChangeListener { _, on -> cfg.handsFreeLoop = on }
        tab3d.setOnClickListener { switchTab(0) }; tabChat.setOnClickListener { switchTab(1) }
        btnMaster.setOnClickListener {
            cfg.masterEnabled = !cfg.masterEnabled; refreshHeader()
            if (cfg.masterEnabled) ensureCareRunning() else {
                activeJob?.cancel(); HybridBrain.stopAll(); tts?.stop(); sr?.destroy()
                stopService(Intent(this, CompanionCareService::class.java)); stopService(Intent(this, FloatingOverlayService::class.java))
                tvStatus.text = "Standby (Master OFF)"
            }
        }
        tvHealth.setOnClickListener { PhoneDoctorSheet.showDoctor(this) { switchTab(1); sendMsg("আমার ফোনের ব্যাটারি, তাপমাত্রা, RAM ও স্ক্রিন টাইম দেখে পরামর্শ দাও।") } }
        findViewById<TextView>(R.id.btnSettings).setOnClickListener { openSettings() }
        findViewById<TextView>(R.id.btnHistory).setOnClickListener { PhoneDoctorSheet.showHistoryAndMemory(this) }
        mapOf(R.id.btnPoseWave to "wave", R.id.btnPoseSit to "sit", R.id.btnPoseStand to "stand", R.id.btnPoseSmile to "smile", R.id.btnPosePortrait to "portrait")
            .forEach { (id, p) -> findViewById<TextView>(id).setOnClickListener { stage.setPose(p) } }
        etInput.addTextChangedListener { s -> btnActionOrb.text = if (s.isNullOrBlank()) "✦" else "➤" }
        findViewById<TextView>(R.id.btnMic).setOnClickListener { startMic(false) }
        findViewById<TextView>(R.id.btnOrbClose).setOnClickListener { pageVoiceOrb.visibility = View.GONE; sr?.cancel(); tts?.stop(); stage.setSpeaking(false) }; viewOrb.setOnClickListener { tts?.stop(); stage.setSpeaking(false); startMic(true) }; findViewById<TextView>(R.id.btn3dReplyClose).setOnClickListener { card3dReply.visibility = View.GONE }
        btnActionOrb.setOnClickListener { val t = etInput.text.toString().trim(); if (t.isNotEmpty()) { etInput.setText(""); sendMsg(t) } else { pageVoiceOrb.visibility = View.VISIBLE; swHands.isChecked = true; cfg.handsFreeLoop = true; tvOrbReply.text = ""; startMic(true) } }
        findViewById<TextView>(R.id.btnPlus).setOnClickListener { showPlusMenu() }
    }
    private fun showPlusMenu() {
        val items = arrayOf("👁 Watch Screen", "🔔 Notification Vault", "🛡️ CyberShield Security", "📱 App Presets & Wi-Fi MAC", "🩺 Phone Doctor", "🫧 Floating Game Bubble", "📖 Tutorial & Guide", "⏹ Stop AI / Voice")
        AlertDialog.Builder(this).setTitle("⚡ JARVIS Tools").setItems(items) { _, i ->
            when (i) {
                0 -> ScreenCaptureService.captureBestFrame(this, "Analyze screen") { b -> switchTab(1); sendMsg("স্ক্রিনে কী চলছে বলো", b) }
                1 -> AlertDialog.Builder(this).setTitle("🔔 Notification Vault").setMessage(NotificationVault(this).formattedDigest()).setNeutralButton("Clear") { _, _ -> NotificationVault(this).clear() }.setPositiveButton("OK", null).show()
                2 -> CyberShieldSheet.show(this, CyberShieldCallbacks({ pendingPass = it; exportVault.launch("humayra_backup.hvault") }, { pendingPass = it; importVault.launch(arrayOf("*/*")) }, { pickApk.launch(arrayOf("*/*")) }, { applyFlags() }, { requestAllPerms() }))
                3 -> AppPresetsSheet.show(this)
                4 -> PhoneDoctorSheet.showDoctor(this) { switchTab(1); sendMsg("আমার ফোনের অবস্থা চেক করে পরামর্শ দাও।") }
                5 -> toggleOverlay()
                6 -> GuideTutorialSheet.show(this)
                7 -> { activeJob?.cancel(); HybridBrain.stopAll(); tts?.stop(); stage.setSpeaking(false); tvStatus.text = "Stopped" }
            }
        }.show()
    }
    private fun switchTab(idx: Int) {
        cfg.uiTab = idx; page3d.visibility = if (idx == 0) View.VISIBLE else View.GONE; pageChat.visibility = if (idx == 1) View.VISIBLE else View.GONE
        tab3d.setBackgroundResource(if (idx == 0) R.drawable.bg_glow_button else 0); tabChat.setBackgroundResource(if (idx == 1) R.drawable.bg_glow_button else 0)
        tab3d.setTextColor(ContextCompat.getColor(this, if (idx == 0) R.color.bg_main else R.color.text_secondary))
        tabChat.setTextColor(ContextCompat.getColor(this, if (idx == 1) R.color.bg_main else R.color.text_secondary))
    }
    private fun sendMsg(text: String, img: ByteArray? = null) {
        if (!cfg.masterEnabled) { tvConvo.text = "Master Switch OFF আছে — ওপরে ON চাপুন।"; return }
        tvStatus.text = "Thinking..."; if (pageVoiceOrb.visibility == View.VISIBLE) tvOrbState.text = "Thinking..."; tvActionLog.visibility = View.GONE; tvConvo.text = "You: $text\n\n${cfg.companionName}: "
        if (cfg.uiTab == 0) { card3dReply.visibility = View.VISIBLE; tv3dReply.text = "..." }
        mem.appendHistory("user", text); activeJob?.cancel()
        activeJob = lifecycleScope.launch {
            val quick = if (img == null) DirectCommandRouter.matchQuick(this@MainActivity, text) else emptyList()
            if (quick.isNotEmpty()) {
                val logs = ActionExecutor.executeAll(this@MainActivity, quick, actionCbs())
                val sp = if (Regex("[\\u0980-\\u09FF]").containsMatchIn(text)) "একদম ঠিক আছে, করে দিচ্ছি!" else "Done! Switched it for you."
                mem.appendHistory("assistant", sp); tvConvo.text = "You: $text\n\n${cfg.companionName}: $sp"
                if (cfg.uiTab == 0) tv3dReply.text = "$sp\n${logs.joinToString(" | ")}"
                tvActionLog.text = logs.joinToString(" | "); tvActionLog.visibility = View.VISIBLE
                tvStatus.text = "Instant • Shizuku"; speak(sp); return@launch
            }
            var curPrompt = text
            for (step in 0..2) {
                var streamBuf = ""; if (step > 0) tvConvo.append("\n\n")
                val (raw, eng) = HybridBrain.ask(this@MainActivity, curPrompt, if (step == 0) img else null) { d ->
                    runOnUiThread { streamBuf += d; tvConvo.append(d); scrollConvo.fullScroll(ScrollView.FOCUS_DOWN); if (cfg.uiTab == 0) tv3dReply.text = streamBuf }
                }
                val parsed = ActionParser.parse(raw)
                val spoken = parsed.spokenText.ifBlank { if (parsed.actions.isNotEmpty()) "হয়ে গেছে!" else "আবার বলো?" }
                mem.appendHistory("assistant", spoken); tvConvo.text = "You: $text\n\n${cfg.companionName}: $spoken"
                if (cfg.uiTab == 0) tv3dReply.text = spoken
                scrollConvo.fullScroll(ScrollView.FOCUS_DOWN); tvStatus.text = "Here with you • $eng"; speak(spoken)
                val logs = if (parsed.actions.isNotEmpty()) ActionExecutor.executeAll(this@MainActivity, parsed.actions, actionCbs()) else emptyList()
                if (logs.isNotEmpty()) { tvActionLog.text = logs.joinToString(" | "); tvActionLog.visibility = View.VISIBLE; if (cfg.uiTab == 0) tv3dReply.append("\n${logs.joinToString(" | ")}") }
                if (!parsed.needsContinue || logs.isEmpty() || step == 2) break
                delay(750); curPrompt = "Step done (${logs.joinToString("; ")}). Observe screen and continue next step."
            }
        }
    }
    private fun actionCbs() = object : ActionCallbacks {
        override fun onPose(pose: String) = stage.setPose(pose)
        override fun onWatchScreen(prompt: String) = ScreenCaptureService.captureBestFrame(this@MainActivity, prompt) { b -> sendMsg(prompt, b) }
        override fun onConfirmRequired(label: String, executeConfirmed: () -> Unit) { AlertDialog.Builder(this@MainActivity).setTitle("⚠️ Confirm").setMessage(label).setPositiveButton("Confirm") { _, _ -> executeConfirmed() }.setNegativeButton("Cancel", null).show() }
        override fun onWhatsAppDraftUpdate(replyId: String, message: String) { HumayraNotificationService.sendReplyNow(this@MainActivity, replyId, message) }
        override fun onPreviewHtml(title: String, html: String) { val wv = WebView(this@MainActivity).apply { minimumHeight = (380 * resources.displayMetrics.density).toInt(); settings.javaScriptEnabled = true; loadDataWithBaseURL(null, html, "text/html", "utf-8", null) }; AlertDialog.Builder(this@MainActivity).setTitle("🌐 $title").setView(wv).setPositiveButton("Close", null).show() }
        override fun onDownloadAsset(kind: String) = runDownloader(kind)
    }
    private fun runDownloader(kind: String) {
        val tv = TextView(this).apply { text = "Starting download..."; textSize = 14f; setPadding(48, 36, 48, 24) }
        var dlJob: Job? = null
        val dlg = AlertDialog.Builder(this).setTitle("⬇️ Downloading Asset").setView(tv)
            .setNegativeButton("Cancel") { _, _ -> dlJob?.cancel() }.setCancelable(false).show()
        fun prog(s: String) { runOnUiThread { tvStatus.text = s; tv.text = s } }
        dlJob = lifecycleScope.launch {
            val res: Result<String> = when (kind) {
                "avatar" -> ModelDownloader.download(this@MainActivity, ModelDownloader.VRM_SAMPLE, "avatars", "Humayra_Sample.vrm", ::prog)
                    .map { f -> cfg.addAvatar(AvatarProfile("Sample Goddess", "VRM", f.absolutePath)); cfg.activeAvatarPath = f.absolutePath; stage.loadAvatar(f.absolutePath); "✅ 3D VRM Ready!" }
                "text_model" -> ModelDownloader.download(this@MainActivity, ModelDownloader.LLAMA_1B, "models", "Llama-3.2-1B-Q4.gguf", ::prog)
                    .map { f -> cfg.localTextModelPath = f.absolutePath; "✅ Offline Llama-1B Ready!" }
                else -> {
                    val r1 = ModelDownloader.download(this@MainActivity, ModelDownloader.SMOL_VLM, "models", "SmolVLM-500M-Q8.gguf", ::prog)
                    if (r1.isFailure) Result.failure(r1.exceptionOrNull()!!) else {
                        cfg.localVisionModelPath = r1.getOrThrow().absolutePath
                        ModelDownloader.download(this@MainActivity, ModelDownloader.SMOL_MM, "models", "mmproj-SmolVLM-Q8.gguf", ::prog)
                            .map { f -> cfg.localVisionMmprojPath = f.absolutePath; "✅ SmolVLM Vision Ready!" }
                    }
                }
            }
            val msg = res.getOrElse { "❌ Error: ${it.message}" }
            tvStatus.text = msg; tv.text = msg; dlg.setCancelable(true); dlg.getButton(AlertDialog.BUTTON_NEGATIVE)?.text = "Close"
        }
    }
    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true; tts?.language = Locale("bn", "IN")
                tts?.setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) { runOnUiThread { stage.setSpeaking(true); tvOrbState.text = "Speaking..." } }
                    override fun onDone(id: String?) { runOnUiThread {
                        stage.setSpeaking(false)
                        if (pageVoiceOrb.visibility == View.VISIBLE && cfg.handsFreeLoop && cfg.masterEnabled) {
                            tvOrbState.text = "Listening..."; lifecycleScope.launch { delay(380); if (pageVoiceOrb.visibility == View.VISIBLE) startMic(true) }
                        }
                    } }
                    @Deprecated("Deprecated") override fun onError(id: String?) { runOnUiThread {
                        stage.setSpeaking(false)
                        if (pageVoiceOrb.visibility == View.VISIBLE && cfg.handsFreeLoop && cfg.masterEnabled) {
                            lifecycleScope.launch { delay(450); if (pageVoiceOrb.visibility == View.VISIBLE) startMic(true) }
                        }
                    } }
                })
            }
        }
    }
    private fun speak(t: String) {
        val inOrb = pageVoiceOrb.visibility == View.VISIBLE
        if (!cfg.masterEnabled || (!cfg.readAloud && !inOrb)) return
        val sp = VoiceHelper.cleanForSpeech(t); if (sp.isBlank()) { if (inOrb && cfg.handsFreeLoop) startMic(true); return }
        runCatching { sr?.destroy(); sr = null }
        if (inOrb) { tvOrbReply.text = sp; tvOrbState.text = "Speaking..." }
        VoiceHelper.applyFemaleVoice(tts, sp, cfg.ttsPitch, cfg.ttsSpeed, cfg.preferredVoiceName)
        val rc = if (ttsReady) tts?.speak(sp, TextToSpeech.QUEUE_FLUSH, Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "u") }, "u") else TextToSpeech.ERROR
        if (rc != TextToSpeech.SUCCESS && inOrb && cfg.handsFreeLoop) lifecycleScope.launch { delay(900); if (pageVoiceOrb.visibility == View.VISIBLE) startMic(true) }
    }
    private fun startMic(orbMode: Boolean) {
        if (!cfg.masterEnabled) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestAllPerms(); return }
        runCatching { sr?.destroy(); sr = null }; var lastPart = ""
        sr = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p0: Bundle?) { tvStatus.text = "Listening..."; tvOrbState.text = "Listening..." }
                override fun onRmsChanged(v: Float) { if (orbMode) { val s = 1f + (v.coerceIn(0f, 10f) / 14f); viewOrb.scaleX = s; viewOrb.scaleY = s } }
                override fun onPartialResults(b: Bundle?) { val p = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull(); if (!p.isNullOrBlank()) { lastPart = p; tvOrbState.text = p } }
                override fun onResults(b: Bundle?) {
                    val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().ifBlank { lastPart }
                    lastPart = ""; runCatching { sr?.destroy(); sr = null }
                    if (t.isNotBlank()) { if (orbMode) tvOrbState.text = "Thinking..."; sendMsg(t) }
                    else if (orbMode && cfg.handsFreeLoop && pageVoiceOrb.visibility == View.VISIBLE) lifecycleScope.launch { delay(400); startMic(true) }
                }
                override fun onError(e: Int) {
                    val p = lastPart.trim(); lastPart = ""; runCatching { sr?.destroy(); sr = null }
                    if (p.isNotBlank() && (e == 6 || e == 7 || e == 8)) { if (orbMode) tvOrbState.text = "Thinking..."; sendMsg(p); return }
                    tvStatus.text = "Mic ready ($e)"
                    if (orbMode && cfg.handsFreeLoop && cfg.masterEnabled && pageVoiceOrb.visibility == View.VISIBLE && e != 9) {
                        tvOrbState.text = "Listening..."; lifecycleScope.launch { delay(600); if (pageVoiceOrb.visibility == View.VISIBLE) startMic(true) }
                    } else tvOrbState.text = "Tap Orb to speak"
                }
                override fun onBeginningOfSpeech() {} override fun onBufferReceived(p0: ByteArray?) {} override fun onEndOfSpeech() {} override fun onEvent(p0: Int, p1: Bundle?) {}
            })
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true).putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3).putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1400L)
            startListening(i)
        }
    }
    private fun openSettings() = SettingsSheet.show(this, SettingsCallbacks(
        { pickTextGguf.launch(arrayOf("*/*")) }, { pickVisionGguf.launch(arrayOf("*/*")) }, { pickMmproj.launch(arrayOf("*/*")) },
        { pickAvatar.launch(arrayOf("*/*")) }, { pickWall.launch(arrayOf("image/*")) }, { runDownloader(it) },
        { refreshHeader(); stage.setTheme(cfg.stageBgColor, cfg.stageAccentColor); stage.loadAvatar(cfg.activeAvatarPath); applyWallpaper() },
        { requestAllPerms() }, { checkGitHubUpdate() }, { GuideTutorialSheet.show(this) }, { applyFlags() }, { CyberShieldSheet.show(this, CyberShieldCallbacks({ pendingPass = it; exportVault.launch("humayra_backup.hvault") }, { pendingPass = it; importVault.launch(arrayOf("*/*")) }, { pickApk.launch(arrayOf("*/*")) }, { applyFlags() }, { requestAllPerms() })) }, { PhoneDoctorSheet.showDoctor(this) { switchTab(1); sendMsg("আমার ফোনের অবস্থা দেখে পরামর্শ দাও।") } }
    ))
    private fun importFile(uri: Uri?, dir: String, onReady: (String) -> Unit) {
        if (uri == null) return; tvStatus.text = "Importing..."
        lifecycleScope.launch { val f = FileImportUtils.copyToInternal(this@MainActivity, uri, dir); onReady(f.absolutePath); tvStatus.text = "Imported: ${f.name}" }
    }
    private fun toggleOverlay() {
        if (!Settings.canDrawOverlays(this)) { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); return }
        val i = Intent(this, FloatingOverlayService::class.java); if (FloatingOverlayService.isRunning) stopService(i) else ContextCompat.startForegroundService(this, i)
    }
    private fun refreshHeader() {
        tvName.text = cfg.companionName; btnMaster.text = if (cfg.masterEnabled) "ON" else "OFF"
        btnMaster.setTextColor(ContextCompat.getColor(this, if (cfg.masterEnabled) R.color.status_green else R.color.status_red))
        val t = PhoneDoctorTelemetry.collect(this, false); tvHealth.text = "${t.batteryPct}%•${t.tempC.toInt()}°C"
    }
    private fun ensureCareRunning() {
        requestAllPerms(); if (!cfg.masterEnabled) return
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("humayra_care_periodic", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<CareCheckWorker>(15, TimeUnit.MINUTES).build())
        runCatching { ContextCompat.startForegroundService(this, Intent(this, CompanionCareService::class.java)) }
        lifecycleScope.launch { while (isActive && cfg.masterEnabled) { if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) refreshHeader(); delay(15000) } }
    }
    private fun requestAllPerms() {
        val list = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) list.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) list.add(Manifest.permission.BLUETOOTH_CONNECT)
        permLauncher.launch(list.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }.toTypedArray())
    }
    private fun checkGitHubUpdate() = lifecycleScope.launch(Dispatchers.IO) {
        val msg = runCatching {
            val r = OkHttpClient().newCall(Request.Builder().url("https://api.github.com/repos/imgod4644-afk/HUMAYRA-/actions/runs?status=success&per_page=1").build()).execute().use { it.body?.string().orEmpty() }
            val run = JSONObject(r).getJSONArray("workflow_runs").getJSONObject(0)
            "Latest Build #${run.optInt("run_number")} (${run.optString("created_at").take(10)})\nCommit: ${run.optString("head_sha").take(7)}"
        }.getOrElse { "Check failed: ${it.message}" }
        withContext(Dispatchers.Main) { AlertDialog.Builder(this@MainActivity).setTitle("🔄 GitHub Build").setMessage(msg).setPositiveButton("Open GitHub") { _, _ -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/imgod4644-afk/HUMAYRA-/actions"))) }.setNegativeButton("OK", null).show() }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }
    private fun handleIntent(i: Intent?) {
        if (i?.getBooleanExtra("open_doctor", false) == true) { PhoneDoctorSheet.showDoctor(this) { switchTab(1); sendMsg("ফোনের অবস্থা দেখে পরামর্শ দাও।") }; return }
        i?.getStringExtra("preset_pkg")?.let { p -> AppPresetsSheet.showForApp(this, p, i.getStringExtra("preset_lbl") ?: p); return }
        if (i?.action == Intent.ACTION_ASSIST && cfg.masterEnabled) { pageVoiceOrb.visibility = View.VISIBLE; swHands.isChecked = true; cfg.handsFreeLoop = true; tvOrbReply.text = ""; startMic(true); return }
        val id = i?.getStringExtra("edit_id") ?: return; val et = EditText(this).apply { setText(i.getStringExtra("edit_draft").orEmpty()) }
        AlertDialog.Builder(this).setTitle("✏️ Reply to ${i.getStringExtra("edit_sender")}").setView(et).setPositiveButton("Send") { _, _ -> HumayraNotificationService.sendReplyNow(this, id, et.text.toString()) }.setNegativeButton("Cancel", null).show()
    }
    override fun onDestroy() { sr?.destroy(); tts?.shutdown(); super.onDestroy() }
}
