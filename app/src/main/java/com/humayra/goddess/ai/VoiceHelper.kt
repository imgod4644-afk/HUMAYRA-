package com.humayra.goddess.ai

import android.content.Context
import android.media.*
import android.os.*
import android.speech.tts.*
import kotlinx.coroutines.*
import java.io.*
import java.nio.*
import java.util.Locale

object VoiceHelper {
    private val fem = listOf("bnf","bn-in-language","bn-bd-language","ahp","cxx","ena","enc","en-in-language","hia","hif","cfn","hi-in-language","sfg","tpf","iob","iog","fis","female","-f-","_f_")
    private val mal = listOf("ban","bin","bnm","bnx","iom","iol","tpd","hid","hie","end","ene","rjs","gbb","gbd","sfb","aub","aud","male","-m-","_m_","#male")
    private val indWords = Regex("\\b(main|mein|meh|tum|kese|kaise|kya|hai|hoon|aap|accha|ami|tumi|apni|kemon|bhalo|valo|achi)\\b", RegexOption.IGNORE_CASE)
    fun cleanForSpeech(raw: String): String = raw
        .replace(Regex("\\[\\[ACTIONS:.*?]]", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("\\[\\[CONTINUE]]", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("https?://\\S+"), " ")
        .replace(Regex("[*#_`~>|•]+"), ", ")
        .replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF\\uFE0F\\u200D]"), " ")
        .replace(Regex("\\s*\\n+\\s*"), ", ").replace(Regex("\\s{2,}"), " ").trim()
    fun getVoicesForPicker(tts: TextToSpeech?): List<Voice> = runCatching {
        tts?.voices.orEmpty().filter { v ->
            val l = v.locale.language; val n = v.name.lowercase()
            (l == "bn" || l == "en" || l == "hi") && mal.none { n.contains(it) }
        }.sortedByDescending { v -> (if (fem.any { v.name.lowercase().contains(it) }) 1000 else 0) + v.quality }
    }.getOrDefault(emptyList())
    fun applyFemaleVoice(tts: TextToSpeech?, text: String, pitch: Float, speed: Float, prefVoice: String = "") {
        if (tts == null) return
        val loc = when {
            Regex("[\\u0980-\\u09FF]").containsMatchIn(text) -> Locale("bn", "IN")
            Regex("[\\u0900-\\u097F]").containsMatchIn(text) -> Locale("hi", "IN")
            indWords.containsMatchIn(text) -> Locale("en", "IN")
            else -> Locale.US
        }
        tts.language = loc
        runCatching {
            val all = tts.voices.orEmpty()
            val exact = if (prefVoice.isNotBlank()) all.firstOrNull { it.name == prefVoice } else null
            val vs = all.filter { it.locale.language == loc.language }.ifEmpty { all.filter { it.locale.language == "en" } }
            val cleanVs = vs.filter { v -> mal.none { v.name.lowercase().contains(it) } }.ifEmpty { vs }
            val pick = exact ?: cleanVs.maxByOrNull { v ->
                val n = v.name.lowercase()
                val f = if (fem.any { n.contains(it) } || v.features?.any { it.contains("female", true) } == true) 1000 else 0
                f + v.quality + (if (v.locale.country == loc.country) 50 else 0) + (if (!v.isNetworkConnectionRequired) 15 else 0)
            }
            if (pick != null) { tts.voice = pick; tts.language = pick.locale }
        }
        tts.setPitch(if (pitch in 0.99f..1.06f) 1.10f else pitch.coerceIn(0.85f, 1.35f))
        tts.setSpeechRate(if (speed in 1.00f..1.03f) 0.99f else speed.coerceIn(0.8f, 1.3f))
    }
    suspend fun synthesizeToM4a(ctx: Context, rawText: String, pitch: Float, speed: Float, prefVoice: String = ""): File? {
        val text = cleanForSpeech(rawText); if (text.isBlank()) return null
        val wav = File(ctx.cacheDir, "tg_tts_${System.currentTimeMillis()}.wav")
        val m4a = File(ctx.cacheDir, "tg_voice_${System.currentTimeMillis()}.m4a")
        val ready = CompletableDeferred<Boolean>(); val done = CompletableDeferred<Boolean>()
        var tts: TextToSpeech? = null
        Handler(Looper.getMainLooper()).post {
            tts = TextToSpeech(ctx.applicationContext) { st -> ready.complete(st == TextToSpeech.SUCCESS) }.apply {
                setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { if (!done.isCompleted) done.complete(true) }
                    @Deprecated("Deprecated") override fun onError(id: String?) { if (!done.isCompleted) done.complete(false) }
                })
            }
        }
        return try {
            if (withTimeoutOrNull(5000L) { ready.await() } != true) return null
            Handler(Looper.getMainLooper()).post {
                applyFemaleVoice(tts, text, pitch, speed, prefVoice)
                tts?.synthesizeToFile(text, Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "tg") }, wav, "tg")
            }
            if (withTimeoutOrNull(12_000L) { done.await() } != true || !wav.exists()) return null
            withContext(Dispatchers.IO) { if (wavToM4a(wav, m4a)) m4a else null }
        } finally {
            Handler(Looper.getMainLooper()).post { runCatching { tts?.shutdown() } }; wav.delete()
        }
    }
    private fun wavToM4a(wav: File, m4a: File): Boolean = runCatching {
        val raw = wav.readBytes(); if (raw.size < 44) return false
        var pos = 12; var sr = 22050; var ch = 1; var dataStart = 44; var dataLen = raw.size - 44
        while (pos + 8 <= raw.size) {
            val tag = String(raw, pos, 4); val sz = ByteBuffer.wrap(raw, pos + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (tag == "fmt " && pos + 20 <= raw.size) {
                ch = ByteBuffer.wrap(raw, pos + 10, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt().coerceIn(1, 2)
                sr = ByteBuffer.wrap(raw, pos + 12, 4).order(ByteOrder.LITTLE_ENDIAN).int.coerceIn(8000, 48000)
            } else if (tag == "data") { dataStart = pos + 8; dataLen = minOf(sz, raw.size - dataStart); break }
            pos += 8 + sz + (sz and 1)
        }
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sr, ch).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64_000); setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val mux = MediaMuxer(m4a.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1; var started = false; var idx = dataStart; val end = dataStart + dataLen; var pts = 0L; var eosIn = false
        try {
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); codec.start()
            val info = MediaCodec.BufferInfo()
            while (true) {
                if (!eosIn) {
                    val ii = codec.dequeueInputBuffer(5000)
                    if (ii >= 0) {
                        val ib = codec.getInputBuffer(ii)!!
                        val n = minOf(ib.remaining(), end - idx)
                        if (n > 0) { ib.put(raw, idx, n); codec.queueInputBuffer(ii, 0, n, pts, 0); idx += n; pts += (n / (2 * ch)) * 1_000_000L / sr }
                        else { codec.queueInputBuffer(ii, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM); eosIn = true }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 5000)
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { track = mux.addTrack(codec.outputFormat); mux.start(); started = true }
                else if (oi >= 0) {
                    val ob = codec.getOutputBuffer(oi)!!
                    if (info.size > 0 && started && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) { ob.position(info.offset); ob.limit(info.offset + info.size); mux.writeSampleData(track, ob, info) }
                    codec.releaseOutputBuffer(oi, false); if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
            true
        } finally { runCatching { codec.stop(); codec.release() }; runCatching { if (started) mux.stop(); mux.release() } }
    }.getOrDefault(false)
}
