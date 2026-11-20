package com.humayra.goddess.storage

import android.content.Context
import kotlinx.coroutines.*
import okhttp3.*
import java.io.File
import java.util.concurrent.TimeUnit

object ModelDownloader {
    private val http = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).connectTimeout(25, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
    const val VRM_SAMPLE = "https://cdn.jsdelivr.net/gh/pixiv/three-vrm@dev/packages/three-vrm/examples/models/VRM1_Constraint_Twist_Sample.vrm"
    const val LLAMA_1B = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf"
    const val SMOL_VLM = "https://huggingface.co/ggml-org/SmolVLM-500M-Instruct-GGUF/resolve/main/SmolVLM-500M-Instruct-Q8_0.gguf"
    const val SMOL_MM = "https://huggingface.co/ggml-org/SmolVLM-500M-Instruct-GGUF/resolve/main/mmproj-SmolVLM-500M-Instruct-Q8_0.gguf"
    suspend fun download(ctx: Context, url: String, subDir: String, fileName: String, onProg: (String) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(ctx.filesDir, subDir).apply { mkdirs() }
                val dest = File(dir, fileName); val tmp = File(dir, "$fileName.part")
                onProg("Connecting to server...")
                val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                http.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) error("HTTP ${res.code}")
                    val body = res.body ?: error("Empty body"); val tot = body.contentLength()
                    body.byteStream().use { inp -> tmp.outputStream().use { out ->
                        val buf = ByteArray(32768); var got = 0L; var lastMs = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val r = inp.read(buf); if (r <= 0) break; out.write(buf, 0, r); got += r
                            val now = System.currentTimeMillis()
                            if (now - lastMs >= 450L) {
                                lastMs = now; val mb = "%.1f".format(got / 1_048_576f)
                                val msg = if (tot > 0) "$fileName: ${mb}/${"%.1f".format(tot / 1_048_576f)} MB (${(got * 100 / tot).toInt()}%)" else "$fileName: $mb MB"
                                onProg(msg)
                            }
                        }
                    } }
                }
                if (dest.exists()) dest.delete(); tmp.renameTo(dest); dest
            }
        }
}
