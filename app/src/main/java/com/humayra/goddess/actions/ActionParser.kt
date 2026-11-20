package com.humayra.goddess.actions

import org.json.*

data class ParsedAiReply(val spokenText: String, val actions: List<JSONObject>, val needsContinue: Boolean = false)

object ActionParser {
    private val tagRegex = Regex("\\[\\[ACTIONS:\\s*(\\[[\\s\\S]*?])\\s*]]", RegexOption.IGNORE_CASE)
    private val contRegex = Regex("\\[\\[CONTINUE]]", RegexOption.IGNORE_CASE)

    fun parse(raw: String): ParsedAiReply {
        val cont = contRegex.containsMatchIn(raw)
        val body = raw.replace(contRegex, "").trim()
        val m = tagRegex.find(body) ?: return ParsedAiReply(body, emptyList(), cont)
        val list = mutableListOf<JSONObject>()
        try {
            val arr = JSONArray(m.groupValues[1])
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { o ->
                if (!o.has("type")) { val t = o.optString("action").ifBlank { o.optString("tool") }; if (t.isNotBlank()) o.put("type", t) }
                list.add(o)
            }
        } catch (_: Exception) {}
        return ParsedAiReply(body.replace(m.value, "").trim(), list, cont)
    }
}
