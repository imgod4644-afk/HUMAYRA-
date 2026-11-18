package com.humayra.goddess.ui

import android.app.AlertDialog
import android.content.*
import android.provider.Settings
import android.widget.*
import com.humayra.goddess.shizuku.ShizukuBridge
import com.humayra.goddess.storage.*
import kotlinx.coroutines.*

object AppPresetsSheet {
    fun show(ctx: Context) {
        val store = AppPresetStore(ctx); val cfg = AppConfig(ctx)
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(42, 20, 42, 16) }
        root.addView(TextView(ctx).apply { text = "📶 Saved Wi-Fi & MAC Randomization"; textSize = 13f })
        val etSsid = EditText(ctx).apply { hint = "Saved Wi-Fi SSID"; setText(cfg.savedWifiSsid) }
        val etPass = EditText(ctx).apply { hint = "Wi-Fi Password (optional)"; setText(cfg.savedWifiPass) }
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
        root.addView(TextView(ctx).apply { text = "\n📱 Per-App Presets (ট্যাপ করে বদলান):"; textSize = 13f })
        root.addView(Button(ctx).apply { text = "➕ Add / Customize Installed App"; setOnClickListener { pickApp(ctx, store) } })
        store.getAll().forEach { p ->
            root.addView(Button(ctx).apply { text = "${p.label} ${if (p.blindPrivacy) "🔒" else ""}${if (p.ecoMode) "🎮" else ""}"; setOnClickListener { editPreset(ctx, store, p) } })
        }
        AlertDialog.Builder(ctx).setTitle("📱 App Presets & Wi-Fi").setView(ScrollView(ctx).apply { addView(root) })
            .setPositiveButton("Save Wi-Fi") { _, _ -> cfg.savedWifiSsid = etSsid.text.toString().trim(); cfg.savedWifiPass = etPass.text.toString() }
            .setNegativeButton("Close", null).show()
    }

    private fun pickApp(ctx: Context, store: AppPresetStore) {
        val pm = ctx.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }.distinctBy { it.second }.sortedBy { it.first.lowercase() }
        AlertDialog.Builder(ctx).setTitle("Select App").setItems(apps.map { it.first }.toTypedArray()) { _, i ->
            val (lbl, pkg) = apps[i]; editPreset(ctx, store, store.find(pkg) ?: AppPreset(pkg, lbl))
        }.show()
    }

    private fun editPreset(ctx: Context, store: AppPresetStore, p: AppPreset) {
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(42, 16, 42, 8) }
        fun sw(t: String, v: Boolean) = Switch(ctx).apply { text = t; isChecked = v }.also { root.addView(it) }
        val sBlind = sw("🔒 Blind Privacy Shield", p.blindPrivacy); val sHide = sw("🔕 Hide Popups in Vault", p.hideNotifs)
        val sEco = sw("🎮 Gaming Eco Mode", p.ecoMode); val sOv = sw("🫧 Auto Floating Bubble", p.autoOverlay)
        val sStop = sw("⚡ Shizuku Force-Stop on Exit", p.forceStopOnExit); val sVpn = sw("🛡️ Auto VPN", p.autoVpn)
        val etVol = EditText(ctx).apply { hint = "Volume % (-1 = unchanged)"; setText(p.volumePct.toString()) }; root.addView(etVol)
        AlertDialog.Builder(ctx).setTitle("⚙️ ${p.label}").setView(root).setPositiveButton("Save") { _, _ ->
            store.upsert(p.copy(blindPrivacy = sBlind.isChecked, hideNotifs = sHide.isChecked, ecoMode = sEco.isChecked,
                autoOverlay = sOv.isChecked, forceStopOnExit = sStop.isChecked, autoVpn = sVpn.isChecked, volumePct = etVol.text.toString().toIntOrNull() ?: -1))
            Toast.makeText(ctx, "✅ Preset Saved: ${p.label}", Toast.LENGTH_SHORT).show()
        }.setNegativeButton("Cancel", null).show()
    }
}
