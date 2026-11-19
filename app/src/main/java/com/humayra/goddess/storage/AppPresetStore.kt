package com.humayra.goddess.storage

import android.content.Context
import com.humayra.goddess.security.CryptoVault
import org.json.*
import java.io.File

data class AppPreset(
    val pkg: String, val label: String, val enabled: Boolean = true,
    val blindPrivacy: Boolean = false, val hideNotifs: Boolean = false,
    val ecoMode: Boolean = false, val autoOverlay: Boolean = false,
    val forceStopOnExit: Boolean = false, val autoVpn: Boolean = false,
    val volumePct: Int = -1, val brightnessPct: Int = -1, val timeoutSec: Int = -1,
    val dndMode: Int = -1, val darkMode: Int = -1, val autoRotate: Int = -1,
    val wifiState: Int = -1, val btState: Int = -1, val dataState: Int = -1,
    val batterySaver: Int = -1, val customEnterCmd: String = "", val customExitCmd: String = ""
)
class AppPresetStore(ctx: Context) {
    private val file = File(ctx.filesDir, "humayra_presets.json")

    @Synchronized fun getAll(): List<AppPreset> {
        if (!file.exists()) return defaults().also { saveAll(it) }
        val raw = CryptoVault.decrypt(file.readText()).ifBlank { return defaults() }
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return defaults()
        return (0 until arr.length()).map { i -> val o = arr.getJSONObject(i)
            AppPreset(o.optString("pkg"), o.optString("lbl"), o.optBoolean("en", true), o.optBoolean("blind"), o.optBoolean("hide"),
                o.optBoolean("eco"), o.optBoolean("ov"), o.optBoolean("stop"), o.optBoolean("vpn"),
                o.optInt("vol", -1), o.optInt("bri", -1), o.optInt("to", -1), o.optInt("dnd", -1), o.optInt("dark", -1),
                o.optInt("rot", -1), o.optInt("wifi", -1), o.optInt("bt", -1), o.optInt("data", -1),
                o.optInt("bat", -1), o.optString("ecmd"), o.optString("xcmd"))
        }
    }
    @Synchronized fun saveAll(items: List<AppPreset>) {
        val arr = JSONArray().apply { items.forEach { p ->
            put(JSONObject().put("pkg", p.pkg).put("lbl", p.label).put("en", p.enabled).put("blind", p.blindPrivacy).put("hide", p.hideNotifs)
                .put("eco", p.ecoMode).put("ov", p.autoOverlay).put("stop", p.forceStopOnExit).put("vpn", p.autoVpn)
                .put("vol", p.volumePct).put("bri", p.brightnessPct).put("to", p.timeoutSec).put("dnd", p.dndMode)
                .put("dark", p.darkMode).put("rot", p.autoRotate).put("wifi", p.wifiState).put("bt", p.btState)
                .put("data", p.dataState).put("bat", p.batterySaver).put("ecmd", p.customEnterCmd).put("xcmd", p.customExitCmd))
        } }
        file.writeText(CryptoVault.encrypt(arr.toString()))
    }
    fun upsert(p: AppPreset) = saveAll(getAll().filterNot { it.pkg.equals(p.pkg, true) } + p)
    fun delete(pkg: String) = saveAll(getAll().filterNot { it.pkg.equals(pkg, true) })
    fun find(pkg: String): AppPreset? = getAll().firstOrNull { it.pkg.equals(pkg, true) }
    private fun defaults() = listOf(
        AppPreset("com.dts.freefireth", "Free Fire", hideNotifs = true, ecoMode = true, autoOverlay = true, volumePct = 80),
        AppPreset("com.dts.freefiremax", "Free Fire MAX", hideNotifs = true, ecoMode = true, autoOverlay = true, volumePct = 80),
        AppPreset("com.google.android.youtube", "YouTube", volumePct = 70),
        AppPreset("com.bKash.customerapp", "bKash", blindPrivacy = true)
    )
}
