package com.humayra.goddess.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.humayra.goddess.service.HumayraAccessibilityService
import com.humayra.goddess.storage.AppConfig
import com.humayra.goddess.storage.MemoryStore
import com.humayra.goddess.telemetry.PhoneDoctorTelemetry

object HybridBrain {
    fun isOnline(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun ask(
        ctx: Context, userMessage: String, imageBytes: ByteArray? = null, onDelta: (String) -> Unit = {}
    ): Pair<String, String> {
        val cfg = AppConfig(ctx)
        val mem = MemoryStore(ctx)
        val tele = PhoneDoctorTelemetry.collect(ctx).toPromptSummary()
        val scr = HumayraAccessibilityService.getActiveScreenSummary()
        val hist = mem.getHistory()
        val sys = HumayraPromptBuilder.buildSystemPrompt(cfg, mem.getMemories(), tele, scr, MemoryStore.recentEvents.toList())
        val tryCloud = (cfg.brainMode == "ONLINE" || (cfg.brainMode == "HYBRID" && isOnline(ctx) && cfg.cloudApiKey.isNotBlank()))
        if (tryCloud) {
            val res = CloudAiClient.chat(cfg, sys, hist, userMessage, imageBytes)
            if (res.isSuccess) return res.getOrThrow() to "Online (${cfg.cloudProvider})"
            if (cfg.brainMode == "ONLINE") return "Cloud Error: ${res.exceptionOrNull()?.message}" to "Online Error"
        }
        val localSys = HumayraPromptBuilder.buildSystemPrompt(cfg, mem.getMemories(), tele, scr, MemoryStore.recentEvents.toList(), true)
        if (imageBytes != null) {
            val out = LocalLlamaEngine.analyzeImage(cfg.localVisionModelPath, cfg.localVisionMmprojPath, imageBytes, userMessage, cfg.ecoMode, onDelta)
            return out to "Offline Vision"
        }
        val ctxBlock = hist.takeLast(4).joinToString("\n") { "${it.role}: ${it.text}" }
        val out = LocalLlamaEngine.generateText(cfg.localTextModelPath, localSys, ctxBlock, userMessage, cfg.ecoMode, onDelta)
        return out to "Offline Local"
    }

    suspend fun draftWhatsAppReply(ctx: Context, sender: String, msg: String, isGroup: Boolean): String {
        val cfg = AppConfig(ctx)
        val kind = if (isGroup) "group" else "personal"
        val p = "WhatsApp $kind message from '$sender': \"$msg\". Write ONLY a short natural reply in ${cfg.userName}'s tone (matching language). Do not add quotes or action tags."
        val (reply, _) = ask(ctx, p)
        return reply.replace(Regex("\\[\\[ACTIONS:[\\s\\S]*?]]"), "").trim().take(280)
    }

    fun stopAll() = LocalLlamaEngine.cancel()
}
