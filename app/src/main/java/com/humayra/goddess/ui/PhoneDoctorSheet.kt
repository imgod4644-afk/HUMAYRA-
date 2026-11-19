package com.humayra.goddess.ui

import android.content.Context
import android.graphics.Color
import android.os.*
import android.widget.*
import com.humayra.goddess.service.HumayraAccessibilityService
import com.humayra.goddess.shizuku.ShizukuBridge
import com.humayra.goddess.storage.MemoryStore
import com.humayra.goddess.telemetry.PhoneDoctorTelemetry
import java.util.Locale

object PhoneDoctorSheet {
    fun showDoctor(ctx: Context, onAskOptimize: () -> Unit) {
        val h = Handler(Looper.getMainLooper())
        lateinit var tv: TextView
        val d = FullPage.show(ctx, "🩺 Live Phone Status", "Ask AI", { dlg -> dlg.dismiss(); onAskOptimize() }) { body, _ ->
            tv = TextView(ctx).apply { textSize = 14f; setTextColor(Color.WHITE); setLineSpacing(8f, 1.15f) }
            body.addView(tv)
        }
        val tick = object : Runnable {
            override fun run() {
                if (!d.isShowing) return
                val t = PhoneDoctorTelemetry.collect(ctx, true)
                val sh = if (ShizukuBridge.hasPermission()) "✅ READY" else if (ShizukuBridge.isRunning()) "⚠️ Grant Perm" else "❌ Off"
                val acc = if (HumayraAccessibilityService.instance != null) "✅ ON" else "❌ OFF"
                val pre = HumayraAccessibilityService.activePresetLabel.ifBlank { "None" }
                tv.text = buildString {
                    appendLine("⚡ LIVE TELEMETRY (Auto-refresh 2s)\n")
                    appendLine("🔋 Battery: ${t.batteryPct}% (${t.chargingSource})")
                    appendLine("🌡 Temp: ${String.format(Locale.US, "%.1f", t.tempC)}°C | Volt: ${t.voltageMv}mV | ${t.health}")
                    appendLine("🧠 RAM Used: ${String.format(Locale.US, "%.2f / %.2f", t.ramUsedGb, t.ramTotalGb)} GB")
                    appendLine("💾 Storage Free: ${String.format(Locale.US, "%.1f / %.1f", t.storageFreeGb, t.storageTotalGb)} GB")
                    appendLine("🌐 Net: ${t.networkSummary} | VPN: ${t.vpnActive} | BT: ${t.bluetoothOn}")
                    appendLine("🔧 Shizuku: $sh | Accessibility: $acc")
                    appendLine("🎯 Active Preset: $pre (${HumayraAccessibilityService.activePackage.ifBlank { "Idle" }})")
                    appendLine("📱 Today Screen: ${t.screenTimeMin} min")
                    append("🔥 Top Apps: ${if (t.topApps.isEmpty()) "Usage Access দিন" else t.topApps.joinToString(", ")}")
                }
                h.postDelayed(this, 2000L)
            }
        }
        d.setOnDismissListener { h.removeCallbacks(tick) }; h.post(tick)
    }
    fun showHistoryAndMemory(ctx: Context) {
        val mem = MemoryStore(ctx)
        lateinit var etMem: EditText; lateinit var tvHist: TextView
        FullPage.show(ctx, "📜 History & Memory", "Save", { dlg -> mem.replaceAllMemories(etMem.text.toString().lines()); dlg.dismiss() }) { body, _ ->
            val histText = mem.getHistory().takeLast(25).joinToString("\n\n") { "${if (it.role == "user") "🧑 You" else "✨ HUMAYRA"}: ${it.text}" }.ifBlank { "এখনো কোনো চ্যাট হিস্ট্রি নেই।" }
            tvHist = TextView(ctx).apply { text = histText; textSize = 13f; setTextColor(Color.WHITE) }
            body.addView(tvHist)
            body.addView(TextView(ctx).apply { text = "\n🧠 Long-Term Memory (প্রতি লাইনে ১টি তথ্য):"; textSize = 13f; setTextColor(Color.parseColor("#60A5FA")) })
            etMem = EditText(ctx).apply { setText(mem.getMemories().joinToString("\n")); minLines = 4; setTextColor(Color.WHITE); textSize = 13f }
            body.addView(etMem)
            body.addView(Button(ctx).apply { text = "🧹 Clear Chat History"; setOnClickListener { mem.clearHistory(); tvHist.text = "Cleared." } })
        }
    }
}
