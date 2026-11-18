package com.humayra.goddess.ai

import android.content.Context
import android.net.*
import com.humayra.goddess.security.CyberShield
import com.humayra.goddess.service.HumayraAccessibilityService
import com.humayra.goddess.storage.*
import com.humayra.goddess.telemetry.PhoneDoctorTelemetry

object HybridBrain {
    fun isOnline(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
    suspend fun ask(ctx: Context, userMessage: String, imageBytes: ByteArray? = null, onDelta: (String) -> Unit = {}): Pair<String, String> {
        val cfg = AppConfig(ctx); if (!cfg.masterEnabled) return "Master Switch OFF আছে — ওপরে ⏻ চাপলে চালু হব।" to "Standby"
        SelfGuardian.autoHeal(ctx); SelfGuardian.learnFromUser(ctx, userMessage)
        val mem = MemoryStore(ctx); val tele = PhoneDoctorTelemetry.collect(ctx).toPromptSummary(); val diag = SelfGuardian.briefStatus(ctx)
        val rawScr = HumayraAccessibilityService.getActiveScreenSummary(); val hist = mem.getHistory()
        val tryCloud = cfg.brainMode == "ONLINE" || (cfg.brainMode == "HYBRID" && isOnline(ctx) && CloudAiClient.availableTargets(cfg).isNotEmpty())
        if (tryCloud) {
            val cScr = if (cfg.strictCloudPrivacy) "Hidden by Cloud Privacy Shield" else if (cfg.piiRedaction) CyberShield.redactPii(rawScr) else rawScr
            val cEv = if (cfg.strictCloudPrivacy) emptyList() else MemoryStore.recentEvents.map { CyberShield.redactPii(it) }
            val cMsg = if (cfg.piiRedaction) CyberShield.redactPii(userMessage) else userMessage
            val cMem = if (cfg.strictCloudPrivacy) emptyList() else mem.getMemories()
            val sys = HumayraPromptBuilder.buildSystemPrompt(cfg, cMem, tele, cScr, diag, cEv)
            val res = CloudAiClient.chat(cfg, sys, if (cfg.decoyActive) emptyList() else hist, cMsg, imageBytes)
            if (res.isSuccess) return res.getOrThrow().let { it.first to "Online (${it.second})" }
            if (cfg.brainMode == "ONLINE") return "Cloud Error: ${res.exceptionOrNull()?.message}" to "Online Error"
        }
        val lSys = HumayraPromptBuilder.buildSystemPrompt(cfg, mem.getMemories(), tele, rawScr, diag, MemoryStore.recentEvents.toList(), true)
        if (imageBytes != null) return LocalLlamaEngine.analyzeImage(cfg.localVisionModelPath, cfg.localVisionMmprojPath, imageBytes, userMessage, cfg.ecoMode, onDelta) to "Offline Vision"
        val ctxBlock = hist.takeLast(4).joinToString("\n") { "${it.role}: ${it.text}" }
        return LocalLlamaEngine.generateText(cfg.localTextModelPath, lSys, ctxBlock, userMessage, cfg.ecoMode, onDelta) to "Offline Local"
    }
    suspend fun draftWhatsAppReply(ctx: Context, sender: String, msg: String, isGroup: Boolean): String {
        val cfg = AppConfig(ctx); if (!cfg.masterEnabled) return ""
        val clean = if (cfg.piiRedaction) CyberShield.redactPii(msg) else msg
        return ask(ctx, "WhatsApp ${if (isGroup) "group" else "personal"} message from '$sender': \"$clean\". Write ONLY a short natural reply.").first.replace(Regex("\\[\\[ACTIONS:[\\s\\S]*?]]"), "").trim().take(280)
    }
    fun stopAll() = LocalLlamaEngine.cancel()
}
