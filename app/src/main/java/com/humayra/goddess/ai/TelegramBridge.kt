package com.humayra.goddess.ai

import android.content.Context
import com.humayra.goddess.actions.*
import com.humayra.goddess.storage.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
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

    fun sendBackupFile(token: String, chatId: String, file: File, caption: String): Boolean = runCatching {
        if (token.isBlank() || chatId.isBlank() || !file.exists()) return false
        val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId).addFormDataPart("caption", caption)
            .addFormDataPart("document", file.name, file.asRequestBody("application/octet-stream".toMediaType())).build()
        http.newCall(Request.Builder().url("https://api.telegram.org/bot$token/sendDocument").post(mb).build()).execute().use { it.isSuccessful }
    }.getOrDefault(false)
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
            if (txt == "/start" && cfg.telegramChatId.isBlank()) {
                cfg.telegramChatId = cid; sendMessage(tok, cid, "হ্যালো ${cfg.userName}! আমি ${cfg.companionName} — এখন থেকে শুধু তোমার সাথেই এই প্রাইভেট চ্যাটে কথা বলব 💜"); continue
            }
            if (cfg.telegramChatId.isNotBlank() && cid != cfg.telegramChatId) continue
            val reply = HybridBrain.ask(ctx, "[Telegram Private Chat - match exact user language (Banglish/Hinglish/Bengali/Hindi/English) like a warm playful human girlfriend, zero AI robot tone, short natural text]: $txt").first
            val parsed = ActionParser.parse(reply)
            sendMessage(tok, cid, parsed.spokenText.ifBlank { "হুম বলো!" })
        }
    }
}
