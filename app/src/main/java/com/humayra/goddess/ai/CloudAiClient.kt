package com.humayra.goddess.ai

import android.util.Base64
import com.humayra.goddess.storage.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.*
import java.io.*
import java.util.concurrent.TimeUnit

data class ApiTarget(val prov: String, val key: String, val model: String)

object CloudAiClient {
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(22, TimeUnit.SECONDS).build()
    @Volatile private var freeCache: List<String> = emptyList()
    @Volatile private var freeCacheMs = 0L
    private fun cleanKey(k: String) = k.trim().removePrefix("Bearer").replace(Regex("\\s+"), "")
    private fun fastOpenRouterFallbacks(): List<String> {
        val now = System.currentTimeMillis()
        if (freeCache.isEmpty() || now - freeCacheMs > 1_800_000L) {
            runCatching {
                val req = Request.Builder().url("https://openrouter.ai/api/v1/models").build()
                http.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val arr = JSONObject(res.body?.string().orEmpty()).optJSONArray("data") ?: return@use
                        val picks = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.filter { o ->
                            val id = o.optString("id"); val pr = o.optJSONObject("pricing")
                            id.endsWith(":free") && pr?.optString("prompt") == "0" && pr.optString("completion") == "0"
                        }.map { it.optString("id") }.filter { !it.contains("r1", true) && !it.contains("reasoning", true) && !it.contains("ultra", true) }.take(3)
                        if (picks.isNotEmpty()) { freeCache = picks; freeCacheMs = now }
                    }
                }
            }
        }
        return (listOf("openrouter/free", "nvidia/nemotron-3.5-lightning:free", "google/gemma-4-26b-a4b-it:free", "liquid/lfm-2.5-2.6b:free") + freeCache).distinct()
    }
    fun availableTargets(cfg: AppConfig): List<ApiTarget> {
        val raw = listOf(
            ApiTarget("GEMINI", cleanKey(cfg.geminiApiKey), cfg.geminiModel),
            ApiTarget("GROQ", cleanKey(cfg.groqApiKey), cfg.groqModel),
            ApiTarget("OPENROUTER", cleanKey(cfg.openrouterApiKey), cfg.openrouterModel),
            ApiTarget("OPENAI", cleanKey(cfg.openaiApiKey), cfg.openaiModel)
        ).filter { it.key.isNotEmpty() }.map { t ->
            when { t.key.startsWith("AIza") -> t.copy(prov = "GEMINI"); t.key.startsWith("gsk_") -> t.copy(prov = "GROQ"); t.key.startsWith("sk-or-") -> t.copy(prov = "OPENROUTER"); else -> t }
        }
        val pri = cfg.cloudProvider.uppercase()
        val base = (raw.filter { it.prov == pri } + raw.filter { it.prov != pri }).distinctBy { "${it.prov}:${it.key.take(8)}" }
        if (base.size != 1) return base
        val b = base[0]
        return when (b.prov) {
            "OPENROUTER" -> (listOf(b.model) + fastOpenRouterFallbacks()).filter { it.isNotBlank() }.distinct().take(5).map { b.copy(model = it) }
            "GEMINI" -> listOf(b.model, "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash").filter { it.isNotBlank() }.distinct().map { b.copy(model = it) }
            "GROQ" -> listOf(b.model, "llama-3.3-70b-versatile", "llama-3.1-8b-instant").filter { it.isNotBlank() }.distinct().map { b.copy(model = it) }
            else -> base
        }
    }
    suspend fun chat(cfg: AppConfig, sys: String, hist: List<ChatEntry>, userText: String, img: ByteArray? = null, onDelta: (String) -> Unit = {}): Result<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val all = availableTargets(cfg)
                require(all.isNotEmpty()) { "কোনো Cloud API Key সেট করা নেই (Settings -> Save চাপুন)।" }
                val ready = all.filterNot { SelfGuardian.isCooling(it.prov + ":" + it.model) }.ifEmpty { all }
                if (cfg.councilMode && ready.size >= 2 && img == null && !userText.contains("[[")) {
                    val ans = coroutineScope { ready.take(3).map { t -> async { runCatching { t.model to callTarget(cfg, t, sys, hist, userText, null) {} }.getOrNull() } }.awaitAll().filterNotNull() }
                    if (ans.size >= 2) {
                        val best = runCatching { callTarget(cfg, ready.first(), sys, emptyList(), "Combine concisely:\n" + ans.joinToString("\n") { "${it.first}: ${it.second}" }, null, onDelta) }.getOrNull()
                        if (!best.isNullOrBlank()) return@runCatching best to "Council"
                    }
                }
                val errs = mutableListOf<String>()
                for (t in ready) {
                    val r = runCatching { callTarget(cfg, t, sys, hist, userText, img, onDelta) }
                    if (r.isSuccess) { SelfGuardian.markOk(t.prov + ":" + t.model); return@runCatching r.getOrThrow() to t.prov }
                    SelfGuardian.markFail(t.prov + ":" + t.model); errs.add("${t.model}: ${r.exceptionOrNull()?.message?.take(60)}")
                }
                error(errs.joinToString(" | "))
            }
        }
    private fun callTarget(cfg: AppConfig, t: ApiTarget, sys: String, hist: List<ChatEntry>, userText: String, img: ByteArray?, onDelta: (String) -> Unit): String =
        if (t.prov == "GEMINI") callGemini(cfg, t, sys, hist, userText, img, onDelta) else callCompat(cfg, t, sys, hist, userText, img, onDelta)
    private fun callCompat(cfg: AppConfig, t: ApiTarget, sys: String, hist: List<ChatEntry>, userText: String, img: ByteArray?, onDelta: (String) -> Unit): String {
        val url = when (t.prov) { "GROQ" -> "https://api.groq.com/openai/v1/chat/completions"; "OPENAI" -> "https://api.openai.com/v1/chat/completions"; else -> "https://openrouter.ai/api/v1/chat/completions" }
        val m = t.model.trim(); val model = if (m.isBlank() || m.contains("hermes-3", true)) when (t.prov) { "GROQ" -> "llama-3.3-70b-versatile"; "OPENAI" -> "gpt-4o-mini"; else -> "openrouter/free" } else m
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", sys))
        hist.takeLast(6).forEach { h -> msgs.put(JSONObject().put("role", if (h.role == "user") "user" else "assistant").put("content", h.text)) }
        if (img != null) {
            val arr = JSONArray().put(JSONObject().put("type", "text").put("text", userText)).put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,${Base64.encodeToString(img, Base64.NO_WRAP)}")))
            msgs.put(JSONObject().put("role", "user").put("content", arr))
        } else msgs.put(JSONObject().put("role", "user").put("content", userText))
        val body = JSONObject().put("model", model).put("messages", msgs).put("temperature", 0.72).put("max_tokens", 700).put("stream", true)
        if (t.prov == "OPENROUTER") body.put("reasoning", JSONObject().put("effort", "low").put("exclude", true))
        val req = Request.Builder().url(url).header("Authorization", "Bearer ${t.key}").header("HTTP-Referer", "https://humayra.local").header("X-Title", "HUMAYRA").post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) error("HTTP ${res.code}: ${res.body?.string().orEmpty().take(120)}")
            val sb = StringBuilder(); var inTag = false
            BufferedReader(InputStreamReader(res.body!!.byteStream())).use { br ->
                while (true) {
                    val line = br.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim(); if (data == "[DONE]") break
                    val ch = runCatching { JSONObject(data).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content", "") }.getOrNull().orEmpty()
                    if (ch.isNotEmpty()) { sb.append(ch); if (!inTag && !sb.contains("[[")) onDelta(ch) else inTag = true }
                }
            }
            return sb.toString().trim().ifEmpty { error("Empty stream from ${t.model}") }
        }
    }
    private fun callGemini(cfg: AppConfig, t: ApiTarget, sys: String, hist: List<ChatEntry>, userText: String, img: ByteArray?, onDelta: (String) -> Unit): String {
        val rawM = t.model.trim().removePrefix("models/")
        val model = if (rawM.isBlank() || rawM.contains("/") || rawM.contains("hermes", true)) "gemini-2.5-flash" else rawM
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${t.key}"
        val contents = JSONArray()
        hist.takeLast(6).forEach { h -> contents.put(JSONObject().put("role", if (h.role == "user") "user" else "model").put("parts", JSONArray().put(JSONObject().put("text", h.text)))) }
        val uParts = JSONArray().put(JSONObject().put("text", userText))
        if (img != null) uParts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", Base64.encodeToString(img, Base64.NO_WRAP))))
        contents.put(JSONObject().put("role", "user").put("parts", uParts))
        val root = JSONObject().put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys)))).put("contents", contents)
        if (cfg.uncensoredMode) {
            val s = JSONArray()
            listOf("HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH", "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT").forEach { s.put(JSONObject().put("category", it).put("threshold", "BLOCK_NONE")) }
            root.put("safetySettings", s)
        }
        val req = Request.Builder().url(url).post(root.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty(); if (!res.isSuccessful) error("Gemini ${res.code}: ${raw.take(120)}")
            val parts = JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            val txt = (0 until (parts?.length() ?: 0)).mapNotNull { i -> val p = parts?.optJSONObject(i); if (p != null && !p.isNull("text")) p.optString("text", "") else null }.joinToString("").trim()
            if (txt.isEmpty()) error("Gemini empty") else { onDelta(txt.replace(Regex("\\[\\[ACTIONS:[\\s\\S]*?]]"), "")); return txt }
        }
    }
    suspend fun hearAudio(cfg: AppConfig, audio: ByteArray, mime: String): String? = withContext(Dispatchers.IO) {
        val t = availableTargets(cfg).firstOrNull { it.prov == "GEMINI" || it.prov == "GROQ" } ?: return@withContext null
        runCatching {
            if (t.prov == "GEMINI") {
                val p = JSONArray().put(JSONObject().put("text", "Transcribe this voice message accurately in its original language (Bangla/Hindi/Hinglish/Banglish/English). Output ONLY the transcription.")).put(JSONObject().put("inline_data", JSONObject().put("mime_type", mime).put("data", Base64.encodeToString(audio, Base64.NO_WRAP))))
                val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", p)))
                val req = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${t.key}").post(body.toString().toRequestBody("application/json".toMediaType())).build()
                http.newCall(req).execute().use { r -> JSONObject(r.body?.string().orEmpty()).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")?.trim() }
            } else {
                val mb = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("model", "whisper-large-v3-turbo").addFormDataPart("file", "voice.ogg", audio.toRequestBody(mime.toMediaType())).build()
                val req = Request.Builder().url("https://api.groq.com/openai/v1/audio/transcriptions").header("Authorization", "Bearer ${t.key}").post(mb).build()
                http.newCall(req).execute().use { r -> JSONObject(r.body?.string().orEmpty()).optString("text").trim().ifBlank { null } }
            }
        }.getOrNull()
    }
}
