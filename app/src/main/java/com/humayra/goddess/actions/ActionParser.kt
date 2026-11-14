package com.humayra.goddess.actions

import org.json.JSONArray
import org.json.JSONObject

data class ParsedAiReply(val spokenText: String, val actions: List<JSONObject>)

object ActionParser {
    private val tagRegex = Regex("\\[\\[ACTIONS:\\s*(\\[[\\s\\S]*?])\\s*]]", RegexOption.IGNORE_CASE)

    fun parse(raw: String): ParsedAiReply {
        val m = tagRegex.find(raw) ?: return ParsedAiReply(raw.trim(), emptyList())
        val list = mutableListOf<JSONObject>()
        try {
            val arr = JSONArray(m.groupValues[1])
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list.add(it) }
        } catch (_: Exception) {}
        val clean = raw.replace(m.value, "").trim()
        return ParsedAiReply(clean.ifBlank { "হয়ে গেছে, বস!" }, list)
    }
}
