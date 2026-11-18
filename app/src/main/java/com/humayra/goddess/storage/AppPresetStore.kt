package com.humayra.goddess.storage

import android.content.Context
import com.humayra.goddess.security.CryptoVault
import org.json.*
import java.io.File

data class AppPreset(
    val pkg: String, val label: String, val blindPrivacy: Boolean = false,
    val hideNotifs: Boolean = false, val ecoMode: Boolean = false, val autoOverlay: Boolean = false,
    val forceStopOnExit: Boolean = false, val autoVpn: Boolean = false, val volumePct: Int = -1
)

class AppPresetStore(ctx: Context) {
    private val file = File(ctx.filesDir, "humayra_presets.json")

    @Synchronized fun getAll(): List<AppPreset> {
        if (!file.exists()) return defaults().also { saveAll(it) }
        val raw = CryptoVault.decrypt(file.readText()).ifBlank { return defaults() }
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return defaults()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            AppPreset(o.optString("pkg"), o.optString("lbl"), o.optBoolean("blind"), o.optBoolean("hide"),
                o.optBoolean("eco"), o.optBoolean("ov"), o.optBoolean("stop"), o.optBoolean("vpn"), o.optInt("vol", -1))
        }
    }
    @Synchronized fun saveAll(items: List<AppPreset>) {
        val arr = JSONArray().apply {
            items.forEach { p ->
                put(JSONObject().put("pkg", p.pkg).put("lbl", p.label).put("blind", p.blindPrivacy)
                    .put("hide", p.hideNotifs).put("eco", p.ecoMode).put("ov", p.autoOverlay)
                    .put("stop", p.forceStopOnExit).put("vpn", p.autoVpn).put("vol", p.volumePct))
            }
        }
        file.writeText(CryptoVault.encrypt(arr.toString()))
    }
    fun upsert(p: AppPreset) = saveAll(getAll().filterNot { it.pkg == p.pkg } + p)
    fun find(pkg: String): AppPreset? = getAll().firstOrNull { it.pkg.equals(pkg, true) }
    private fun defaults() = listOf(
        AppPreset("com.dts.freefireth", "Free Fire", hideNotifs = true, ecoMode = true, autoOverlay = true, volumePct = 80),
        AppPreset("com.dts.freefiremax", "Free Fire MAX", hideNotifs = true, ecoMode = true, autoOverlay = true, volumePct = 80),
        AppPreset("com.google.android.youtube", "YouTube", volumePct = 70),
        AppPreset("com.bKash.customerapp", "bKash", blindPrivacy = true)
    )
}
