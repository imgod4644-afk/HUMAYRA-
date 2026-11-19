package com.humayra.goddess.telemetry

import android.app.*
import android.app.usage.UsageStatsManager
import android.bluetooth.BluetoothManager
import android.content.*
import android.net.*
import android.net.wifi.WifiManager
import android.os.*
import java.util.*

data class TelemetrySnapshot(
    val batteryPct: Int, val tempC: Float, val chargingSource: String,
    val voltageMv: Int, val health: String, val ramUsedGb: Float, val ramTotalGb: Float,
    val storageFreeGb: Float, val storageTotalGb: Float, val networkSummary: String,
    val vpnActive: Boolean, val bluetoothOn: Boolean, val screenTimeMin: Int, val topApps: List<String>
) {
    fun toPromptSummary(): String = buildString {
        append("Battery: $batteryPct% ($chargingSource, ${String.format(Locale.US, "%.1f", tempC)}C, ${voltageMv}mV, $health) | ")
        append("RAM: ${String.format(Locale.US, "%.1f/%.1f", ramUsedGb, ramTotalGb)}GB | ")
        append("Storage Free: ${String.format(Locale.US, "%.1f/%.1f", storageFreeGb, storageTotalGb)}GB | ")
        append("Net: $networkSummary | VPN: $vpnActive | BT: $bluetoothOn | ")
        append("Today Screen: ${screenTimeMin}m; Top: ${if (topApps.isEmpty()) "No Usage Access" else topApps.joinToString(", ")}")
    }
}
object PhoneDoctorTelemetry {
    fun collect(ctx: Context, includeUsage: Boolean = true): TelemetrySnapshot {
        val bInt = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lvl = bInt?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scl = bInt?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (lvl >= 0 && scl > 0) (lvl * 100) / scl else 0
        val tempC = (bInt?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val volt = bInt?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val src = when (bInt?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"; BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"; else -> "On Battery"
        }
        val health = when (bInt?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"; BatteryManager.BATTERY_HEALTH_GOOD -> "Good"; else -> "Normal"
        }
        val mi = ActivityManager.MemoryInfo().also { (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
        val sf = StatFs(Environment.getDataDirectory().path)
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val net = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                @Suppress("DEPRECATION")
                val spd = (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.connectionInfo?.linkSpeed ?: 0
                "Wi-Fi (${spd}Mbps)"
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
            else -> "Connected"
        }
        val bt = try { (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true } catch (_: Exception) { false }
        val (scrMin, tops) = if (includeUsage) readUsage(ctx) else (0 to emptyList())
        return TelemetrySnapshot(pct, tempC, src, volt, health, (mi.totalMem - mi.availMem) / 1_073_741_824f, mi.totalMem / 1_073_741_824f, sf.availableBytes / 1_073_741_824f, sf.totalBytes / 1_073_741_824f, net, vpn, bt, scrMin, tops)
    }

    private fun readUsage(ctx: Context): Pair<Int, List<String>> {
        val ao = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val m = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ao.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        if (m != AppOpsManager.MODE_ALLOWED) return 0 to emptyList()
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
        val stats = (ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager).queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis()) ?: return 0 to emptyList()
        val pm = ctx.packageManager
        val merged = stats.filter { it.totalTimeInForeground > 60_000L }.groupBy { it.packageName }.mapValues { it.value.sumOf { u -> u.totalTimeInForeground } }.entries.sortedByDescending { it.value }
        val total = (merged.sumOf { it.value } / 60_000L).toInt()
        val top = merged.take(4).map { (p, ms) -> val lbl = try { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() } catch (_: Exception) { p.substringAfterLast('.') }; "$lbl(${ms / 60_000L}m)" }
        return total to top
    }
}
