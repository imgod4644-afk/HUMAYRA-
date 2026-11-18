package com.humayra.goddess.actions

import android.content.*
import kotlin.math.min

object FuzzyMatch {
    private val aliases = mapOf(
        "fayarfox" to "firefox", "feirefox" to "firefox", "firefx" to "firefox", "ফায়ারফক্স" to "firefox",
        "utube" to "youtube", "youtbe" to "youtube", "ইউটিউব" to "youtube",
        "watsap" to "whatsapp", "whotsapp" to "whatsapp", "হোয়াটসঅ্যাপ" to "whatsapp",
        "proton bpn" to "proton vpn", "prton vpn" to "proton vpn", "ফ্রি ফায়ার" to "free fire",
        "free fair" to "free fire", "teligram" to "telegram", "instragam" to "instagram",
        "facbook" to "facebook", "crome" to "chrome", "ক্রম" to "chrome", "সেটিংস" to "settings"
    )
    fun bestPackage(ctx: Context, raw: String): String? {
        var q = raw.trim().lowercase(); aliases.forEach { (k, v) -> if (q.contains(k)) q = q.replace(k, v) }
        val pm = ctx.packageManager; val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val labeled = apps.map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
        labeled.firstOrNull { it.first.equals(q, true) }?.let { return it.second }
        labeled.firstOrNull { it.first.lowercase().contains(q) || it.second.lowercase().contains(q) }?.let { return it.second }
        return labeled.map { (lbl, pkg) ->
            val words = lbl.lowercase().split(" ") + lbl.lowercase()
            val d = words.minOf { dist(q, it) }; Triple(lbl, pkg, d)
        }.filter { it.third <= maxOf(2, q.length / 3) }.minByOrNull { it.third }?.second
    }
    private fun dist(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]; dp[0] = i
            for (j in 1..b.length) {
                val cur = dp[j]; dp[j] = if (a[i - 1] == b[j - 1]) prev else 1 + min(prev, min(dp[j], dp[j - 1])); prev = cur
            }
        }
        return dp[b.length]
    }
}
