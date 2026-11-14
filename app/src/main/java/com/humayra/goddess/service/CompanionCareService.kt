package com.humayra.goddess.service

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import com.humayra.goddess.*
import com.humayra.goddess.storage.*
import com.humayra.goddess.telemetry.PhoneDoctorTelemetry
import com.humayra.goddess.ui.MainActivity
import kotlinx.coroutines.*
import java.util.Calendar

class CompanionCareService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val n = buildStatusNotif("24/7 Care & Phone Doctor Active")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) startForeground(401, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(401, n)
        scope.launch {
            while (isActive) {
                runCarePass(this@CompanionCareService)
                val t = PhoneDoctorTelemetry.collect(this@CompanionCareService)
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                try { nm.notify(401, buildStatusNotif("🔋 ${t.batteryPct}% • 🌡 ${t.tempC}°C • RAM ${"%.1f".format(t.ramUsedGb)}/${"%.1f".format(t.ramTotalGb)}GB")) } catch (_: SecurityException) {}
                delay(15 * 60_000L)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun buildStatusNotif(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, HumayraApp.CH_CARE).setSmallIcon(R.drawable.ic_humayra_emblem)
            .setContentTitle("${AppConfig(this).companionName} Guard").setContentText(text).setContentIntent(pi).setOngoing(true).build()
    }
    companion object {
        fun runCarePass(ctx: Context) {
            val cfg = AppConfig(ctx); val mem = MemoryStore(ctx); val now = System.currentTimeMillis()
            val rems = mem.getReminders(); var changed = false
            val updated = rems.map { r ->
                if (!r.done && r.triggerAtMs <= now) { changed = true; fireAlert(ctx, "⏰ Reminder", r.message, (r.id % 9000).toInt() + 100); r.copy(done = true) } else r
            }
            if (changed) mem.saveReminders(updated)
            if (!cfg.proactiveCare || now - cfg.lastCareAlertMs < 55 * 60_000L) return
            val t = PhoneDoctorTelemetry.collect(ctx); val hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val screenOn = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
            val msg = when {
                t.tempC > 39.0f -> "ফোন গরম হচ্ছে (${t.tempC}°C)! একটু বিশ্রাম দাও বস।"
                t.batteryPct < 20 && t.chargingSource == "On Battery" -> "ব্যাটারি ${t.batteryPct}%—চার্জার লাগাও বস!"
                t.batteryPct >= 100 && t.chargingSource != "On Battery" -> "ব্যাটারি ১০০% ফুল, চার্জার খুলে রাখো।"
                t.ramTotalGb - t.ramUsedGb < 0.8f -> "RAM অনেক টাইট, ব্যাকগ্রাউন্ড অ্যাপ কমাতে পারো।"
                screenOn && hr in 0..4 -> "অনেক রাত হয়ে গেছে (${hr}টা)—চোখকে বিশ্রাম দিয়ে ঘুমাও!"
                screenOn && hr in 9..22 -> "পানি খেতে ভুলো না বস, একটু স্ট্রেচ করে নাও 💧"
                else -> null
            }
            if (msg != null) { cfg.lastCareAlertMs = now; fireAlert(ctx, "💜 ${cfg.companionName} Care", msg, 3001) }
        }

        private fun fireAlert(ctx: Context, title: String, body: String, id: Int) {
            val n = NotificationCompat.Builder(ctx, HumayraApp.CH_ALERTS).setSmallIcon(R.drawable.ic_humayra_emblem)
                .setContentTitle(title).setContentText(body).setAutoCancel(true).build()
            try { (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, n) } catch (_: SecurityException) {}
        }
    }
}
