package com.humayra.goddess.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.humayra.goddess.service.ScreenCaptureService

class ScreenCaptureConsentActivity : ComponentActivity() {
    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            val i = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("code", res.resultCode)
                putExtra("data", res.data)
                putExtra("prompt", intent.getStringExtra("prompt") ?: "এই স্ক্রিনে কী চলছে বলো")
            }
            ContextCompat.startForegroundService(this, i)
        } else ScreenCaptureService.onPendingReady?.invoke(null)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        launcher.launch(mpm.createScreenCaptureIntent())
    }
}
