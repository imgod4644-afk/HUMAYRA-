package com.humayra.goddess.ai

import android.speech.tts.*
import java.util.Locale

object VoiceHelper {
    private val fem = listOf("female","-f-","_f_","sfg","tpf","iob","iog","ban","bnd","bnf","bin","hia","hif","cfn","slf","ena","enc","ahp")
    private val mal = listOf("male","-m-","_m_","bnm","bnx","iom","iol","tpd","hid","hie","end","ene","sfb","gba","gbb","gbd")
    private val indWords = Regex("\\b(main|mein|meh|tum|kese|kaise|kya|hai|hoon|aap|accha|ami|tumi|apni|kemon|bhalo|valo|achi)\\b", RegexOption.IGNORE_CASE)
    fun cleanForSpeech(raw: String): String = raw
        .replace(Regex("\\[\\[ACTIONS:.*?]]", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("https?://\\S+"), " ")
        .replace(Regex("[*#_`~>|•]+"), ", ")
        .replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF\\uFE0F\\u200D]"), " ")
        .replace(Regex("\\s*\\n+\\s*"), ", ")
        .replace(Regex("\\s{2,}"), " ").trim()
    fun applyFemaleVoice(tts: TextToSpeech?, text: String, pitch: Float, speed: Float) {
        if (tts == null) return
        val loc = when {
            Regex("[\\u0980-\\u09FF]").containsMatchIn(text) -> Locale("bn", "BD")
            Regex("[\\u0900-\\u097F]").containsMatchIn(text) -> Locale("hi", "IN")
            indWords.containsMatchIn(text) -> Locale("en", "IN")
            else -> Locale.US
        }
        tts.language = loc
        runCatching {
            val all = tts.voices.orEmpty()
            val vs = all.filter { it.locale.language == loc.language }.ifEmpty { all.filter { it.locale.language == "en" } }
            val cleanVs = vs.filter { v -> mal.none { v.name.lowercase().contains(it) } }.ifEmpty { vs }
            val pick = cleanVs.maxByOrNull { v ->
                val n = v.name.lowercase()
                val f = if (fem.any { n.contains(it) } || v.features?.any { it.contains("female", true) } == true) 1000 else 0
                f + v.quality + (if (v.locale.country == loc.country) 50 else 0) + (if (!v.isNetworkConnectionRequired) 15 else 0)
            }
            if (pick != null) tts.voice = pick
        }
        tts.setPitch(if (pitch in 0.99f..1.06f) 1.09f else pitch.coerceIn(0.85f, 1.35f))
        tts.setSpeechRate(if (speed in 1.00f..1.03f) 0.99f else speed.coerceIn(0.8f, 1.3f))
    }
}
