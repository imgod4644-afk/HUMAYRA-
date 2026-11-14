package com.humayra.goddess.ai

import android.util.Base64
import com.humayra.goddess.storage.AppConfig
import com.humayra.goddess.storage.ChatEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object CloudAiClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build()

    suspend fun chat(cfg: AppConfig, sys: String, history: List<ChatEntry>, userText: String, imgBytes: ByteArray? = null): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val key = cfg.cloudApiKey.trim()
                require(key.isNotEmpty()) { "Cloud API Key সেট করা নেই।" }
                if (cfg.cloudProvider.equals("GEMINI", true) || cfg.cloudBaseUrl.contains("googleapis.com", true)) {
                    callGemini(cfg, key, sys, history, userText, imgBytes)
                } else {
                    callOpenAiCompat(cfg, key, sys, history, userText, imgBytes)
                }
            }
        }
    private fun callOpenAiCompat(cfg: AppConfig, key: String, sys: String, hist: List<ChatEntry>, userText: String, img: ByteArray?): String {
        val url = cfg.cloudBaseUrl.ifBlank {
            when (cfg.cloudProvider.uppercase()) {
                "GROQ" -> "https://api.groq.com/openai/v1/chat/completions"
                "OPENAI" -> "https://api.openai.com/v1/chat/completions"
                else -> "https://openrouter.ai/api/v1/chat/completions"
            }
        }
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", sys))
        hist.takeLast(8).forEach { h -> msgs.put(JSONObject().put("role", if (h.role == "user") "user" else "assistant").put("content", h.text)) }
        if (img != null) {
            val b64 = Base64.encodeToString(img, Base64.NO_WRAP)
            val arr = JSONArray().put(JSONObject().put("type", "text").put("text", userText))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64")))
            msgs.put(JSONObject().put("role", "user").put("content", arr))
        } else msgs.put(JSONObject().put("role", "user").put("content", userText))
        val body = JSONObject().put("model", cfg.cloudModel.ifBlank { "nousresearch/hermes-3-llama-3.1-405b:free" })
            .put("messages", msgs).put("temperature", 0.8).put("max_tokens", 700)
        val req = Request.Builder().url(url).addHeader("Authorization", "Bearer $key")
            .addHeader("HTTP-Referer", "https://humayra.local").addHeader("X-Title", "HUMAYRA")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) error("HTTP ${res.code}: ${raw.take(180)}")
            return JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
        }
    }
    private fun callGemini(cfg: AppConfig, key: String, sys: String, hist: List<ChatEntry>, userText: String, img: ByteArray?): String {
        val model = cfg.cloudModel.ifBlank { "gemini-2.0-flash" }.removePrefix("models/")
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
        val contents = JSONArray()
        hist.takeLast(8).forEach { h ->
            contents.put(JSONObject().put("role", if (h.role == "user") "user" else "model")
                .put("parts", JSONArray().put(JSONObject().put("text", h.text))))
        }
        val uParts = JSONArray().put(JSONObject().put("text", userText))
        if (img != null) {
            uParts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", Base64.encodeToString(img, Base64.NO_WRAP))))
        }
        contents.put(JSONObject().put("role", "user").put("parts", uParts))
        val root = JSONObject().put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
            .put("contents", contents)
        if (cfg.uncensoredMode) {
            val s = JSONArray()
            listOf("HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH", "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT")
                .forEach { s.put(JSONObject().put("category", it).put("threshold", "BLOCK_NONE")) }
            root.put("safetySettings", s)
        }
        val req = Request.Builder().url(url).post(root.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) error("Gemini ${res.code}: ${raw.take(180)}")
            return JSONObject(raw).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim()
        }
    }
}
