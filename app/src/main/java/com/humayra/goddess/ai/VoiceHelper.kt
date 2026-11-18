package com.humayra.goddess.ai

import android.speech.tts.*
import java.util.Locale

object VoiceHelper {
    private val fem = listOf("female", "-f-", "sfg", "tpf", "iob", "iog", "ban", "bnd", "bnf", "bin", "hia", "hif", "cfn", "slf")
    private val mal = listOf("male", "-m-", "bnm", "iom", "iol", "tpd", "hid", "hie")

    fun applyFemaleVoice(tts: TextToSpeech?, text: String, pitch: Float, speed: Float) {
        if (tts == null) return
        val loc = when {
            Regex("[\\u0980-\\u09FF]").containsMatchIn(text) -> Locale("bn", "BD")
            Regex("[\\u0900-\\u097F]").containsMatchIn(text) -> Locale("hi", "IN")
            else -> Locale.US
        }
        tts.language = loc
        runCatching {
            val vs = tts.voices?.filter { it.locale.language == loc.language }.orEmpty()
            val pick: Voice? = vs.firstOrNull { v -> val n = v.name.lowercase(); fem.any { n.contains(it) } && mal.none { n.contains(it) } && !v.isNetworkConnectionRequired }
                ?: vs.firstOrNull { v -> val n = v.name.lowercase(); fem.any { n.contains(it) } && mal.none { n.contains(it) } }
                ?: vs.firstOrNull { v -> mal.none { v.name.lowercase().contains(it) } }
            if (pick != null) tts.voice = pick
        }
        tts.setPitch(if (pitch in 0.99f..1.06f) 1.12f else pitch)
        tts.setSpeechRate(speed)
    }
}
