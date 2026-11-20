package com.humayra.goddess.shizuku

import android.content.*
import android.content.pm.PackageManager
import android.os.IBinder
import com.humayra.goddess.security.CyberShield
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku

object ShizukuBridge {
    @Volatile private var shell: IShellService? = null
    @Volatile private val bindLock = kotlinx.coroutines.sync.Mutex(); private var pendingWait: CompletableDeferred<IShellService?>? = null
    private var boundArgs: Shizuku.UserServiceArgs? = null
    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            shell = IShellService.Stub.asInterface(binder); pendingWait?.complete(shell); pendingWait = null
        }
        override fun onServiceDisconnected(name: ComponentName?) { shell = null }
    }

    fun isInstalled(ctx: Context) = try { ctx.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0); true } catch (_: Exception) { false }
    fun isRunning() = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
    fun hasPermission() = isRunning() && try { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (_: Throwable) { false }
    fun requestPermission() { if (isRunning() && !hasPermission()) try { Shizuku.requestPermission(9001) } catch (_: Throwable) {} }
    suspend fun runShell(ctx: Context, cmd: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(hasPermission()) { "Shizuku কানেক্ট বা অনুমোদিত নয়।" }
            require(CyberShield.isSafeShizukuCmd(cmd)) { "CyberShield Sandbox: অনিরাপদ কমান্ড ব্লক করা হয়েছে।" }
            val s = shell ?: bindAndWait(ctx) ?: bindAndWait(ctx) ?: error("Shizuku Shell বাইন্ড হয়নি।")
            s.exec(cmd)
        }
    }

    private suspend fun bindAndWait(ctx: Context): IShellService? {
        shell?.let { return it }
        val dbg = (ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val d = CompletableDeferred<IShellService?>().also { pendingWait = it }
        val args = boundArgs ?: Shizuku.UserServiceArgs(ComponentName(ctx.packageName, ShizukuShellService::class.java.name))
            .daemon(true).processNameSuffix("humayra_sh").debuggable(dbg).version(4).also { boundArgs = it }
        withContext(Dispatchers.Main) { try { Shizuku.bindUserService(args, conn) } catch (_: Throwable) { if (!d.isCompleted) d.complete(null) } }
        return withTimeoutOrNull(6000) { d.await() }.also { pendingWait = null }
    }
    suspend fun setWifi(ctx: Context, on: Boolean) = runShell(ctx, "svc wifi ${if (on) "enable" else "disable"}")
    suspend fun connectSavedWifi(ctx: Context, ssid: String, pass: String = ""): Result<String> {
        val s = ssid.replace("\"", "\\\"")
        val c = if (pass.isBlank()) "svc wifi enable && cmd wifi connect-network \"$s\" open" else "svc wifi enable && cmd wifi connect-network \"$s\" wpa2 \"${pass.replace("\"", "\\\"")}\""
        return runShell(ctx, c)
    }
    suspend fun rotateWifiMac(ctx: Context, ssid: String = "", pass: String = ""): Result<String> {
        runShell(ctx, "settings put global non_persistent_mac_randomization_force_enabled 1"); runShell(ctx, "settings put global wifi_connected_mac_randomization_enabled 1")
        runShell(ctx, "cmd wifi set-mac-randomization enable")
        return if (ssid.isNotBlank()) connectSavedWifi(ctx, ssid, pass) else runShell(ctx, "svc wifi disable && sleep 2 && svc wifi enable")
    }
    suspend fun setBluetooth(ctx: Context, on: Boolean) = runShell(ctx, "svc bluetooth ${if (on) "enable" else "disable"}")
    suspend fun setData(ctx: Context, on: Boolean) = runShell(ctx, "svc data ${if (on) "enable" else "disable"}")
    suspend fun setAirplane(ctx: Context, on: Boolean) = runShell(ctx, "cmd connectivity airplane-mode ${if (on) "enable" else "disable"}")
    suspend fun setDarkMode(ctx: Context, on: Boolean) = runShell(ctx, "cmd uimode night ${if (on) "yes" else "no"}")
    suspend fun setDnd(ctx: Context, on: Boolean) = runShell(ctx, "cmd notification set_dnd ${if (on) "on" else "off"}")
    suspend fun setAutoRotate(ctx: Context, on: Boolean) = runShell(ctx, "settings put system accelerometer_rotation ${if (on) 1 else 0}")
    suspend fun setScreenTimeoutSec(ctx: Context, sec: Int) = runShell(ctx, "settings put system screen_off_timeout ${sec.coerceIn(15, 1800) * 1000}")
    suspend fun setBatterySaver(ctx: Context, on: Boolean) = runShell(ctx, "settings put global low_power ${if (on) 1 else 0}")
    suspend fun setBrightness(ctx: Context, pct: Int) = runShell(ctx, "settings put system screen_brightness_mode 0 && settings put system screen_brightness ${((pct.coerceIn(1, 100) / 100f) * 255).toInt()}")
    suspend fun forceStop(ctx: Context, pkg: String) = runShell(ctx, "am force-stop $pkg")
    suspend fun setAppEnabled(ctx: Context, pkg: String, enable: Boolean) = runShell(ctx, "pm ${if (enable) "enable" else "disable-user --user 0"} $pkg")
    suspend fun injectTap(ctx: Context, x: Int, y: Int) = runShell(ctx, "input tap $x $y")
    suspend fun injectText(ctx: Context, txt: String) = runShell(ctx, "input text \"${txt.replace("\"", "\\\"").replace(" ", "%s")}\"")
    suspend fun getThermalInfo(ctx: Context) = runShell(ctx, "cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null; dumpsys meminfo | head -n 6")
    suspend fun grantCorePerms(ctx: Context) {
        listOf("RECORD_AUDIO", "POST_NOTIFICATIONS", "READ_CONTACTS", "CALL_PHONE", "CAMERA").forEach { p ->
            runShell(ctx, "pm grant ${ctx.packageName} android.permission.$p")
        }
    }
}
