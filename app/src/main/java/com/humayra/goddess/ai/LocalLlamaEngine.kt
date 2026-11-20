package com.humayra.goddess.ai

import com.llamatik.core.platform.*
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.Executors

object LocalLlamaEngine {
    private val exec = Executors.newSingleThreadExecutor()
    private var loadedTextPath = ""
    private var loadedVisionPair = ""
    private fun applyParams(eco: Boolean) {
        LlamaBridge.updateGenerateParams(
            temperature = 0.72f, maxTokens = if (eco) 160 else 280, topP = 0.9f, topK = 40,
            repeatPenalty = 1.1f, contextLength = if (eco) 1024 else 1536,
            numThreads = 4, useMmap = true, flashAttention = false, batchSize = 256, gpuLayers = 0
        )
    }
    suspend fun generateText(modelPath: String, sys: String, ctx: String, prompt: String, eco: Boolean, onDelta: (String) -> Unit): String {
        val f = File(modelPath); if (!f.exists()) return "Local .gguf পাওয়া যায়নি — Settings থেকে মডেল সিলেক্ট করুন।"
        val done = CompletableDeferred<String>(); val sb = StringBuilder()
        exec.execute {
            try {
                if (loadedVisionPair.isNotEmpty()) { MultimodalBridge.release(); loadedVisionPair = "" }
                if (loadedTextPath != modelPath) {
                    if (loadedTextPath.isNotEmpty()) LlamaBridge.shutdown()
                    applyParams(eco)
                    if (!LlamaBridge.initGenerateModel(modelPath)) { done.complete("Error: .gguf লোড হয়নি (RAM চেক করুন)"); return@execute }
                    loadedTextPath = modelPath
                } else applyParams(eco)
                LlamaBridge.generateStreamWithContext(sys, ctx, prompt, object : GenStream {
                    override fun onDelta(text: String) { sb.append(text); onDelta(text) }
                    override fun onComplete() { if (!done.isCompleted) done.complete(sb.toString()) }
                    override fun onError(message: String) { if (!done.isCompleted) done.complete("Local AI Error: $message") }
                })
            } catch (t: Throwable) { if (!done.isCompleted) done.complete("Local AI Error: ${t.message}") }
        }
        return withTimeoutOrNull(55_000L) { done.await() } ?: run { cancel(); sb.toString().ifBlank { "Offline AI টাইমআউট — Eco Mode ON বা 1B মডেল ব্যবহার করুন।" } }
    }
    suspend fun analyzeImage(modelPath: String, mmprojPath: String, imgBytes: ByteArray, prompt: String, eco: Boolean, onDelta: (String) -> Unit): String {
        if (!File(modelPath).exists() || !File(mmprojPath).exists()) return "SmolVLM .gguf ও .mmproj Settings থেকে দিন।"
        val done = CompletableDeferred<String>(); val sb = StringBuilder()
        exec.execute {
            try {
                if (loadedTextPath.isNotEmpty() && eco) { LlamaBridge.shutdown(); loadedTextPath = "" }
                val pair = "$modelPath|$mmprojPath"
                if (loadedVisionPair != pair) {
                    if (loadedVisionPair.isNotEmpty()) MultimodalBridge.release()
                    if (!MultimodalBridge.initModel(modelPath, mmprojPath)) { done.complete("Error: Vision মডেল লোড ব্যর্থ।"); return@execute }
                    loadedVisionPair = pair
                }
                MultimodalBridge.analyzeImageBytesStream(imgBytes, prompt, object : GenStream {
                    override fun onDelta(text: String) { sb.append(text); onDelta(text) }
                    override fun onComplete() { done.complete(sb.toString()) }
                    override fun onError(message: String) { done.complete("Vision Error: $message") }
                })
            } catch (t: Throwable) { done.complete("Vision Error: ${t.message}") }
        }
        return done.await()
    }
    fun cancel() { try { LlamaBridge.nativeCancelGenerate() } catch (_: Throwable) {}; try { MultimodalBridge.cancelAnalysis() } catch (_: Throwable) {} }
    fun releaseAll() = exec.execute { try { LlamaBridge.shutdown(); loadedTextPath = "" } catch (_: Throwable) {}; try { MultimodalBridge.release(); loadedVisionPair = "" } catch (_: Throwable) {} }
}
