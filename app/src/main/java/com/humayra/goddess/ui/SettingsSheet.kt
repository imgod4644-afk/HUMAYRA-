package com.humayra.goddess.ui

import android.app.AlertDialog
import android.content.*
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.*
import com.humayra.goddess.storage.*
import java.io.File

data class SettingsCallbacks(
    val onPickTextGguf: () -> Unit, val onPickVisionGguf: () -> Unit, val onPickMmproj: () -> Unit,
    val onPickAvatar: () -> Unit, val onSaved: () -> Unit, val onRequestPerms: () -> Unit
)

object SettingsSheet {
    fun show(ctx: Context, cb: SettingsCallbacks) {
        val cfg = AppConfig(ctx)
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(44, 24, 44, 16) }
        fun addLbl(t: String) = root.addView(TextView(ctx).apply { text = t; textSize = 12f; setPadding(0, 16, 0, 4) })
        fun addEt(hint: String, v: String) = EditText(ctx).apply { this.hint = hint; setText(v); textSize = 13f }.also { root.addView(it) }
        fun addBtn(t: String, f: () -> Unit) = root.addView(Button(ctx).apply { text = t; textSize = 12f; setOnClickListener { f() } })
        fun addSw(t: String, v: Boolean) = Switch(ctx).apply { text = t; isChecked = v; textSize = 13f }.also { root.addView(it) }
        addLbl("👤 Companion & Persona")
        val etName = addEt("Companion Name", cfg.companionName)
        val etUser = addEt("Your Name", cfg.userName)
        val etPrompt = addEt("Custom System Prompt (optional)", cfg.customPrompt)
        val modes = listOf("HYBRID", "ONLINE", "OFFLINE")
        val spMode = Spinner(ctx).apply { adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, modes); setSelection(modes.indexOf(cfg.brainMode).coerceAtLeast(0)) }
        addLbl("🧠 AI Engine Mode"); root.addView(spMode)
        val swUnc = addSw("Uncensored Private Device Mode", cfg.uncensoredMode)
        val swEco = addSw("Gaming Eco Mode (6 GB RAM Safe)", cfg.ecoMode)
        val swCare = addSw("24/7 Proactive Care Alerts", cfg.proactiveCare)
        val swWa = addSw("WhatsApp & W4B Auto-Reply", cfg.whatsappAssistant)
        addLbl("📦 Offline Local .gguf (Text: ${File(cfg.localTextModelPath).name.ifBlank { "None" }})")
        addBtn("Pick Text .gguf (1B-3B Q4_K_M)") { cb.onPickTextGguf() }
        addBtn("Pick SmolVLM Vision .gguf") { cb.onPickVisionGguf() }
        addBtn("Pick Vision .mmproj") { cb.onPickMmproj() }
        val provs = listOf("OPENROUTER", "OPENAI", "GROQ", "GEMINI")
        val spProv = Spinner(ctx).apply { adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, provs); setSelection(provs.indexOf(cfg.cloudProvider).coerceAtLeast(0)) }
        addLbl("☁️ Online Cloud API"); root.addView(spProv)
        val etUrl = addEt("API Base URL", cfg.cloudBaseUrl)
        val etKey = addEt("API Key", cfg.cloudApiKey)
        val etModel = addEt("Model ID", cfg.cloudModel)
        addLbl("🎭 3D Avatar & Stage")
        addBtn("Import .vrm / .glb Avatar") { cb.onPickAvatar() }
        addBtn("Switch Saved Avatar / Built-in") { showAvatarPicker(ctx, cfg, cb.onSaved) }
        val etBg = addEt("Stage Bg Hex (#0B0E14)", cfg.stageBgColor)
        val etAcc = addEt("Accent Rim Hex (#7C9CFF)", cfg.stageAccentColor)
        val etPitch = addEt("Voice Pitch (1.05)", cfg.ttsPitch.toString())
        val etSpeed = addEt("Voice Speed (1.02)", cfg.ttsSpeed.toString())
        addLbl("🛡️ HyperOS 3 / Android 16 Permissions")
        addBtn("1. Grant Mic, Notif, Contacts, Call") { cb.onRequestPerms() }
        addBtn("2. Floating Overlay") { openAct(ctx, Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true) }
        addBtn("3. Accessibility (JARVIS UI)") { openAct(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS, false) }
        addBtn("4. Notification Access (WhatsApp)") { openAct(ctx, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, false) }
        addBtn("5. Usage Access (Screen Time)") { openAct(ctx, Settings.ACTION_USAGE_ACCESS_SETTINGS, false) }
        addBtn("6. Battery: No Restrictions") { openAct(ctx, Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, true) }
        addBtn("7. HyperOS Autostart") { openAutoStart(ctx) }
        AlertDialog.Builder(ctx).setTitle("⚙️ HUMAYRA Settings").setView(ScrollView(ctx).apply { addView(root) })
            .setPositiveButton("Save") { _, _ ->
                cfg.companionName = etName.text.toString(); cfg.userName = etUser.text.toString(); cfg.customPrompt = etPrompt.text.toString()
                cfg.brainMode = spMode.selectedItem.toString(); cfg.uncensoredMode = swUnc.isChecked; cfg.ecoMode = swEco.isChecked
                cfg.proactiveCare = swCare.isChecked; cfg.whatsappAssistant = swWa.isChecked
                cfg.cloudProvider = spProv.selectedItem.toString(); cfg.cloudBaseUrl = etUrl.text.toString().trim()
                cfg.cloudApiKey = etKey.text.toString().trim(); cfg.cloudModel = etModel.text.toString().trim()
                cfg.stageBgColor = etBg.text.toString().ifBlank { "#0B0E14" }; cfg.stageAccentColor = etAcc.text.toString().ifBlank { "#7C9CFF" }
                cfg.ttsPitch = etPitch.text.toString().toFloatOrNull()?.coerceIn(0.5f, 2f) ?: 1.05f
                cfg.ttsSpeed = etSpeed.text.toString().toFloatOrNull()?.coerceIn(0.5f, 2f) ?: 1.02f; cb.onSaved()
            }.setNegativeButton("Cancel", null).show()
    }
    private fun showAvatarPicker(ctx: Context, cfg: AppConfig, onDone: () -> Unit) {
        val list = cfg.getAvatars()
        val labels = (listOf("✨ Built-in Cyber Goddess") + list.map { "${it.name} (${it.style})" }).toTypedArray()
        AlertDialog.Builder(ctx).setTitle("Select 3D Character").setItems(labels) { _, idx ->
            cfg.activeAvatarPath = if (idx == 0) "" else list[idx - 1].path; onDone()
        }.show()
    }

    private fun openAct(ctx: Context, action: String, withPkg: Boolean) = try {
        val i = if (withPkg) Intent(action, Uri.parse("package:${ctx.packageName}")) else Intent(action)
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
    }

    private fun openAutoStart(ctx: Context) {
        val list = listOf(
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
        )
        for (i in list) { try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: Exception) {} }
    }
}
