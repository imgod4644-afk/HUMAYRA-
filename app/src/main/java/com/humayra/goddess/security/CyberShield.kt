package com.humayra.goddess.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import java.io.File

object CyberShield {
    private val sensitiveKeys = listOf(
        "bkash", "nagad", "rocket", "upay", "cellfin", "nexuspay", "citytouch", "brac", "ebl", "ibbl", "dutchbangla",
        "keepass", "bitwarden", "1password", "authy", "authenticator", "permissioncontroller", "packageinstaller", "binance", "trust"
    )
    private val screenCues = listOf("incognito", "private browsing", "secret mode", "enter pin", "enter password", "cvv", "one-time password")

    fun isSensitiveScreen(pkg: String, screenText: String, customBlacklist: Set<String>): Boolean {
        val p = pkg.lowercase(); val s = screenText.lowercase()
        if (customBlacklist.any { it.equals(pkg, true) }) return true
        if (sensitiveKeys.any { p.contains(it) }) return true
        return screenCues.any { s.contains(it) }
    }

    fun redactPii(input: String): String = input
        .replace(Regex("(sk-or-v1-|sk-[a-zA-Z0-9]{10,}|AIza[0-9A-Za-z_-]{20,}|gsk_[0-9A-Za-z]{20,})"), "[KEY_REDACTED]")
        .replace(Regex("\\b(?:\\d[ -]*?){13,16}\\b"), "[CARD_REDACTED]")
        .replace(Regex("\\b(?:\\+?88)?01[3-9]\\d{8}\\b"), "[PHONE_REDACTED]")
        .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[EMAIL_REDACTED]")
        .replace(Regex("(?i)(otp|pin|code|cvv|পিন|ওটিপি)[:\\s-]*\\d{4,8}"), "$1:[REDACTED]")
    fun isSafeShizukuCmd(cmd: String): Boolean {
        val low = cmd.trim().lowercase()
        val blocked = listOf("rm ", "rm\t", "rmdir", "mkfs", "dd ", "pm uninstall", "curl ", "wget ", "nc ", "reboot", "shutdown", "fastboot", "chmod 777", "> /")
        if (blocked.any { low.contains(it) }) return false
        val allowed = listOf("svc ", "cmd ", "settings ", "am force-stop", "pm grant ", "pm disable-user", "pm enable", "pm trim-caches", "dumpsys ", "cat /sys/class/thermal", "top ", "input ", "screencap")
        return allowed.any { low.startsWith(it) }
    }

    fun checkPhishingUrl(url: String): String? {
        val u = url.trim().lowercase(); if (!u.startsWith("http")) return null
        if (u.startsWith("http://") && (u.contains("login") || u.contains("bank") || u.contains("bkash") || u.contains("verify"))) return "⚠️ অনিরাপদ HTTP লগইন/ব্যাংক লিংক!"
        if (Regex("https?://\\d+\\.\\d+\\.\\d+\\.\\d+").containsMatchIn(u)) return "⚠️ সরাসরি IP অ্যাড্রেস লিংক (সন্দেহজনক)!"
        val fakes = listOf("bkaash", "nagad-", "paypa1", "faceb00k", "g00gle", "micr0soft", "free-diamond")
        return if (fakes.any { u.contains(it) }) "🚨 সম্ভাব্য ফিশিং/ভুয়া লিংক!" else null
    }
    @Suppress("DEPRECATION")
    fun auditInstalledApps(ctx: Context): String {
        val pm = ctx.packageManager
        val pkgs = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        val risky = mutableListOf<String>()
        val watch = setOf(Manifest.permission.READ_SMS, Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_CONTACTS)
        for (pi in pkgs) {
            val ai = pi.applicationInfo ?: continue
            if ((ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) continue
            val perms = pi.requestedPermissions?.filter { it in watch } ?: emptyList()
            if (perms.size >= 3) {
                val lbl = pm.getApplicationLabel(ai).toString()
                risky.add("• $lbl: ${perms.joinToString(", ") { it.substringAfterLast('.') }}")
            }
        }
        return if (risky.isEmpty()) "✅ কোনো সন্দেহজনক অতিরিক্ত পারমিশনওয়ালা থার্ড-পার্টি অ্যাপ পাওয়া যায়নি।"
        else "⚠️ ${risky.size}টি অ্যাপ বেশি সংবেদনশীল পারমিশন ব্যবহার করছে:\n${risky.take(15).joinToString("\n")}"
    }

    @Suppress("DEPRECATION")
    fun inspectApk(ctx: Context, file: File): String {
        val pm = ctx.packageManager
        val pi = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_PERMISSIONS) ?: return "❌ APK পড়া যায়নি।"
        val perms = pi.requestedPermissions?.map { it.substringAfterLast('.') } ?: emptyList()
        val red = perms.filter { it in listOf("READ_SMS", "RECEIVE_SMS", "BIND_ACCESSIBILITY_SERVICE", "SYSTEM_ALERT_WINDOW", "RECORD_AUDIO", "CAMERA", "CALL_PHONE", "READ_CONTACTS") }
        return "📦 Package: ${pi.packageName}\n🔑 Total Perms: ${perms.size}\n🚨 Sensitive: ${if (red.isEmpty()) "None" else red.joinToString(", ")}"
    }
}
