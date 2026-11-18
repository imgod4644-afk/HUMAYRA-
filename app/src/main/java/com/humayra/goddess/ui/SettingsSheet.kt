package com.humayra.goddess.ui

import android.app.AlertDialog
import android.content.*
import android.net.Uri
import android.provider.Settings
import android.widget.*
import com.humayra.goddess.ai.TelegramBridge
import com.humayra.goddess.shizuku.ShizukuBridge
import com.humayra.goddess.storage.*
import kotlinx.coroutines.*
import java.io.File

data class SettingsCallbacks(
    val onPickTextGguf: () -> Unit, val onPickVisionGguf: () -> Unit, val onPickMmproj: () -> Unit,
    val onPickAvatar: () -> Unit, val onPickWallpaper: () -> Unit, val onDownloadAsset: (String) -> Unit,
    val onSaved: () -> Unit, val onRequestPerms: () -> Unit, val onCheckUpdate: () -> Unit, val onTutorial: () -> Unit
)
object SettingsSheet {
    fun show(ctx: Context, cb: SettingsCallbacks) {
        val cfg = AppConfig(ctx)
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(42, 20, 42, 16) }
        fun lbl(t: String) = root.addView(TextView(ctx).apply { text = t; textSize = 12f; setPadding(0, 14, 0, 4) })
        fun et(h: String, v: String) = EditText(ctx).apply { hint = h; setText(v); textSize = 12f }.also { root.addView(it) }
        fun btn(t: String, f: () -> Unit) = root.addView(Button(ctx).apply { text = t; textSize = 12f; setOnClickListener { f() } })
        fun sw(t: String, v: Boolean) = Switch(ctx).apply { text = t; isChecked = v; textSize = 13f }.also { root.addView(it) }

        btn("📖 টিউটোরিয়াল ও গাইড দেখুন") { cb.onTutorial() }
        lbl("👤 Persona & Custom Instructions")
        val etName = et("Companion Name", cfg.companionName); val etUser = et("Your Name", cfg.userName)
        val etPrompt = et("Custom Instructions / Persona", cfg.customPrompt)
        val vibes = listOf("HUMAN_COMPANION", "JARVIS_TACTICAL", "GAMING_SQUAD")
        val spVibe = Spinner(ctx).apply { adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, vibes); setSelection(vibes.indexOf(cfg.vibePreset).coerceAtLeast(0)) }
        lbl("🎭 Vibe Preset"); root.addView(spVibe)
        val modes = listOf("HYBRID", "ONLINE", "OFFLINE")
        val spMode = Spinner(ctx).apply { adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, modes); setSelection(modes.indexOf(cfg.brainMode).coerceAtLeast(0)) }
        lbl("🧠 AI Engine Mode"); root.addView(spMode)
        val swCouncil = sw("JARVIS Council (Multi-API তুলনা)", cfg.councilMode)
        val swUnc = sw("Private Uncensored Mode", cfg.uncensoredMode)
        val swEco = sw("Gaming Eco Mode (6GB RAM)", cfg.ecoMode)
        val swHands = sw("Hands-Free Voice Loop", cfg.handsFreeLoop)
        val swCare = sw("24/7 Proactive Care", cfg.proactiveCare)
        val swWa = sw("WhatsApp & W4B Auto-Draft", cfg.whatsappAssistant)
        val swShiz = sw("Use Shizuku System Control", cfg.useShizuku)
        val provs = listOf("OPENROUTER", "GEMINI", "GROQ", "OPENAI")
        val spProv = Spinner(ctx).apply { adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, provs); setSelection(provs.indexOf(cfg.cloudProvider).coerceAtLeast(0)) }
        lbl("☁️ Primary Cloud & All 4 API Keys"); root.addView(spProv)
        val etGemKey = et("Gemini Key (AIza...)", cfg.geminiApiKey)
        val etGemMod = et("Gemini Model", cfg.geminiModel)
        val etOrKey = et("OpenRouter Key (sk-or-...)", cfg.openrouterApiKey)
        val etOrMod = et("OpenRouter Model", cfg.openrouterModel)
        val etGrqKey = et("Groq Key (gsk_...)", cfg.groqApiKey)
        val etOaiKey = et("OpenAI Key (sk-...)", cfg.openaiApiKey)
        lbl("📦 Offline .gguf (Text: ${File(cfg.localTextModelPath).name.ifBlank { "None" }})")
        btn("⬇️ Auto-Download Llama-3.2-1B (.gguf)") { cb.onDownloadAsset("text_model") }
        btn("⬇️ Auto-Download SmolVLM Vision") { cb.onDownloadAsset("vision_model") }
        btn("Pick Text .gguf") { cb.onPickTextGguf() }
        btn("Pick Vision .gguf") { cb.onPickVisionGguf() }
        btn("Pick .mmproj") { cb.onPickMmproj() }
        lbl("✈️ Telegram Private Bot Bridge")
        val swTg = sw("Enable Telegram Human Bridge", cfg.telegramHumanEnabled)
        val etTgTok = et("@BotFather Bot Token", cfg.telegramToken)
        lbl("Locked Chat ID: ${cfg.telegramChatId.ifBlank { "বটে /start লিখলে অটো লক হবে" }}")
        btn("Test Telegram Message") {
            CoroutineScope(Dispatchers.IO).launch {
                val ok = TelegramBridge.sendMessage(etTgTok.text.toString().trim(), cfg.telegramChatId, "হ্যালো! আমি ${cfg.companionName} 💜")
                withContext(Dispatchers.Main) { Toast.makeText(ctx, if (ok) "✅ Sent!" else "❌ আগে বটে /start দিন", Toast.LENGTH_SHORT).show() }
            }
        }
        lbl("🎨 3D Avatar & Theme Studio")
        btn("⬇️ Auto-Download Sample 3D .vrm") { cb.onDownloadAsset("avatar") }
        btn("Import .vrm / .glb") { cb.onPickAvatar() }
        btn("Switch Avatar / Built-in") { showAvatarPicker(ctx, cfg, cb.onSaved) }
        btn("🖼️ Pick 3D Stage Wallpaper") { cb.onPickWallpaper() }
        btn("Clear Wallpaper") { cfg.stageWallpaperPath = ""; cb.onSaved() }
        val etBg = et("Bg Hex", cfg.stageBgColor); val etAcc = et("Accent Hex", cfg.stageAccentColor)
        val etPitch = et("Voice Pitch", cfg.ttsPitch.toString()); val etSpeed = et("Voice Speed", cfg.ttsSpeed.toString())
        lbl("⚡ Shizuku: ${if (ShizukuBridge.hasPermission()) "✅ READY" else if (ShizukuBridge.isRunning()) "⚠️ Running" else "❌ Off"}")
        btn("Authorize Shizuku") { ShizukuBridge.requestPermission() }
        lbl("🛡️ HyperOS & Default Assistant")
        btn("1. Grant Mic/Cam/Notif/Contacts/Call") { cb.onRequestPerms() }
        btn("2. Default Digital Assistant (ASSIST)") { openAct(ctx, Settings.ACTION_VOICE_INPUT_SETTINGS, false) }
        btn("3. Floating Overlay") { openAct(ctx, Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true) }
        btn("4. Accessibility (JARVIS Engine)") { openAct(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS, false) }
        btn("5. Notification Access") { openAct(ctx, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, false) }
        btn("6. Usage Access") { openAct(ctx, Settings.ACTION_USAGE_ACCESS_SETTINGS, false) }
        btn("7. Battery: No Restrictions") { openAct(ctx, Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, true) }
        btn("8. HyperOS Autostart") { openAutoStart(ctx) }
        btn("🔄 Check GitHub APK Update") { cb.onCheckUpdate() }
        AlertDialog.Builder(ctx).setTitle("⚙️ HUMAYRA Settings").setView(ScrollView(ctx).apply { addView(root) })
            .setPositiveButton("Save") { _, _ ->
                cfg.companionName = etName.text.toString(); cfg.userName = etUser.text.toString(); cfg.customPrompt = etPrompt.text.toString()
                cfg.vibePreset = spVibe.selectedItem.toString(); cfg.brainMode = spMode.selectedItem.toString()
                cfg.councilMode = swCouncil.isChecked; cfg.uncensoredMode = swUnc.isChecked; cfg.ecoMode = swEco.isChecked
                cfg.handsFreeLoop = swHands.isChecked; cfg.proactiveCare = swCare.isChecked; cfg.whatsappAssistant = swWa.isChecked; cfg.useShizuku = swShiz.isChecked
                cfg.cloudProvider = spProv.selectedItem.toString(); cfg.geminiApiKey = etGemKey.text.toString().trim(); cfg.geminiModel = etGemMod.text.toString().trim()
                cfg.openrouterApiKey = etOrKey.text.toString().trim(); cfg.openrouterModel = etOrMod.text.toString().trim()
                cfg.groqApiKey = etGrqKey.text.toString().trim(); cfg.openaiApiKey = etOaiKey.text.toString().trim()
                cfg.telegramHumanEnabled = swTg.isChecked; cfg.telegramToken = etTgTok.text.toString().trim()
                cfg.stageBgColor = etBg.text.toString().ifBlank { "#000000" }; cfg.stageAccentColor = etAcc.text.toString().ifBlank { "#60A5FA" }
                cfg.ttsPitch = etPitch.text.toString().toFloatOrNull()?.coerceIn(0.5f, 2f) ?: 1.05f; cfg.ttsSpeed = etSpeed.text.toString().toFloatOrNull()?.coerceIn(0.5f, 2f) ?: 1.02f
                Toast.makeText(ctx, "✅ Settings Saved", Toast.LENGTH_SHORT).show(); cb.onSaved()
            }.setNegativeButton("Cancel", null).show()
    }
    private fun showAvatarPicker(ctx: Context, cfg: AppConfig, onDone: () -> Unit) { val l = cfg.getAvatars(); AlertDialog.Builder(ctx).setTitle("Select 3D Avatar").setItems((listOf("✨ Built-in") + l.map { it.name }).toTypedArray()) { _, i -> cfg.activeAvatarPath = if (i == 0) "" else l[i - 1].path; onDone() }.show() }
    private fun openAct(ctx: Context, act: String, pkg: Boolean) = runCatching { ctx.startActivity((if (pkg) Intent(act, Uri.parse("package:${ctx.packageName}")) else Intent(act)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }
    private fun openAutoStart(ctx: Context) { listOf(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")), Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))).forEach { runCatching { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } } }
}
