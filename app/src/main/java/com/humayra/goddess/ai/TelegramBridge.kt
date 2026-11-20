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
    private var lastUpdateId = 0L; private var alwaysVoice = false
    fun sendMessage(token: String, chatId: String, text: String): Boolean = runCatching {
        if (token.isBlank() || chatId.isBlank()) return false
        val body = FormBody.Builder().add("chat_id", chatId).add("text", text.take(4000)).build()
        http.newCall(Request.Builder().url("https://api.telegram.org/bot$token/sendMessage").post(body).build()).execute().use { it.isSuccessful }
    }.getOrDefault(false)
    fun sendVoice(token: String, chatId: String, file: File, caption: String = ""): Boolean = runCatching {
        if (token.isBlank() || chatId.isBlank() || !file.exists()) return false
        val rb = file.asRequestBody("audio/mp4".toMediaType())
        val mb = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("chat_id", chatId).addFormDataPart("caption", caption.take(900)).addFormDataPart("voice", file.name, rb).build()
        http.newCall(Request.Builder().url("https://api.telegram.org/bot$token/sendVoice").post(mb).build()).execute().use { it.isSuccessful }
    }.getOrDefault(false)
    private fun sendTyping(token: String, chatId: String, act: String = "typing") = runCatching {
        val body = FormBody.Builder().add("chat_id", chatId).add("action", act).build()
        http.newCall(Request.Builder().url("https://api.telegram.org/bot$token/sendChatAction").post(body).build()).execute().close()
    }
    private fun downloadVoice(tok: String, fid: String): ByteArray? = runCatching {
        val r = http.newCall(Request.Builder().url("https://api.telegram.org/bot$tok/getFile?file_id=$fid").build()).execute().use { it.body?.string() } ?: return null
        val path = JSONObject(r).optJSONObject("result")?.optString("file_path").orEmpty(); if (path.isBlank()) return null
        http.newCall(Request.Builder().url("https://api.telegram.org/file/bot$tok/$path").build()).execute().use { if (it.isSuccessful) it.body?.bytes() else null }
    }.getOrNull()
    fun sendBackupFile(token: String, chatId: String, file: File, caption: String): Boolean = runCatching {
        if (token.isBlank() || chatId.isBlank() || !file.exists()) return false
        val body = file.asRequestBody("application/octet-stream".toMediaType())
        val mb = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("chat_id", chatId).addFormDataPart("caption", caption).addFormDataPart("document", file.name, body).build()
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
    private fun hasBn(s: String) = s.any { it in '\u0980'..'\u09FF' }
    private fun hasDeva(s: String) = s.any { it in '\u0900'..'\u097F' }
    private fun normWord(w: String) = w.replace(Regex("(.)\\1{2,}"), "$1")
    private val hiWords = setOf("main","mein","meh","hum","tum","tumhe","tumhara","tumara","mera","meri","tera","teri","aap","aapka","kaisa","kaisi","kaise","kese","kesi","kya","kyu","kyun","hai","hain","hoon","ho","nahi","nhi","tho","haan","han","mujhe","accha","achha","acha","theek","thik","badiya","badhiya","mast","yaar","jaanu","raha","rahi","rahe","gaya","gayi","gaye","kuch","batao","btao","bohot","bahut","abhi","phir","fir","arre","oye","suno","baat","hua","hui","karo","sach","pagal","ekdum","khana","khaya")
    private val bnWords = setOf("ami","tumi","apni","tui","amar","tomar","apnar","tor","kemon","acho","achen","achi","achis","keno","kothay","kothai","korcho","korchen","korchi","korbo","korle","korlen","bhalo","valo","kharap","baje","ajke","aajke","ekhon","holo","hoyeche","hoise","hobe","bolen","bolcho","bolchi","tomake","apnake","parbo","jabo","khabo","khaiso","khecho","valobashi","bhalobashi","ektu","onek","khub","kichu","kisu","darun","naki","eshecho","gecho","taina","shona")
    private val enWords = setOf("you","your","are","am","is","was","were","the","from","now","will","my","name","girlfriend","what","how","why","when","where","that","this","with","have","has","really","today","good","nice","school","feel","chat","love","baby")
    private fun looksBanglish(s: String): Boolean {
        val w = Regex("[a-zA-Z']+").findAll(s.lowercase()).map { normWord(it.value) }.toList()
        return w.count { it in bnWords } > w.count { it in hiWords } && w.count { it in bnWords } >= 2
    }
    private fun wantsRespect(mems: List<String>, txt: String): Boolean {
        val all = mems.joinToString(" ") + " " + txt
        return all.contains("আপনি") || all.contains("apni", true) || Regex("\\baap\\b", RegexOption.IGNORE_CASE).containsMatchIn(all)
    }
    private fun detectStyle(txt: String, hist: List<ChatEntry>): String {
        if (txt.count { it in '\u0980'..'\u09FF' } >= 2) return "BENGALI"
        if (txt.count { it in '\u0900'..'\u097F' } >= 2) return "HINDI"
        val w = Regex("[a-zA-Z']+").findAll(txt.lowercase()).map { normWord(it.value) }.toList()
        val hi = w.count { it in hiWords }; val bn = w.count { it in bnWords }; val en = w.count { it in enWords }
        if (hi > bn && hi > 0) return "HINGLISH"; if (bn > hi && bn > 0) return "BANGLISH"
        if (en >= 2 && hi == 0 && bn == 0) return "ENGLISH"
        for (e in hist.asReversed()) if (e.role == "user") detectStyle(e.text, emptyList()).let { if (it != "AUTO") return it }
        return if (w.isNotEmpty()) "ENGLISH" else "AUTO"
    }
    private fun buildHumanSys(ctx: Context, cfg: AppConfig, style: String, txt: String): String {
        val rawMems = MemoryStore(ctx).getMemories().takeLast(14)
        val respect = wantsRespect(rawMems, txt)
        val mems = (if (style == "BENGALI" || style == "HINDI") rawMems else rawMems.filter { !hasBn(it) && !hasDeva(it) }).joinToString("; ")
        val hon = when { !respect -> ""; style == "HINGLISH" -> "Use 'aap'. "; style == "BANGLISH" -> "Use 'apni'. "; style == "BENGALI" -> "Use natural 'আপনি'. "; else -> "" }
        val langLock = when (style) {
            "HINGLISH" -> "LANG LOCK: Reply ONLY in Hinglish (Roman Hindi+English, e.g. 'Arrey sach mein?! 😳'). ZERO Bengali!"
            "BANGLISH" -> "LANG LOCK: Reply ONLY in Banglish (Roman Bengali, e.g. 'Sotti naki?! 😳'). ZERO Bengali script!"
            "HINDI" -> "LANG LOCK: Reply ONLY in conversational Hindi."
            "BENGALI" -> "LANG LOCK: শুধু বাংলা অক্ষরে স্বাভাবিক কথ্য বাংলায় উত্তর দাও (যেমন 'আরে সত্যি নাকি?! 😳')। কোনো হিন্দি/হিংলিশ লিখবে না!"
            else -> "LANG LOCK: Reply ONLY in casual English (e.g. 'Wait seriously?! 😳')."
        }
        return "You are ${cfg.companionName}, texting ${cfg.userName} on Telegram as his real human girlfriend. NEVER sound like AI/bot. React warmly & playfully (surprise, curiosity, teasing) in 1-2 short lines. Adopt his nickname/role. $hon$langLock " +
            (if (cfg.customPrompt.isNotBlank()) "Style: ${cfg.customPrompt}. " else "") +
            (if (mems.isNotBlank()) "Facts: $mems" else "")
    }
    suspend fun pollOnce(ctx: Context) {
        val cfg = AppConfig(ctx); if (!cfg.masterEnabled || !cfg.telegramHumanEnabled) return
        val tok = cfg.telegramToken.trim(); if (tok.isBlank()) return
        val raw = runCatching { http.newCall(Request.Builder().url("https://api.telegram.org/bot$tok/getUpdates?offset=${lastUpdateId + 1}&timeout=3").build()).execute().use { if (it.isSuccessful) it.body?.string() else null } }.getOrNull() ?: return
        val res = runCatching { JSONObject(raw).optJSONArray("result") }.getOrNull() ?: return
        for (i in 0 until res.length()) {
            val u = res.optJSONObject(i) ?: continue; lastUpdateId = maxOf(lastUpdateId, u.optLong("update_id"))
            val msg = u.optJSONObject("message") ?: continue; val cid = msg.optJSONObject("chat")?.optLong("id")?.toString() ?: continue
            if (cfg.telegramChatId.isNotBlank() && cid != cfg.telegramChatId) continue
            val vObj = msg.optJSONObject("voice") ?: msg.optJSONObject("audio"); var txt = msg.optString("text").trim(); val isV = vObj != null
            if (txt == "/start") { if (cfg.telegramChatId.isBlank()) cfg.telegramChatId = cid; sendMessage(tok, cid, "Hey! Finally text korle 😊"); continue }
            if (txt == "/voice") { alwaysVoice = !alwaysVoice; sendMessage(tok, cid, if (alwaysVoice) "🎤 Voice Reply ON 💜" else "💬 Text Reply ON 💜"); continue }
            if (isV && txt.isBlank()) {
                sendTyping(tok, cid, "record_voice")
                val b = downloadVoice(tok, vObj!!.optString("file_id"))
                txt = if (b != null) CloudAiClient.hearAudio(cfg, b, "audio/ogg").orEmpty() else ""
                if (txt.isBlank()) { sendMessage(tok, cid, "Voice shunte Settings-e Gemini ba Groq key dao na 💜"); continue }
            }
            if (txt.isBlank()) continue
            if (!isV) sendTyping(tok, cid)
            if (txt.contains("আপনি") || txt.contains("apni", true) || txt.contains("name", true) || txt.contains("নাম", true) || txt.contains("girlfriend", true) || txt.contains("dakbe", true) || Regex("\\baap\\b", RegexOption.IGNORE_CASE).containsMatchIn(txt)) MemoryStore(ctx).addMemory(txt)
            val hist = tgHist(ctx); val style = detectStyle(txt, hist); val sys = buildHumanSys(ctx, cfg, style, txt)
            val cHist = if (style == "BENGALI" || style == "HINDI") hist else hist.filter { !hasBn(it.text) && !hasDeva(it.text) }
            val online = HybridBrain.isOnline(ctx) && CloudAiClient.availableTargets(cfg).isNotEmpty()
            val reply = if (online) CloudAiClient.chat(cfg, sys, cHist, txt, null).getOrNull()?.first
                else LocalLlamaEngine.generateText(cfg.localTextModelPath, sys, cHist.takeLast(2).joinToString("\n") { "${it.role}: ${it.text}" }, txt, cfg.ecoMode) {}
            val bad = reply != null && ((style in listOf("HINGLISH", "BANGLISH", "ENGLISH") && (hasBn(reply) || hasDeva(reply))) || (style == "HINGLISH" && looksBanglish(reply)) || (style == "BENGALI" && !hasBn(reply)))
            val rwSys = "Rewrite ONLY in $style (${if (style == "BENGALI") "pure Bengali script" else "Roman letters, ZERO Bengali/Devanagari"}). Warm human girlfriend tone." + if (sys.contains("'aap'")) " Use 'aap'." else if (sys.contains("'apni'")) " Use 'apni'." else ""
            val fixed = if (bad && online) CloudAiClient.chat(cfg, rwSys, emptyList(), "User: $txt\nFix: $reply", null).getOrNull()?.first else reply
            val fb = when (style) { "HINGLISH" -> "Network slow hai, phir se bolo na? 💜"; "BENGALI" -> "নেটওয়ার্কটা সমস্যা করছে গো, আরেকবার বলবে? 💜"; else -> "Network problem, say again? 💜" }
            val clean = fixed?.takeIf { it.isNotBlank() } ?: fb
            hist.add(ChatEntry("user", txt, System.currentTimeMillis())); hist.add(ChatEntry("assistant", clean, System.currentTimeMillis()))
            saveTgHist(ctx, hist)
            val wantVoice = cfg.telegramVoiceReplies && (alwaysVoice || isV || txt.contains("voice", true) || txt.contains("audio", true) || txt.contains("ভয়েস") || txt.contains("শোনাও"))
            val vf = if (wantVoice) VoiceHelper.synthesizeToM4a(ctx, clean, cfg.ttsPitch, cfg.ttsSpeed, cfg.preferredVoiceName) else null
            if (vf == null || !sendVoice(tok, cid, vf, clean)) sendMessage(tok, cid, clean)
            vf?.delete()
        }
    }
}
