package com.humayra.goddess.service

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import com.humayra.goddess.*
import com.humayra.goddess.ai.*
import com.humayra.goddess.shizuku.ShizukuBridge
import com.humayra.goddess.storage.*
import com.humayra.goddess.telemetry.*
import com.humayra.goddess.ui.MainActivity
import kotlinx.coroutines.*
import java.util.*

class CompanionCareService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wasVpnOn = false; private var lastClipTs = 0L
    private var lastCarePassMs = 0L; private var lastLiveMs = 0L; private var lastScrOn = true
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        val n = buildStatusNotif("Live Guard Active", "Tap to view full real-time phone status")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) startForeground(401, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(401, n)
        scope.launch { loopCare() }
    }
    private suspend fun loopCare() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        while (scope.isActive) {
            val cfg = AppConfig(this); if (!cfg.masterEnabled) { stopSelf(); break }
            val scrOn = pm.isInteractive; val now = System.currentTimeMillis()
            if (scrOn && !lastScrOn) lastLiveMs = 0L; lastScrOn = scrOn
            if (cfg.telegramHumanEnabled) TelegramBridge.pollOnce(this)
            val t = if ((scrOn && now - lastLiveMs >= 16_000L) || now - lastCarePassMs >= 120_000L) PhoneDoctorTelemetry.collect(this, false) else null
            if (t != null && scrOn && now - lastLiveMs >= 16_000L) { lastLiveMs = now; pushLiveNotif(cfg, t) }
            if (t != null && now - lastCarePassMs >= 120_000L) {
                lastCarePassMs = now; runCarePass(this); wasVpnOn = checkVpnDrop(cfg, t)
            }
            if (scrOn) checkClipboard(cfg, now)
            val waitMs = when {
                scrOn -> 8000L
                cfg.telegramHumanEnabled && (t?.batteryPct ?: 50) < 20 -> 60_000L
                cfg.telegramHumanEnabled -> 30_000L
                else -> 90_000L
            }
            delay(waitMs)
        }
    }
    private fun checkVpnDrop(cfg: AppConfig, t: TelemetrySnapshot): Boolean {
        if (cfg.vpnDropAlert && wasVpnOn && !t.vpnActive) fireAlert(this, "🛡️ VPN Drop", "VPN disconnected!", 3009)
        return t.vpnActive
    }
    private fun pushLiveNotif(cfg: AppConfig, t: TelemetrySnapshot) {
        val sh = if (ShizukuBridge.hasPermission()) "ON" else "OFF"
        val tm = String.format(Locale.US, "%.1f", t.tempC); val ru = String.format(Locale.US, "%.1f/%.1f", t.ramUsedGb, t.ramTotalGb)
        val short = "🔋 ${t.batteryPct}% • 🌡 ${tm}°C • 🧠 ${ru}GB • ${t.networkSummary}"
        val big = "🔋 ${t.batteryPct}% (${t.chargingSource}) • 🌡 ${tm}°C\n🧠 RAM: ${ru} GB • 🌐 ${t.networkSummary} • ⚡Shizuku: $sh\nTap to view full real-time phone status"
        runCatching { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(401, buildStatusNotif(short, big, cfg.companionName)) }
    }
    private fun checkClipboard(cfg: AppConfig, now: Long) {
        if (!cfg.clipboardGuard) return
        if (lastClipTs > 0 && now - lastClipTs > 40_000L) {
            lastClipTs = 0L; Handler(Looper.getMainLooper()).post { runCatching { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("", "")) } }
        } else if (lastClipTs == 0L) {
            Handler(Looper.getMainLooper()).post { runCatching { val t = (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString().orEmpty(); if (t.isNotBlank() && (Regex("\\b\\d{4,8}\\b").containsMatchIn(t) || t.contains("sk-") || t.contains("AIza"))) lastClipTs = now } }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    private fun buildStatusNotif(text: String, big: String = text, name: String = AppConfig(this).companionName): Notification {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("open_doctor", true)
        val pi = PendingIntent.getActivity(this, 401, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val vis = if (AppConfig(this).lockScreenPrivacy) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC
        return NotificationCompat.Builder(this, HumayraApp.CH_CARE).setSmallIcon(R.drawable.ic_humayra_emblem)
            .setContentTitle("$name • Live Phone Status").setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(big))
            .setOnlyAlertOnce(true).setVisibility(vis).setContentIntent(pi).setOngoing(true).build()
    }
    companion object {
        fun runCarePass(ctx: Context) {
            val cfg = AppConfig(ctx); if (!cfg.masterEnabled) return
            val mem = MemoryStore(ctx); val now = System.currentTimeMillis(); var ch = false
            val up = mem.getReminders().map { r -> if (!r.done && r.triggerAtMs <= now) { ch = true; fireAlert(ctx, "⏰ Reminder", r.message, (r.id % 9000).toInt() + 100); r.copy(done = true) } else r }
            if (ch) mem.saveReminders(up); if (!cfg.proactiveCare || now - cfg.lastCareAlertMs < 30 * 60_000L) return
            val t = PhoneDoctorTelemetry.collect(ctx, false); val hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val scrOn = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
            cfg.lastCareAlertMs = now; pickVariedMsg(ctx, cfg, t, hr, scrOn)?.let { fireAlert(ctx, "🌙 ${cfg.companionName}", it, 3001) }
        }
        private fun pickVariedMsg(ctx: Context, cfg: AppConfig, t: TelemetrySnapshot, hr: Int, scrOn: Boolean): String? {
            if (t.tempC > 39.0f) return "ফোন ${t.tempC}°C গরম হয়েছে, একটু রাখো!"
            if (t.batteryPct < 20 && t.chargingSource == "On Battery") return "ব্যাটারি ${t.batteryPct}%, চার্জার লাগাও!"
            if (t.batteryPct >= 100 && t.chargingSource != "On Battery") return "চার্জ ১০০% ফুল, চার্জার খোলো!"
            if (scrOn && hr in 0..4) return "রাত ${hr}টা বাজে—ঘুমাও এবার!"
            if (!scrOn || hr !in 8..23) return null
            val aiMsg = runBlocking { withTimeoutOrNull(12_000) {
                if (HybridBrain.isOnline(ctx) && CloudAiClient.availableTargets(cfg).isNotEmpty())
                    HybridBrain.ask(ctx, "Write ONE short casual check-in (max 15 words, human tone, time ${hr}:00, bat ${t.batteryPct}%, temp ${t.tempC}C).").first.take(120)
                else null
            } }
            return aiMsg?.takeIf { it.isNotBlank() && !it.contains("Error", true) } ?: "ফোন ফিট (${t.batteryPct}%, ${t.tempC}°C) — পানি খেয়ে নাও 💧"
        }
        fun fireAlert(ctx: Context, title: String, body: String, id: Int) {
            val cfg = AppConfig(ctx); val vis = if (cfg.lockScreenPrivacy) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC
            val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("open_doctor", true)
            val pi = PendingIntent.getActivity(ctx, id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(ctx, HumayraApp.CH_ALERTS).setSmallIcon(R.drawable.ic_humayra_emblem)
                .setContentTitle(title).setContentText(body).setVisibility(vis).setContentIntent(pi).setAutoCancel(true).build()
            try { (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, n) } catch (_: SecurityException) {}
            if (id == 3009 && cfg.telegramHumanEnabled && cfg.telegramToken.isNotBlank() && cfg.telegramChatId.isNotBlank()) CoroutineScope(Dispatchers.IO).launch { TelegramBridge.sendMessage(cfg.telegramToken, cfg.telegramChatId, body) }
        }
    }
}
