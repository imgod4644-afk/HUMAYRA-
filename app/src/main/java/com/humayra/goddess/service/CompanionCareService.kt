package com.humayra.goddess.service

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import com.humayra.goddess.*
import com.humayra.goddess.ai.*
import com.humayra.goddess.storage.*
import com.humayra.goddess.telemetry.*
import com.humayra.goddess.ui.MainActivity
import kotlinx.coroutines.*
import java.util.Calendar

class CompanionCareService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wasVpnOn = false; private var lastClipTs = 0L; private var lastCarePassMs = 0L
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        val n = buildStatusNotif("CyberShield & 24/7 Guard Active")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) startForeground(401, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(401, n)
        scope.launch {
            while (isActive) {
                val cfg = AppConfig(this@CompanionCareService); if (!cfg.masterEnabled) { stopSelf(); break }
                if (cfg.telegramHumanEnabled) TelegramBridge.pollOnce(this@CompanionCareService)
                val now = System.currentTimeMillis()
                if (now - lastCarePassMs >= 120_000L) {
                    lastCarePassMs = now; runCarePass(this@CompanionCareService)
                    val t = PhoneDoctorTelemetry.collect(this@CompanionCareService)
                    if (cfg.vpnDropAlert && wasVpnOn && !t.vpnActive) fireAlert(this@CompanionCareService, "🛡️ VPN Drop", "আপনার VPN ডিসকানেক্ট হয়েছে!", 3009)
                    wasVpnOn = t.vpnActive; runCatching { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(401, buildStatusNotif("🔋 ${t.batteryPct}% • 🌡 ${t.tempC}°C • Shield ON")) }
                }
                checkClipboard(cfg, now); delay(8000)
            }
        }
    }
    private fun checkClipboard(cfg: AppConfig, now: Long) {
        if (!cfg.clipboardGuard) return
        if (lastClipTs > 0 && now - lastClipTs > 40_000L) { lastClipTs = 0L; Handler(Looper.getMainLooper()).post { runCatching { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("", "")) } } }
        else if (lastClipTs == 0L) { Handler(Looper.getMainLooper()).post { runCatching { val t = (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString().orEmpty(); if (t.isNotBlank() && (Regex("\\b\\d{4,8}\\b").containsMatchIn(t) || t.contains("sk-") || t.contains("AIza"))) lastClipTs = now } } }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    private fun buildStatusNotif(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val vis = if (AppConfig(this).lockScreenPrivacy) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC
        return NotificationCompat.Builder(this, HumayraApp.CH_CARE).setSmallIcon(R.drawable.ic_humayra_emblem).setContentTitle("${AppConfig(this).companionName} Guard").setContentText(text).setVisibility(vis).setContentIntent(pi).setOngoing(true).build()
    }
    companion object {
        fun runCarePass(ctx: Context) {
            val cfg = AppConfig(ctx); if (!cfg.masterEnabled) return
            val mem = MemoryStore(ctx); val now = System.currentTimeMillis(); var ch = false
            val up = mem.getReminders().map { r -> if (!r.done && r.triggerAtMs <= now) { ch = true; fireAlert(ctx, "⏰ Reminder", r.message, (r.id % 9000).toInt() + 100); r.copy(done = true) } else r }
            if (ch) mem.saveReminders(up); if (!cfg.proactiveCare || now - cfg.lastCareAlertMs < 55 * 60_000L) return
            val t = PhoneDoctorTelemetry.collect(ctx); val hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val scrOn = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
            val msg = pickVariedMsg(ctx, cfg, t, hr, scrOn)
            if (msg != null) { cfg.lastCareAlertMs = now; fireAlert(ctx, "🌙 ${cfg.companionName}", msg, 3001) }
        }
        private fun pickVariedMsg(ctx: Context, cfg: AppConfig, t: TelemetrySnapshot, hr: Int, scrOn: Boolean): String? {
            if (t.tempC > 39.0f) return listOf("ফোন ${t.tempC}°C গরম হয়েছে, একটু বিশ্রাম দাও!", "তাপমাত্রা ${t.tempC}°C—ফোন একটু ঠান্ডা হতে দাও!").random()
            if (t.batteryPct < 20 && t.chargingSource == "On Battery") return listOf("ব্যাটারি ${t.batteryPct}%-এ নেমেছে, চার্জার দাও!", "চার্জ মাত্র ${t.batteryPct}%—চার্জে লাগাও!").random()
            if (t.batteryPct >= 100 && t.chargingSource != "On Battery") return listOf("ব্যাটারি ১০০% ফুল, চার্জার খোলো!", "ফুল চার্জ হয়ে গেছে বস—চার্জার খুলে রাখো।").random()
            if (scrOn && hr in 0..4) return listOf("রাত ${hr}টা বাজে—এবার ঘুমাও তো!", "অনেক রাত হলো, আমি আছি—ঘুমিয়ে পড়ো।").random()
            if (!scrOn || hr !in 8..23) return null
            val aiMsg = runBlocking { withTimeoutOrNull(12_000) {
                if (HybridBrain.isOnline(ctx) && CloudAiClient.availableTargets(cfg).isNotEmpty())
                    HybridBrain.ask(ctx, "Write ONE short fresh varied casual check-in (max 15 words, Bengali/Banglish, warm female companion tone, time ${hr}:00, bat ${t.batteryPct}%, temp ${t.tempC}C). No actions.").first.take(120)
                else null
            } }
            return aiMsg?.takeIf { it.isNotBlank() && !it.contains("Error", true) } ?: listOf(
                "কি করছো? একটু পানি খেয়ে নাও তো 💧", "ফোন ফিট আছে (${t.batteryPct}%, ${t.tempC}°C) — কোনো কাজ থাকলে বলো!",
                "কাজের ফাঁকে চোখকে ৩০ সেকেন্ড বিশ্রাম দাও 💜", "এত চুপচাপ কেন, নতুন কিছু বানাব নাকি?", "সব সিস্টেম ক্লিয়ার—তোমার মুড কেমন?"
            ).random()
        }
        fun fireAlert(ctx: Context, title: String, body: String, id: Int) {
            val cfg = AppConfig(ctx); val vis = if (cfg.lockScreenPrivacy) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC
            val n = NotificationCompat.Builder(ctx, HumayraApp.CH_ALERTS).setSmallIcon(R.drawable.ic_humayra_emblem).setContentTitle(title).setContentText(body).setVisibility(vis).setAutoCancel(true).build()
            try { (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, n) } catch (_: SecurityException) {}
            if (cfg.telegramHumanEnabled && cfg.telegramToken.isNotBlank() && cfg.telegramChatId.isNotBlank()) CoroutineScope(Dispatchers.IO).launch { TelegramBridge.sendMessage(cfg.telegramToken, cfg.telegramChatId, "$title: $body") }
        }
    }
}
    companion object {
        fun runCarePass(ctx: Context) {
            val cfg = AppConfig(ctx); if (!cfg.masterEnabled) return
            val mem = MemoryStore(ctx); val now = System.currentTimeMillis(); var ch = false
            val up = mem.getReminders().map { r -> if (!r.done && r.triggerAtMs <= now) { ch = true; fireAlert(ctx, "⏰ Reminder", r.message, (r.id % 9000).toInt() + 100); r.copy(done = true) } else r }
            if (ch) mem.saveReminders(up); if (!cfg.proactiveCare || now - cfg.lastCareAlertMs < 55 * 60_000L) return
            val t = PhoneDoctorTelemetry.collect(ctx); val hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val scrOn = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
            val msg = pickVariedMsg(ctx, cfg, t, hr, scrOn)
            if (msg != null) { cfg.lastCareAlertMs = now; fireAlert(ctx, "🌙 ${cfg.companionName}", msg, 3001) }
        }
        private fun pickVariedMsg(ctx: Context, cfg: AppConfig, t: TelemetrySnapshot, hr: Int, scrOn: Boolean): String? {
            if (t.tempC > 39.0f) return listOf("ফোন ${t.tempC}°C গরম হয়েছে, একটু রাখো!", "তাপমাত্রা ${t.tempC}°C—ফোন ঠান্ডা হতে দাও!").random()
            if (t.batteryPct < 20 && t.chargingSource == "On Battery") return listOf("ব্যাটারি ${t.batteryPct}%, চার্জার দাও!", "চার্জ মাত্র ${t.batteryPct}%—চার্জে লাগাও!").random()
            if (t.batteryPct >= 100 && t.chargingSource != "On Battery") return listOf("চার্জ ১০০% ফুল, খুলে ফেলো!", "ব্যাটারি ফুল হয়েছে—চার্জার খোলো।").random()
            if (scrOn && hr in 0..4) return listOf("রাত ${hr}টা বাজে—ঘুমাও তো!", "অনেক রাত হলো, ঘুমিয়ে পড়ো।").random()
            if (!scrOn || hr !in 8..23) return null
            val aiMsg = runBlocking { withTimeoutOrNull(12_000) {
                if (HybridBrain.isOnline(ctx) && CloudAiClient.availableTargets(cfg).isNotEmpty())
                    HybridBrain.ask(ctx, "Write ONE short fresh varied casual check-in (max 15 words, Bengali/Banglish, warm female companion tone, time ${hr}:00, bat ${t.batteryPct}%, temp ${t.tempC}C). No actions.").first.take(120)
                else null
            } }
            return aiMsg?.takeIf { it.isNotBlank() && !it.contains("Error", true) } ?: listOf(
                "কি করছো? পানি খেয়ে নাও 💧", "ফোন ফিট (${t.batteryPct}%, ${t.tempC}°C) — কোনো কাজ থাকলে বলো!",
                "চোখকে ৩০ সেকেন্ড বিশ্রাম দাও 💜", "চুপচাপ কেন, নতুন কিছু বানাব নাকি?", "সব ক্লিয়ার—মুড কেমন এখন?"
            ).random()
        }
        fun fireAlert(ctx: Context, title: String, body: String, id: Int) {
            val cfg = AppConfig(ctx); val vis = if (cfg.lockScreenPrivacy) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC
            val n = NotificationCompat.Builder(ctx, HumayraApp.CH_ALERTS).setSmallIcon(R.drawable.ic_humayra_emblem).setContentTitle(title).setContentText(body).setVisibility(vis).setAutoCancel(true).build()
            try { (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, n) } catch (_: SecurityException) {}
            if (cfg.telegramHumanEnabled && cfg.telegramToken.isNotBlank() && cfg.telegramChatId.isNotBlank()) CoroutineScope(Dispatchers.IO).launch { TelegramBridge.sendMessage(cfg.telegramToken, cfg.telegramChatId, "$title: $body") }
        }
    }
}
