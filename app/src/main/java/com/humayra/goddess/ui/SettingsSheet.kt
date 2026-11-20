package com.humayra.goddess.ui

import android.app.AlertDialog
import android.content.*
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.*
import com.humayra.goddess.ai.*
import com.humayra.goddess.shizuku.ShizukuBridge
import com.humayra.goddess.storage.*
import kotlinx.coroutines.*
import java.io.File
import java.util.Locale

data class SettingsCallbacks(
    val onPickTextGguf: () -> Unit, val onPickVisionGguf: () -> Unit, val onPickMmproj: () -> Unit,
    val onPickAvatar: () -> Unit, val onPickWallpaper: () -> Unit, val onDownloadAsset: (String) -> Unit,
    val onSaved: () -> Unit, val onRequestPerms: () -> Unit, val onCheckUpdate: () -> Unit, val onTutorial: () -> Unit,
    val onApplyFlags: () -> Unit = {}, val onOpenShield: () -> Unit = {}, val onOpenDoctor: () -> Unit = {}
)
object SettingsSheet {
    fun show(ctx: Context, cb: SettingsCallbacks) {
        val cfg = AppConfig(ctx); lateinit var saveAllFn: () -> Unit
        FullPage.show(ctx, "⚙️ HUMAYRA System UI", "Save", { dlg -> saveAllFn(); dlg.dismiss() }) { root, dlg ->
            val c1 = UiKit.card(ctx, root, "⚡ QUICK CONTROLS & PRIVACY")
            val kCnt = CloudAiClient.availableTargets(cfg).size
            val shS = if (ShizukuBridge.hasPermission()) "Shizuku ON" else "Shizuku OFF"
            UiKit.lbl(ctx, c1, "Status: $shS  •  AI Targets: $kCnt  •  Voice: ${cfg.preferredVoiceName.ifBlank { "Auto Female" }}")
            val swShot = UiKit.sw(ctx, c1, "📸 Allow Screenshots & Recents", !cfg.flagSecure)
            swShot.setOnCheckedChangeListener { _, on -> cfg.flagSecure = !on; cb.onApplyFlags() }
            UiKit.btn(ctx, c1, "📱 App Presets & Shizuku Auto-Control", true) { AppPresetsSheet.show(ctx) }
            UiKit.btn(ctx, c1, "🩺 Live Phone Status (Phone Doctor)") { cb.onOpenDoctor() }
            UiKit.btn(ctx, c1, "🛡️ CyberShield Security & Vault") { cb.onOpenShield() }
            UiKit.btn(ctx, c1, "📖 Tutorial & Guide") { cb.onTutorial() }

            val c2 = UiKit.card(ctx, root, "🧠 AI ENGINE & 1-KEY AUTO CONNECT")
            val etUni = UiKit.secretEt(ctx, c2, "🔑 Paste ANY 1 Key (AIza / sk-or- / gsk_ / sk-)", "")
            val modes = listOf("HYBRID", "ONLINE", "OFFLINE")
            val spMode = Spinner(ctx).apply { adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, modes); setSelection(modes.indexOf(cfg.brainMode).coerceAtLeast(0)) }
            UiKit.lbl(ctx, c2, "AI Mode"); c2.addView(spMode)
            val provs = listOf("OPENROUTER", "GEMINI", "GROQ", "OPENAI")
            val spProv = Spinner(ctx).apply { adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, provs); setSelection(provs.indexOf(cfg.cloudProvider).coerceAtLeast(0)) }
            UiKit.lbl(ctx, c2, "Primary Provider"); c2.addView(spProv)
            val etGemKey = UiKit.secretEt(ctx, c2, "Gemini Key (AIza...)", cfg.geminiApiKey)
            val etGemMod = UiKit.et(ctx, c2, "Gemini Model", cfg.geminiModel)
            val etOrKey = UiKit.secretEt(ctx, c2, "OpenRouter Key (sk-or-...)", cfg.openrouterApiKey)
            val etOrMod = UiKit.et(ctx, c2, "OpenRouter Model", cfg.openrouterModel)
            val etGrqKey = UiKit.secretEt(ctx, c2, "Groq Key (gsk_...)", cfg.groqApiKey)
            val etOaiKey = UiKit.secretEt(ctx, c2, "OpenAI Key (sk-...)", cfg.openaiApiKey)
            val swCouncil = UiKit.sw(ctx, c2, "JARVIS Council (Multi-API)", cfg.councilMode)
            val swUnc = UiKit.sw(ctx, c2, "Private Uncensored Mode", cfg.uncensoredMode)
            val swEco = UiKit.sw(ctx, c2, "Gaming Eco Mode (6GB RAM)", cfg.ecoMode)

            val c3 = UiKit.card(ctx, root, "🎙️ FEMALE VOICE & TELEGRAM")
            UiKit.btn(ctx, c3, "🎙️ Select & Test Female Voice", true) { showVoicePicker(ctx, cfg) }
            val etPitch = UiKit.et(ctx, c3, "Voice Pitch", cfg.ttsPitch.toString())
            val etSpeed = UiKit.et(ctx, c3, "Voice Speed", cfg.ttsSpeed.toString())
            val swHands = UiKit.sw(ctx, c3, "Continuous Hands-Free Voice Call", cfg.handsFreeLoop)
            val swCare = UiKit.sw(ctx, c3, "24/7 Proactive Battery/Care Guard", cfg.proactiveCare)
            val swWa = UiKit.sw(ctx, c3, "WhatsApp & W4B Auto-Draft", cfg.whatsappAssistant)
            val swShiz = UiKit.sw(ctx, c3, "Use Shizuku Full Device Control", cfg.useShizuku)
            val swTg = UiKit.sw(ctx, c3, "Enable Telegram Companion Bridge", cfg.telegramHumanEnabled)
            val swTgVoice = UiKit.sw(ctx, c3, "🎤 Telegram Voice Note Replies", cfg.telegramVoiceReplies)
            val etTgTok = UiKit.secretEt(ctx, c3, "@BotFather Bot Token", cfg.telegramToken)
            UiKit.lbl(ctx, c3, "Locked Chat ID: ${cfg.telegramChatId.ifBlank { "বটে /start দিলে অটো লক হবে" }}")
            UiKit.btn(ctx, c3, "✉️ Test Telegram Message") {
                CoroutineScope(Dispatchers.IO).launch {
                    val ok = TelegramBridge.sendMessage(etTgTok.text.toString().trim(), cfg.telegramChatId, "হ্যালো! আমি ${cfg.companionName} 💜")
                    withContext(Dispatchers.Main) { Toast.makeText(ctx, if (ok) "✅ Sent!" else "❌ আগে বটে /start দিন", Toast.LENGTH_SHORT).show() }
                }
            }

            val c4 = UiKit.card(ctx, root, "👤 PERSONA, OFFLINE GGUF & 3D")
            val etName = UiKit.et(ctx, c4, "Companion Name", cfg.companionName)
            val etUser = UiKit.et(ctx, c4, "Your Name", cfg.userName)
            val etPrompt = UiKit.et(ctx, c4, "Custom Instructions", cfg.customPrompt)
            val vibes = listOf("HUMAN_COMPANION", "JARVIS_TACTICAL", "GAMING_SQUAD")
            val spVibe = Spinner(ctx).apply { adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, vibes); setSelection(vibes.indexOf(cfg.vibePreset).coerceAtLeast(0)) }
            UiKit.lbl(ctx, c4, "Vibe Preset"); c4.addView(spVibe)
            UiKit.lbl(ctx, c4, "Offline Model: ${File(cfg.localTextModelPath).name.ifBlank { "None" }}")
            UiKit.btn(ctx, c4, "⬇️ Auto-Download Llama-3.2-1B (.gguf)") { cb.onDownloadAsset("text_model") }
            UiKit.btn(ctx, c4, "⬇️ Auto-Download SmolVLM Vision") { cb.onDownloadAsset("vision_model") }
            UiKit.btn(ctx, c4, "📂 Pick Text .gguf") { cb.onPickTextGguf() }
            UiKit.btn(ctx, c4, "📂 Pick Vision .gguf") { cb.onPickVisionGguf() }
            UiKit.btn(ctx, c4, "📂 Pick .mmproj") { cb.onPickMmproj() }
            UiKit.btn(ctx, c4, "⬇️ Download Sample 3D .vrm") { cb.onDownloadAsset("avatar") }
            UiKit.btn(ctx, c4, "🧊 Import .vrm / .glb") { cb.onPickAvatar() }
            UiKit.btn(ctx, c4, "🎭 Switch Avatar") { showAvatarPicker(ctx, cfg, cb.onSaved) }
            UiKit.btn(ctx, c4, "🖼️ Pick 3D Wallpaper") { cb.onPickWallpaper() }
            UiKit.btn(ctx, c4, "Clear Wallpaper") { cfg.stageWallpaperPath = ""; cb.onSaved() }
            val etBg = UiKit.et(ctx, c4, "Bg Hex", cfg.stageBgColor)
            val etAcc = UiKit.et(ctx, c4, "Accent Hex", cfg.stageAccentColor)

            val c5 = UiKit.card(ctx, root, "🛡️ SHIZUKU & HYPEROS PERMISSIONS")
            UiKit.btn(ctx, c5, "⚡ Authorize Shizuku", true) { ShizukuBridge.requestPermission() }
            UiKit.btn(ctx, c5, "1. Grant Mic/Cam/Notif/Contacts/Call") { cb.onRequestPerms() }
            UiKit.btn(ctx, c5, "2. Default Assistant (ASSIST)") { openAct(ctx, Settings.ACTION_VOICE_INPUT_SETTINGS, false) }
            UiKit.btn(ctx, c5, "3. Floating Overlay") { openAct(ctx, Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true) }
            UiKit.btn(ctx, c5, "4. Accessibility (JARVIS Engine)") { openAct(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS, false) }
            UiKit.btn(ctx, c5, "5. Notification Access") { openAct(ctx, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, false) }
            UiKit.btn(ctx, c5, "6. Usage Access") { openAct(ctx, Settings.ACTION_USAGE_ACCESS_SETTINGS, false) }
            UiKit.btn(ctx, c5, "7. Battery: No Restrictions") { openAct(ctx, Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, true) }
            UiKit.btn(ctx, c5, "8. HyperOS Autostart") { openAutoStart(ctx) }
            UiKit.btn(ctx, c5, "🔄 Check GitHub APK Update") { cb.onCheckUpdate() }
            saveAllFn = {
                cfg.flagSecure = !swShot.isChecked; cb.onApplyFlags()
                cfg.companionName = etName.text.toString(); cfg.userName = etUser.text.toString(); cfg.customPrompt = etPrompt.text.toString()
                cfg.vibePreset = spVibe.selectedItem.toString(); cfg.brainMode = spMode.selectedItem.toString()
                cfg.councilMode = swCouncil.isChecked; cfg.uncensoredMode = swUnc.isChecked; cfg.ecoMode = swEco.isChecked
                cfg.handsFreeLoop = swHands.isChecked; cfg.proactiveCare = swCare.isChecked; cfg.whatsappAssistant = swWa.isChecked; cfg.useShizuku = swShiz.isChecked
                cfg.cloudProvider = spProv.selectedItem.toString(); cfg.geminiApiKey = etGemKey.text.toString().trim(); cfg.geminiModel = etGemMod.text.toString().trim()
                cfg.openrouterApiKey = etOrKey.text.toString().trim(); cfg.openrouterModel = etOrMod.text.toString().trim()
                cfg.groqApiKey = etGrqKey.text.toString().trim(); cfg.openaiApiKey = etOaiKey.text.toString().trim()
                val uk = etUni.text.toString().trim().removePrefix("Bearer").replace(Regex("\\s+"), "")
                if (uk.isNotEmpty()) when {
                    uk.startsWith("AIza") -> { cfg.geminiApiKey = uk; cfg.cloudProvider = "GEMINI" }
                    uk.startsWith("gsk_") -> { cfg.groqApiKey = uk; cfg.cloudProvider = "GROQ" }
                    uk.startsWith("sk-or-") -> { cfg.openrouterApiKey = uk; cfg.cloudProvider = "OPENROUTER" }
                    else -> { cfg.openaiApiKey = uk; cfg.cloudProvider = "OPENAI" }
                }
                cfg.telegramHumanEnabled = swTg.isChecked; cfg.telegramVoiceReplies = swTgVoice.isChecked; cfg.telegramToken = etTgTok.text.toString().trim()
                cfg.stageBgColor = etBg.text.toString().ifBlank { "#000000" }; cfg.stageAccentColor = etAcc.text.toString().ifBlank { "#60A5FA" }
                cfg.ttsPitch = etPitch.text.toString().toFloatOrNull()?.coerceIn(0.5f, 2f) ?: 1.05f; cfg.ttsSpeed = etSpeed.text.toString().toFloatOrNull()?.coerceIn(0.5f, 2f) ?: 1.02f
                Toast.makeText(ctx, "✅ Settings Saved", Toast.LENGTH_SHORT).show(); cb.onSaved()
            }
            UiKit.btn(ctx, root, "💾 Save All Settings", true) { saveAllFn(); dlg.dismiss() }
        }
    }
    private fun showVoicePicker(ctx: Context, cfg: AppConfig) {
        var t: TextToSpeech? = null
        FullPage.show(ctx, "🎙️ Select & Test Female Voice") { root, dlg ->
            val card = UiKit.card(ctx, root, "Tap any voice to hear & select")
            UiKit.btn(ctx, card, "✨ Auto Best Female Voice", cfg.preferredVoiceName.isBlank()) {
                cfg.preferredVoiceName = ""; Toast.makeText(ctx, "✅ Auto Voice Set", Toast.LENGTH_SHORT).show()
            }
            val listBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }; card.addView(listBox)
            t = TextToSpeech(ctx) { st ->
                if (st == TextToSpeech.SUCCESS) {
                    val vs = VoiceHelper.getVoicesForPicker(t)
                    root.post {
                        vs.take(18).forEach { v ->
                            val tag = if (v.name == cfg.preferredVoiceName) "✅ " else ""
                            UiKit.btn(ctx, listBox, "$tag${v.locale.displayLanguage} (${v.locale.country}) • ${v.name.takeLast(14)}") {
                                cfg.preferredVoiceName = v.name; t?.voice = v; t?.language = v.locale; t?.setPitch(1.10f)
                                val s = if (v.locale.language == "bn") "হ্যালো! আমি হুমায়রা, কেমন লাগছে?" else "Hello! I am Humayra."
                                t?.speak(s, TextToSpeech.QUEUE_FLUSH, null, "tst")
                            }
                        }
                    }
                }
            }
            dlg.setOnDismissListener { runCatching { t?.shutdown() } }
        }
    }
    private fun showAvatarPicker(ctx: Context, cfg: AppConfig, onDone: () -> Unit) { val l = cfg.getAvatars(); AlertDialog.Builder(ctx).setTitle("Select 3D Avatar").setItems((listOf("✨ Built-in") + l.map { it.name }).toTypedArray()) { _, i -> cfg.activeAvatarPath = if (i == 0) "" else l[i - 1].path; onDone() }.show() }
    private fun openAct(ctx: Context, act: String, pkg: Boolean) = runCatching { ctx.startActivity((if (pkg) Intent(act, Uri.parse("package:${ctx.packageName}")) else Intent(act)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }
    private fun openAutoStart(ctx: Context) { listOf(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")), Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))).forEach { runCatching { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } } }
}
