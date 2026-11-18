package com.humayra.goddess.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.*
import androidx.webkit.WebViewAssetLoader
import com.humayra.goddess.storage.AppConfig
import java.io.File

class StageController(private val ctx: Context, private val web: WebView) {
    private val avatarsDir = File(ctx.filesDir, "avatars").apply { mkdirs() }
    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
        .addPathHandler("/avatars/", WebViewAssetLoader.InternalStoragePathHandler(ctx, avatarsDir)).build()

    @SuppressLint("SetJavaScriptEnabled")
    fun init() {
        web.setBackgroundColor(Color.TRANSPARENT)
        web.settings.apply { javaScriptEnabled = true; domStorageEnabled = true; allowFileAccess = false }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, req: WebResourceRequest) = assetLoader.shouldInterceptRequest(req.url)
            override fun onPageFinished(v: WebView, url: String?) {
                val c = AppConfig(ctx); setTheme(c.stageBgColor, c.stageAccentColor)
                setWallpaper(c.stageWallpaperPath.isNotBlank())
                if (c.activeAvatarPath.isNotBlank()) loadAvatar(c.activeAvatarPath)
            }
        }
        web.loadUrl("https://appassets.androidplatform.net/assets/humayra_stage.html")
    }

    fun setPose(pose: String) = eval("setPose(${org.json.JSONObject.quote(pose)})")
    fun setTheme(bg: String, acc: String) = eval("setTheme(${org.json.JSONObject.quote(bg)},${org.json.JSONObject.quote(acc)})")
    fun setWallpaper(on: Boolean) = eval("setWallpaper($on)")
    fun setSpeaking(speaking: Boolean) = eval("setSpeaking($speaking)")
    fun loadAvatar(path: String) {
        val f = File(path)
        if (f.exists()) eval("loadModel('https://appassets.androidplatform.net/avatars/${f.name}')") else eval("resetFallback()")
    }
    private fun eval(js: String) { web.post { web.evaluateJavascript(js, null) } }
}
