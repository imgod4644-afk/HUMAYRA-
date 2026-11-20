package com.humayra.goddess.ai

import android.content.Context
import android.provider.Settings
import com.humayra.goddess.service.HumayraAccessibilityService
import com.humayra.goddess.shizuku.ShizukuBridge
import com.humayra.goddess.storage.*
import com.humayra.goddess.telemetry.PhoneDoctorTelemetry
import java.util.concurrent.ConcurrentHashMap

object SelfGuardian {
    private val cooldownUntil = ConcurrentHashMap<String, Long>()
    @Volatile var lastHealNote: String = ""

    fun isCooling(prov: String) = (cooldownUntil[prov.uppercase()] ?: 0L) > System.currentTimeMillis()
    fun markFail(prov: String) { cooldownUntil[prov.uppercase()] = System.currentTimeMillis() + 90_000L }
    fun markOk(prov: String) { cooldownUntil.remove(prov.uppercase()) }

    fun briefStatus(ctx: Context): String {
        val acc = HumayraAccessibilityService.instance != null
        val ov = Settings.canDrawOverlays(ctx)
        val sh = ShizukuBridge.hasPermission()
        return "Accessibility:${if (acc) "ON" else "OFF"}, Overlay:${if (ov) "ON" else "OFF"}, Shizuku:${if (sh) "READY" else "OFF"}"
    }

    fun autoHeal(ctx: Context) {
        val cfg = AppConfig(ctx); val t = PhoneDoctorTelemetry.collect(ctx, false)
        val freeRam = t.ramTotalGb - t.ramUsedGb
        if ((freeRam < 1.0f || t.tempC > 39.5f) && !cfg.ecoMode) {
            cfg.ecoMode = true; LocalLlamaEngine.releaseAll()
            lastHealNote = "Auto-Heal: Eco Mode ON & GGUF freed (RAM=${"%.1f".format(freeRam)}GB, ${t.tempC}°C)"
            MemoryStore.pushEvent(lastHealNote)
        }
    }

    fun learnFromUser(ctx: Context, msg: String) {
        val m = msg.trim(); val mem = MemoryStore(ctx)
        val cues = listOf("my name is ", "call me ", "আমার নাম ", "আমাকে ", "i live in ", "আমি থাকি ", "i like ", "আমার পছন্দ ", "my wifi ")
        val low = m.lowercase()
        if (cues.any { low.contains(it) } && m.length in 6..140) mem.addMemory(m)
    }
}
