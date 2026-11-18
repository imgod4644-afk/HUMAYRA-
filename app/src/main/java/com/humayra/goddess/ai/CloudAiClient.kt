package com.humayra.goddess.ai

import android.util.Base64
import com.humayra.goddess.storage.*
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ApiTarget(val prov: String, val key: String, val model: String)

object CloudAiClient {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).build()

    private fun cleanKey(k: String) = k.trim().removePrefix("Bearer").replace(Regex("\\s+"), "")

    fun availableTargets(cfg: AppConfig): List<ApiTarget> {
        val raw = listOf(
            ApiTarget("GEMINI", cleanKey(cfg.geminiApiKey), cfg.geminiModel),
            ApiTarget("GROQ", cleanKey(cfg.groqApiKey), cfg.groqModel),
            ApiTarget("OPENROUTER", cleanKey(cfg.openrouterApiKey), cfg.openrouterModel),
            ApiTarget("OPENAI", cleanKey(cfg.openaiApiKey), cfg.openaiModel)
        ).filter { it.key.isNotEmpty() }.map { t ->
            when {
                t.key.startsWith("AIza") -> t.copy(prov = "GEMINI")
                t.key.startsWith("gsk_") -> t.copy(prov = "GROQ")
                t.key.startsWith("sk-or-") -> t.copy(prov = "OPENROUTER")
                else -> t
            }
        }
        val pri = cfg.cloudProvider.uppercase()
        val base = (raw.filter { it.prov == pri } + raw.filter { it.prov != pri }).distinctBy { "${it.prov}:${it.key.take(8)}" }; return if (base.size == 1 && base[0].prov == "OPENROUTER") listOf(base[0], base[0].copy(model = "google/gemma-4-31b-it:free"), base[0].copy(model = "nvidia/nemotron-nano-12b-v2-vl:free")) else if (base.size == 1 && base[0].prov == "GEMINI") listOf(base[0], base[0].copy(model = "gemini-2.0-flash")) else base
    }
    suspend fun chat(cfg: AppConfig, sys: String, hist: List<ChatEntry>, userText: String, img: ByteArray? = null): Result<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val all = availableTargets(cfg)
                require(all.isNotEmpty()) { "কোনো Cloud API Key সেট করা নেই (Settings -> Save চাপুন)।" }
                val ready = all.filterNot { SelfGuardian.isCooling(it.prov) }.ifEmpty { all }
                if (cfg.councilMode && ready.size >= 2 && img == null && !userText.contains("[[")) {
                    val ans = coroutineScope {
                        ready.take(3).map { t -> async { runCatching { t.prov to callTarget(cfg, t, sys, hist, userText, null) }.getOrNull() } }
                            .awaitAll().filterNotNull()
                    }
                    if (ans.size >= 2) {
                        val synth = "Combine these answers concisely into the single best reply:\n" + ans.joinToString("\n---\n") { "${it.first}: ${it.second}" }
                        val best = runCatching { callTarget(cfg, ready.first(), sys, emptyList(), synth, null) }.getOrNull()
                        if (!best.isNullOrBlank()) return@runCatching best to "Council (${ans.joinToString("+") { it.first }})"
                    }
                }
                val errs = mutableListOf<String>()
                for (t in ready) {
                    val r = runCatching { callTarget(cfg, t, sys, hist, userText, img) }
                    if (r.isSuccess) { SelfGuardian.markOk(t.prov); return@runCatching r.getOrThrow() to t.prov }
                    SelfGuardian.markFail(t.prov); errs.add("${t.prov}: ${r.exceptionOrNull()?.message}")
                }
                error(errs.joinToString(" | "))
            }
        }
    private fun callTarget(cfg: AppConfig, t: ApiTarget, sys: String, hist: List<ChatEntry>, userText: String, img: ByteArray?): String =
        if (t.prov == "GEMINI") callGemini(cfg, t, sys, hist, userText, img) else callCompat(cfg, t, sys, hist, userText, img)

    private fun callCompat(cfg: AppConfig, t: ApiTarget, sys: String, hist: List<ChatEntry>, userText: String, img: ByteArray?): String {
        val url = when (t.prov) { "GROQ" -> "https://api.groq.com/openai/v1/chat/completions"; "OPENAI" -> "https://api.openai.com/v1/chat/completions"; else -> "https://openrouter.ai/api/v1/chat/completions" }
        val m = t.model.trim()
        val model = if (m.isBlank() || m.contains("hermes-3-llama-3.1-405b", true)) {
            when (t.prov) { "GROQ" -> "llama-3.3-70b-versatile"; "OPENAI" -> "gpt-4o-mini"; else -> "openrouter/free" }
        } else m
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", sys))
        hist.takeLast(8).forEach { h -> msgs.put(JSONObject().put("role", if (h.role == "user") "user" else "assistant").put("content", h.text)) }
        if (img != null) {
            val arr = JSONArray().put(JSONObject().put("type", "text").put("text", userText))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,${Base64.encodeToString(img, Base64.NO_WRAP)}")))
            msgs.put(JSONObject().put("role", "user").put("content", arr))
        } else msgs.put(JSONObject().put("role", "user").put("content", userText))
        val body = JSONObject().put("model", model).put("messages", msgs).put("temperature", 0.75).put("max_tokens", 1024)
        val req = Request.Builder().url(url).header("Authorization", "Bearer ${t.key}").header("HTTP-Referer", "https://humayra.local")
            .header("X-Title", "HUMAYRA").post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty(); if (!res.isSuccessful) error("HTTP ${res.code}: ${raw.take(140)}")
            val msgObj = JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val txt = if (!msgObj.isNull("content")) msgObj.optString("content", "").trim() else ""
            if (txt.isNotEmpty()) return txt
            val rsn = if (!msgObj.isNull("reasoning")) msgObj.optString("reasoning", "").trim() else ""
            if (rsn.isNotEmpty()) return rsn else error("Empty content from ${t.prov}")
        }
    }
    private fun callGemini(cfg: AppConfig, t: ApiTarget, sys: String, hist: List<ChatEntry>, userText: String, img: ByteArray?): String {
        val rawM = t.model.trim().removePrefix("models/")
        val model = if (rawM.isBlank() || rawM.contains("/") || rawM.contains("hermes", true)) "gemini-2.5-flash" else rawM
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${t.key}"
        val contents = JSONArray()
        hist.takeLast(8).forEach { h -> contents.put(JSONObject().put("role", if (h.role == "user") "user" else "model").put("parts", JSONArray().put(JSONObject().put("text", h.text)))) }
        val uParts = JSONArray().put(JSONObject().put("text", userText))
        if (img != null) uParts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", Base64.encodeToString(img, Base64.NO_WRAP))))
        contents.put(JSONObject().put("role", "user").put("parts", uParts))
        val root = JSONObject().put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys)))).put("contents", contents)
        if (cfg.uncensoredMode) {
            val s = JSONArray()
            listOf("HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH", "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT")
                .forEach { s.put(JSONObject().put("category", it).put("threshold", "BLOCK_NONE")) }
            root.put("safetySettings", s)
        }
        val req = Request.Builder().url(url).post(root.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty(); if (!res.isSuccessful) error("Gemini ${res.code}: ${raw.take(140)}")
            val parts = JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            val txt = (0 until (parts?.length() ?: 0)).mapNotNull { i -> val p = parts?.optJSONObject(i); if (p != null && !p.isNull("text")) p.optString("text", "") else null }.joinToString("").trim()
            return txt.ifEmpty { error("Gemini empty/blocked") }
        }
    }
}
