package com.humayra.goddess.ai

import com.llamatik.core.platform.GenStream
import com.llamatik.core.platform.LlamaBridge
import com.llamatik.core.platform.MultimodalBridge
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.util.concurrent.Executors

object LocalLlamaEngine {
    private val exec = Executors.newSingleThreadExecutor()
    private var loadedTextPath = ""
    private var loadedVisionPair = ""

    private fun applyParams(eco: Boolean) {
        LlamaBridge.updateGenerateParams(
            temperature = 0.78f, maxTokens = if (eco) 260 else 512, topP = 0.92f, topK = 40,
            repeatPenalty = 1.12f, contextLength = if (eco) 1536 else 2048,
            numThreads = if (eco) 2 else 4, useMmap = true, flashAttention = false,
            batchSize = if (eco) 128 else 256, gpuLayers = 0
        )
    }
    suspend fun generateText(modelPath: String, sys: String, ctx: String, prompt: String, eco: Boolean, onDelta: (String) -> Unit): String {
        val f = File(modelPath); if (!f.exists()) return "Local .gguf model পাওয়া যায়নি। Settings থেকে একটি .gguf ফাইল সিলেক্ট করুন।"
        val done = CompletableDeferred<String>()
        exec.execute {
            try {
                if (loadedVisionPair.isNotEmpty() && eco) { MultimodalBridge.release(); loadedVisionPair = "" }
                if (loadedTextPath != modelPath) {
                    if (loadedTextPath.isNotEmpty()) LlamaBridge.shutdown()
                    applyParams(eco)
                    if (!LlamaBridge.initGenerateModel(modelPath)) { done.complete("Error: .gguf লোড করা যায়নি (RAM চেক করুন)"); return@execute }
                    loadedTextPath = modelPath
                } else applyParams(eco)
                val sb = StringBuilder()
                LlamaBridge.generateStreamWithContext(sys, ctx, prompt, object : GenStream {
                    override fun onDelta(text: String) { sb.append(text); onDelta(text) }
                    override fun onComplete() { done.complete(sb.toString()) }
                    override fun onError(message: String) { done.complete("Local AI Error: $message") }
                })
            } catch (t: Throwable) { done.complete("Local AI Error: ${t.message}") }
        }
        return done.await()
    }
    suspend fun analyzeImage(modelPath: String, mmprojPath: String, imgBytes: ByteArray, prompt: String, eco: Boolean, onDelta: (String) -> Unit): String {
        if (!File(modelPath).exists() || !File(mmprojPath).exists()) return "SmolVLM .gguf এবং .mmproj ফাইল Settings থেকে সিলেক্ট করুন।"
        val done = CompletableDeferred<String>()
        exec.execute {
            try {
                if (loadedTextPath.isNotEmpty() && eco) { LlamaBridge.shutdown(); loadedTextPath = "" }
                val pair = "$modelPath|$mmprojPath"
                if (loadedVisionPair != pair) {
                    if (loadedVisionPair.isNotEmpty()) MultimodalBridge.release()
                    if (!MultimodalBridge.initModel(modelPath, mmprojPath)) { done.complete("Error: Vision মডেল লোড ব্যর্থ হয়েছে।"); return@execute }
                    loadedVisionPair = pair
                }
                val sb = StringBuilder()
                MultimodalBridge.analyzeImageBytesStream(imgBytes, prompt, object : GenStream {
                    override fun onDelta(text: String) { sb.append(text); onDelta(text) }
                    override fun onComplete() { done.complete(sb.toString()) }
                    override fun onError(message: String) { done.complete("Vision Error: $message") }
                })
            } catch (t: Throwable) { done.complete("Vision Error: ${t.message}") }
        }
        return done.await()
    }

    fun cancel() {
        try { LlamaBridge.nativeCancelGenerate() } catch (_: Throwable) {}
        try { MultimodalBridge.cancelAnalysis() } catch (_: Throwable) {}
    }

    fun releaseAll() = exec.execute {
        try { LlamaBridge.shutdown(); loadedTextPath = "" } catch (_: Throwable) {}
        try { MultimodalBridge.release(); loadedVisionPair = "" } catch (_: Throwable) {}
    }
}
