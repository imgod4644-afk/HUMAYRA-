package com.humayra.goddess.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import androidx.core.app.NotificationCompat
import com.humayra.goddess.HumayraApp
import com.humayra.goddess.R
import com.humayra.goddess.storage.AppConfig
import com.humayra.goddess.ui.ScreenCaptureConsentActivity
import java.io.ByteArrayOutputStream

class ScreenCaptureService : Service() {
    private var proj: MediaProjection? = null
    private var vd: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = NotificationCompat.Builder(this, HumayraApp.CH_CAPTURE).setSmallIcon(R.drawable.ic_humayra_emblem)
            .setContentTitle("HUMAYRA Screen Watcher").setContentText("Single-frame eco capture").build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(402, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(402, n)
        val code = intent?.getIntExtra("code", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data: Intent? = intent?.getParcelableExtra("data")
        if (code != Activity.RESULT_OK || data == null) { cleanup(); stopSelf(); return START_NOT_STICKY }
        val dm = resources.displayMetrics; val maxW = if (AppConfig(this).ecoMode) 640 else 900
        val sc = minOf(1f, maxW.toFloat() / dm.widthPixels)
        val w = (dm.widthPixels * sc).toInt().coerceAtLeast(320); val h = (dm.heightPixels * sc).toInt().coerceAtLeast(320)
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        proj = mpm.getMediaProjection(code, data)?.apply {
            registerCallback(object : MediaProjection.Callback() { override fun onStop() { cleanup() } }, handler)
        }
        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        vd = proj?.createVirtualDisplay("HumayraCap", w, h, dm.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, handler)
        handler.postDelayed({
            var bytes: ByteArray? = null
            try {
                reader?.acquireLatestImage()?.use { img ->
                    val pl = img.planes[0]; val pad = (pl.rowStride - pl.pixelStride * w) / pl.pixelStride
                    val raw = Bitmap.createBitmap(w + pad, h, Bitmap.Config.ARGB_8888).also { it.copyPixelsFromBuffer(pl.buffer) }
                    val bmp = Bitmap.createBitmap(raw, 0, 0, w, h); raw.recycle()
                    bytes = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray(); bmp.recycle()
                }
            } catch (_: Throwable) {}
            onPendingReady?.invoke(bytes); onPendingReady = null; cleanup(); stopSelf()
        }, 280)
        return START_NOT_STICKY
    }

    private fun cleanup() {
        try { vd?.release(); reader?.close(); proj?.stop() } catch (_: Throwable) {}
        vd = null; reader = null; proj = null
    }

    companion object {
        var onPendingReady: ((ByteArray?) -> Unit)? = null
        fun captureBestFrame(ctx: android.content.Context, prompt: String, cb: (ByteArray?) -> Unit) {
            val maxW = if (AppConfig(ctx).ecoMode) 640 else 900
            val acc = HumayraAccessibilityService.instance
            if (acc != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                acc.captureScreenJpeg(maxW) { b -> if (b != null) cb(b) else requestProjection(ctx, prompt, cb) }
            } else requestProjection(ctx, prompt, cb)
        }
        private fun requestProjection(ctx: android.content.Context, prompt: String, cb: (ByteArray?) -> Unit) {
            onPendingReady = cb
            ctx.startActivity(Intent(ctx, ScreenCaptureConsentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("prompt", prompt))
        }
    }
}
