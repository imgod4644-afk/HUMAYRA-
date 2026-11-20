package com.humayra.goddess.actions

import org.json.*

data class ParsedAiReply(val spokenText: String, val actions: List<JSONObject>, val needsContinue: Boolean = false)

object ActionParser {
    private val contRegex = Regex("\\[\\[CONTINUE]]", RegexOption.IGNORE_CASE)
    fun parse(raw: String): ParsedAiReply {
        val cont = contRegex.containsMatchIn(raw)
        var body = raw.replace(contRegex, "").trim()
        val idx = body.indexOf("[[ACTION", ignoreCase = true)
        if (idx < 0) return ParsedAiReply(body, emptyList(), cont)
        val start = body.indexOf('[', idx + 2); val list = mutableListOf<JSONObject>()
        var end = -1
        if (start >= 0) {
            var depth = 0; var inStr = false; var esc = false
            for (i in start until body.length) {
                val c = body[i]
                if (esc) { esc = false; continue }; if (c == '\\') { esc = true; continue }
                if (c == '"') { inStr = !inStr; continue }
                if (!inStr) { if (c == '[') depth++ else if (c == ']') { depth--; if (depth == 0) { end = i; break } } }
            }
        }
        if (end > start) {
            runCatching {
                val arr = JSONArray(body.substring(start, end + 1))
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { o ->
                    if (!o.has("type")) { val t = o.optString("action").ifBlank { o.optString("tool") }; if (t.isNotBlank()) o.put("type", t) }
                    list.add(o)
                }
            }
        }
        val clean = body.substring(0, idx).trim()
        return ParsedAiReply(clean, list, cont)
    }
}
