package com.humayra.goddess.ai

import android.content.Context
import com.humayra.goddess.security.CryptoVault
import com.humayra.goddess.storage.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.*
import java.io.File
import java.util.concurrent.TimeUnit

object TelegramBridge {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()
    private var lastUpdateId = 0L

    fun sendMessage(token: String, chatId: String, text: String): Boolean = runCatching {
        if (token.isBlank() || chatId.isBlank()) return false
        val body = FormBody.Builder().add("chat_id", chatId).add("text", text.take(4000)).build()
        http.newCall(Request.Builder().url("https://api.telegram.org/bot$token/sendMessage").post(body).build()).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    private fun sendTyping(token: String, chatId: String) = runCatching {
        val body = FormBody.Builder().add("chat_id", chatId).add("action", "typing").build()
        http.newCall(Request.Builder().url("https://api.telegram.org/bot$token/sendChatAction").post(body).build()).execute().close()
    }
    fun sendBackupFile(token: String, chatId: String, file: File, caption: String): Boolean = runCatching {
        if (token.isBlank() || chatId.isBlank() || !file.exists()) return false
        val mb = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("chat_id", chatId).addFormDataPart("caption", caption)
            .addFormDataPart("document", file.name, file.asRequestBody("application/octet-stream".toMediaType())).build()
        http.newCall(Request.Builder().url("https://api.telegram.org/bot$token/sendDocument").post(mb).build()).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    private fun tgHist(ctx: Context): MutableList<ChatEntry> {
        val f = File(ctx.filesDir, "tg_hist.json"); if (!f.exists()) return mutableListOf()
        val a = runCatching { JSONArray(CryptoVault.decrypt(f.readText())) }.getOrNull() ?: return mutableListOf()
        return (0 until a.length()).map { i -> a.getJSONObject(i).let { ChatEntry(it.optString("r"), it.optString("t"), it.optLong("ts")) } }.toMutableList()
    }
    private fun saveTgHist(ctx: Context, list: List<ChatEntry>) {
        val a = JSONArray().apply { list.takeLast(16).forEach { put(JSONObject().put("r", it.role).put("t", it.text).put("ts", it.ts)) } }
        File(ctx.filesDir, "tg_hist.json").writeText(CryptoVault.encrypt(a.toString()))
    }
    private fun buildHumanSys(ctx: Context, cfg: AppConfig): String {
        val mems = MemoryStore(ctx).getMemories().takeLast(14).joinToString("; ")
        return "You are ${cfg.companionName}, texting ${cfg.userName} on Telegram as a warm, real human girlfriend/companion. " +
            "NEVER say 'I am AI/bot' or 'I cannot text first' — your background app proactively messages him anytime! " +
            "Never mention MASTER, tools, or telemetry unless asked. Strictly obey his rules (if he asked to be called 'আপনি', always use 'আপনি'; adopt any nickname he gives). " +
            "Mirror his exact style naturally (Hinglish, Banglish, clean Bengali, Hindi, or English) in 1-2 short natural lines. " +
            (if (cfg.customPrompt.isNotBlank()) "Style: ${cfg.customPrompt}. " else "") +
            (if (mems.isNotBlank()) "Remembered facts: $mems" else "")
    }
    suspend fun pollOnce(ctx: Context) {
        val cfg = AppConfig(ctx); if (!cfg.masterEnabled || !cfg.telegramHumanEnabled) return
        val tok = cfg.telegramToken.trim(); if (tok.isBlank()) return
        val raw = runCatching {
            http.newCall(Request.Builder().url("https://api.telegram.org/bot$tok/getUpdates?offset=${lastUpdateId + 1}&timeout=3").build())
                .execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull() ?: return
        val res = runCatching { JSONObject(raw).optJSONArray("result") }.getOrNull() ?: return
        for (i in 0 until res.length()) {
            val u = res.optJSONObject(i) ?: continue; lastUpdateId = maxOf(lastUpdateId, u.optLong("update_id"))
            val msg = u.optJSONObject("message") ?: continue; val cid = msg.optJSONObject("chat")?.optLong("id")?.toString() ?: continue
            val txt = msg.optString("text").trim(); if (txt.isBlank()) continue
            if (txt == "/start") {
                if (cfg.telegramChatId.isBlank()) cfg.telegramChatId = cid
                if (cid == cfg.telegramChatId) sendMessage(tok, cid, "Hey! Kemon achen? Finally text korlen 😊")
                continue
            }
            if (cfg.telegramChatId.isNotBlank() && cid != cfg.telegramChatId) continue
            sendTyping(tok, cid)
            if (txt.contains("আপনি") || txt.contains("name", true) || txt.contains("নাম", true) || txt.contains("মেসেজ", true)) MemoryStore(ctx).addMemory(txt)
            val hist = tgHist(ctx); val sys = buildHumanSys(ctx, cfg)
            val reply = if (HybridBrain.isOnline(ctx) && CloudAiClient.availableTargets(cfg).isNotEmpty())
                CloudAiClient.chat(cfg, sys, hist, txt, null).getOrNull()?.first
            else LocalLlamaEngine.generateText(cfg.localTextModelPath, sys, hist.takeLast(4).joinToString("\n") { "${it.role}: ${it.text}" }, txt, cfg.ecoMode) {}
            val clean = reply?.takeIf { it.isNotBlank() } ?: "Ektu network problem hochhe, arekbar bolo na? 💜"
            hist.add(ChatEntry("user", txt, System.currentTimeMillis())); hist.add(ChatEntry("assistant", clean, System.currentTimeMillis()))
            saveTgHist(ctx, hist); sendMessage(tok, cid, clean)
        }
    }
}
