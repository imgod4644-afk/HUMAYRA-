package com.humayra.goddess.security

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.*
import androidx.core.content.ContextCompat
import java.io.File

object IntruderCamera {
    @SuppressLint("MissingPermission")
    fun captureSilent(ctx: Context) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val camId = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT } ?: return
        val ht = HandlerThread("IntruderCam").apply { start() }; val h = Handler(ht.looper)
        val ir = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1)
        var dev: CameraDevice? = null
        fun cleanup() { runCatching { dev?.close(); ir.close(); ht.quitSafely() } }
        ir.setOnImageAvailableListener({ r ->
            r.acquireLatestImage()?.use { img ->
                val buf = img.planes[0].buffer; val b = ByteArray(buf.remaining()).also { buf.get(it) }
                val dir = File(ctx.filesDir, "intruders").apply { mkdirs() }
                File(dir, "intruder_${System.currentTimeMillis()}.jpg").writeBytes(b)
                dir.listFiles()?.sortedBy { it.lastModified() }?.dropLast(15)?.forEach { it.delete() }
            }; cleanup()
        }, h)
        cm.openCamera(camId, object : CameraDevice.StateCallback() {
            override fun onOpened(d: CameraDevice) {
                dev = d
                @Suppress("DEPRECATION")
                d.createCaptureSession(listOf(ir.surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        val req = d.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply { addTarget(ir.surface) }
                        s.capture(req.build(), null, h)
                    }
                    override fun onConfigureFailed(s: CameraCaptureSession) = cleanup()
                }, h)
            }
            override fun onDisconnected(d: CameraDevice) = cleanup()
            override fun onError(d: CameraDevice, err: Int) = cleanup()
        }, h)
    }
}
