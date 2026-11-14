package com.humayra.goddess.service

import android.content.*
import androidx.core.content.ContextCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "humayra_care_periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CareCheckWorker>(15, TimeUnit.MINUTES).build()
        )
        try { ContextCompat.startForegroundService(ctx, Intent(ctx, CompanionCareService::class.java)) } catch (_: Throwable) {}
    }
}
