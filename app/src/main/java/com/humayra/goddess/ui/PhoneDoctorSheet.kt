package com.humayra.goddess.ui

import android.app.AlertDialog
import android.content.Context
import android.widget.*
import com.humayra.goddess.storage.MemoryStore
import com.humayra.goddess.telemetry.PhoneDoctorTelemetry
import java.util.Locale

object PhoneDoctorSheet {
    fun showDoctor(ctx: Context, onAskOptimize: () -> Unit) {
        val t = PhoneDoctorTelemetry.collect(ctx)
        val msg = buildString {
            appendLine("🔋 Battery: ${t.batteryPct}% (${t.chargingSource})")
            appendLine("🌡 Temp: ${String.format(Locale.US, "%.1f", t.tempC)}°C | Volt: ${t.voltageMv}mV | ${t.health}")
            appendLine("🧠 RAM Used: ${String.format(Locale.US, "%.2f / %.2f", t.ramUsedGb, t.ramTotalGb)} GB")
            appendLine("💾 Storage Free: ${String.format(Locale.US, "%.1f / %.1f", t.storageFreeGb, t.storageTotalGb)} GB")
            appendLine("🌐 Net: ${t.networkSummary} | VPN: ${t.vpnActive} | BT: ${t.bluetoothOn}")
            appendLine("📱 Today Screen: ${t.screenTimeMin} min")
            append("🔥 Top Apps: ${if (t.topApps.isEmpty()) "Settings থেকে Usage Access দিন" else t.topApps.joinToString(", ")}")
        }
        AlertDialog.Builder(ctx).setTitle("🩺 HUMAYRA Phone Doctor").setMessage(msg)
            .setPositiveButton("Analyze with HUMAYRA") { _, _ -> onAskOptimize() }
            .setNegativeButton("Close", null).show()
    }
    fun showHistoryAndMemory(ctx: Context) {
        val mem = MemoryStore(ctx)
        val layout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 24, 40, 12) }
        val histText = mem.getHistory().takeLast(25).joinToString("\n\n") {
            "${if (it.role == "user") "🧑 You" else "✨ HUMAYRA"}: ${it.text}"
        }.ifBlank { "এখনো কোনো চ্যাট হিস্ট্রি নেই।" }
        val tvHist = TextView(ctx).apply { text = histText; textSize = 12f }
        val sv = ScrollView(ctx).apply { layoutParams = LinearLayout.LayoutParams(-1, 380); addView(tvHist) }
        val lbl = TextView(ctx).apply { text = "\n🧠 Long-Term Memory (প্রতি লাইনে ১টি তথ্য):"; textSize = 13f }
        val etMem = EditText(ctx).apply { setText(mem.getMemories().joinToString("\n")); minLines = 4; maxLines = 6; textSize = 12f }
        layout.addView(sv); layout.addView(lbl); layout.addView(etMem)
        AlertDialog.Builder(ctx).setTitle("📜 History & Long-Term Memory").setView(layout)
            .setPositiveButton("Save Memory") { _, _ -> mem.replaceAllMemories(etMem.text.toString().lines()) }
            .setNeutralButton("Clear Chat") { _, _ -> mem.clearHistory() }
            .setNegativeButton("Close", null).show()
    }
}
