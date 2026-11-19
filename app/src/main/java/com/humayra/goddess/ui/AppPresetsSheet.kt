package com.humayra.goddess.ui

import android.content.*
import android.graphics.Color
import android.provider.Settings
import android.widget.*
import androidx.core.widget.addTextChangedListener
import com.humayra.goddess.service.HumayraAccessibilityService
import com.humayra.goddess.shizuku.ShizukuBridge
import com.humayra.goddess.storage.*
import kotlinx.coroutines.*

object AppPresetsSheet {
    fun show(ctx: Context) {
        val store = AppPresetStore(ctx); val cfg = AppConfig(ctx)
        lateinit var etSsid: EditText; lateinit var etPass: EditText
        FullPage.show(ctx, "📱 App Presets & Shizuku", "Save Wi-Fi", { d ->
            cfg.savedWifiSsid = etSsid.text.toString().trim(); cfg.savedWifiPass = etPass.text.toString()
            Toast.makeText(ctx, "✅ Wi-Fi Saved", Toast.LENGTH_SHORT).show(); d.dismiss()
        }) { root, mainDlg ->
            val shMsg = if (ShizukuBridge.hasPermission()) "✅ Shizuku READY (Full Control)" else if (ShizukuBridge.isRunning()) "⚠️ Shizuku Running — Authorize করুন" else "❌ Shizuku Off"
            root.addView(TextView(ctx).apply { text = shMsg; textSize = 13f; setTextColor(Color.parseColor("#60A5FA")) })
            if (!ShizukuBridge.hasPermission()) root.addView(Button(ctx).apply { text = "⚡ Authorize Shizuku"; setOnClickListener { ShizukuBridge.requestPermission() } })
            root.addView(TextView(ctx).apply { text = "\n📶 Saved Wi-Fi & MAC"; textSize = 13f; setTextColor(Color.WHITE) })
            etSsid = EditText(ctx).apply { hint = "Saved Wi-Fi SSID"; setText(cfg.savedWifiSsid); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
            etPass = EditText(ctx).apply { hint = "Wi-Fi Password"; setText(cfg.savedWifiPass); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
            root.addView(etSsid); root.addView(etPass)
            root.addView(Button(ctx).apply { text = "🔄 Rotate Wi-Fi MAC Now (Shizuku)"; setOnClickListener {
                CoroutineScope(Dispatchers.Main).launch {
                    val r = ShizukuBridge.rotateWifiMac(ctx, etSsid.text.toString(), etPass.text.toString())
                    Toast.makeText(ctx, if (r.isSuccess) "✅ MAC Re-Randomized!" else r.exceptionOrNull()?.message, Toast.LENGTH_SHORT).show()
                }
            } })
            root.addView(Button(ctx).apply { text = "🛠️ Dev Options (Non-Persistent MAC)"; setOnClickListener {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            } })
            root.addView(TextView(ctx).apply { text = "\n📱 Per-App Auto Presets (ট্যাপ করে বদলান):"; textSize = 14f; setTextColor(Color.parseColor("#60A5FA")) })
            root.addView(Button(ctx).apply { text = "➕ Add / Search Installed App or APK"; setOnClickListener { pickApp(ctx) { mainDlg.dismiss(); show(ctx) } } })
            root.addView(Button(ctx).apply { text = "✍️ Add Custom Package Name"; setOnClickListener { askManualPkg(ctx) { mainDlg.dismiss(); show(ctx) } } })
            store.getAll().sortedBy { it.label.lowercase() }.forEach { p ->
                val st = if (p.enabled) "✅" else "⏸️"
                val tags = listOfNotNull(if (p.volumePct >= 0) "🔊${p.volumePct}%" else null, if (p.brightnessPct > 0) "☀️${p.brightnessPct}%" else null, if (p.forceStopOnExit) "⚡" else null, if (p.blindPrivacy) "🔒" else null, if (p.ecoMode) "🎮" else null).joinToString(" ")
                root.addView(Button(ctx).apply { text = "$st ${p.label} ${if (tags.isNotBlank()) "[$tags]" else ""}"; setOnClickListener { showForApp(ctx, p.pkg, p.label) { mainDlg.dismiss(); show(ctx) } } })
            }
        }
    }
    private fun pickApp(ctx: Context, onUpdated: () -> Unit) {
        val pm = ctx.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }.distinctBy { it.second }.sortedBy { it.first.lowercase() }
        FullPage.show(ctx, "🔍 Select App / APK") { body, dlg ->
            val etQ = EditText(ctx).apply { hint = "Search app name or package..."; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
            val listBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            fun render(q: String) {
                listBox.removeAllViews()
                apps.filter { q.isBlank() || it.first.contains(q, true) || it.second.contains(q, true) }.forEach { (lbl, pkg) ->
                    listBox.addView(Button(ctx).apply { text = "$lbl\n($pkg)"; setOnClickListener { dlg.dismiss(); showForApp(ctx, pkg, lbl, onUpdated) } })
                }
            }
            render(""); etQ.addTextChangedListener { render(it?.toString().orEmpty()) }
            body.addView(etQ); body.addView(listBox)
        }
    }
    private fun askManualPkg(ctx: Context, onUpdated: () -> Unit) {
        FullPage.show(ctx, "✍️ Custom Package") { body, dlg ->
            val etPkg = EditText(ctx).apply { hint = "Package (e.g. com.game.app)"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
            val etLbl = EditText(ctx).apply { hint = "Display Name"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
            body.addView(etPkg); body.addView(etLbl)
            body.addView(Button(ctx).apply { text = "Next →"; setOnClickListener {
                val p = etPkg.text.toString().trim(); if (p.isBlank()) return@setOnClickListener
                val l = etLbl.text.toString().trim().ifBlank { p.substringAfterLast('.') }
                dlg.dismiss(); showForApp(ctx, p, l, onUpdated)
            } })
        }
    }
    fun showForApp(ctx: Context, pkg: String, label: String, onUpdated: (() -> Unit)? = null) {
        val store = AppPresetStore(ctx); val p = store.find(pkg) ?: AppPreset(pkg, label)
        lateinit var saveFn: () -> Unit
        FullPage.show(ctx, "⚙️ $label", "Save", { dlg -> saveFn(); dlg.dismiss() }) { root, dlg ->
            fun lbl(t: String) = root.addView(TextView(ctx).apply { text = t; textSize = 12f; setTextColor(Color.parseColor("#60A5FA")); setPadding(0, 14, 0, 4) })
            fun sw(t: String, v: Boolean) = Switch(ctx).apply { text = t; isChecked = v; setTextColor(Color.WHITE) }.also { root.addView(it) }
            fun et(h: String, v: String) = EditText(ctx).apply { hint = h; setText(v); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }.also { root.addView(it) }
            fun tri(t: String, v: Int): Spinner {
                lbl(t); val opts = listOf("অপরিবর্তিত (Keep)", "ON (চালু)", "OFF (বন্ধ)")
                return Spinner(ctx).apply { adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, opts); setSelection(when (v) { 1 -> 1; 0 -> 2; else -> 0 }) }.also { root.addView(it) }
            }
            fun readTri(sp: Spinner) = when (sp.selectedItemPosition) { 1 -> 1; 2 -> 0; else -> -1 }
            val sEn = sw("✅ Preset Enabled (অটো কাজ চালু)", p.enabled)
            lbl("🎚️ Levels (-1 = অপরিবর্তিত)"); val etVol = et("Media Volume % (0-100)", p.volumePct.toString())
            val etBri = et("Brightness % (1-100)", p.brightnessPct.toString())
            val etTo = et("Screen Timeout sec (15-1800)", p.timeoutSec.toString())
            val spDnd = tri("🔕 DND / Focus Mode (Shizuku)", p.dndMode)
            val spDark = tri("🌙 Dark Mode (Shizuku)", p.darkMode)
            val spRot = tri("🔄 Auto-Rotate (Shizuku)", p.autoRotate)
            val spWifi = tri("📶 Wi-Fi (Shizuku)", p.wifiState)
            val spBt = tri("🔵 Bluetooth (Shizuku)", p.btState)
            val spData = tri("📱 Mobile Data (Shizuku)", p.dataState)
            val spBat = tri("🔋 Battery Saver (Shizuku)", p.batterySaver)
            lbl("⚡ App Switches")
            val sBlind = sw("🔒 Blind Privacy Shield", p.blindPrivacy); val sHide = sw("🔕 Hide Popups in Vault", p.hideNotifs)
            val sEco = sw("🎮 Gaming Eco Mode", p.ecoMode); val sOv = sw("🫧 Auto Floating Bubble", p.autoOverlay)
            val sStop = sw("⚡ Shizuku Force-Stop on Exit", p.forceStopOnExit); val sVpn = sw("🛡️ Auto VPN on Open", p.autoVpn)
            lbl("💻 Custom Shizuku Commands")
            val etEc = et("Enter Command (e.g. settings put ...)", p.customEnterCmd)
            val etXc = et("Exit Command (optional)", p.customExitCmd)
            fun buildPre() = p.copy(enabled = sEn.isChecked, blindPrivacy = sBlind.isChecked, hideNotifs = sHide.isChecked, ecoMode = sEco.isChecked,
                autoOverlay = sOv.isChecked, forceStopOnExit = sStop.isChecked, autoVpn = sVpn.isChecked,
                volumePct = etVol.text.toString().toIntOrNull() ?: -1, brightnessPct = etBri.text.toString().toIntOrNull() ?: -1,
                timeoutSec = etTo.text.toString().toIntOrNull() ?: -1, dndMode = readTri(spDnd), darkMode = readTri(spDark),
                autoRotate = readTri(spRot), wifiState = readTri(spWifi), btState = readTri(spBt), dataState = readTri(spData),
                batterySaver = readTri(spBat), customEnterCmd = etEc.text.toString().trim(), customExitCmd = etXc.text.toString().trim())
            saveFn = { store.upsert(buildPre()); HumayraAccessibilityService.invalidatePresetCache(); Toast.makeText(ctx, "✅ Saved: $label", Toast.LENGTH_SHORT).show(); onUpdated?.invoke() }
            root.addView(Button(ctx).apply { text = "⚡ Test Preset Now"; setOnClickListener { HumayraAccessibilityService.applyPresetNow(ctx, buildPre()); Toast.makeText(ctx, "⚡ Applied!", Toast.LENGTH_SHORT).show() } })
            root.addView(Button(ctx).apply { text = "💾 Save Preset"; setOnClickListener { saveFn(); dlg.dismiss() } })
            root.addView(Button(ctx).apply { text = "🗑️ Delete Preset"; setOnClickListener { store.delete(pkg); HumayraAccessibilityService.invalidatePresetCache(); dlg.dismiss(); onUpdated?.invoke() } })
        }
    }
}
