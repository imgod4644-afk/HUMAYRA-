package com.humayra.goddess.actions

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.humayra.goddess.HumayraApp
import com.humayra.goddess.R

class ReminderWorker(private val ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val msg = inputData.getString("msg") ?: "Reminder"
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = NotificationCompat.Builder(ctx, HumayraApp.CH_ALERTS)
            .setSmallIcon(R.drawable.ic_humayra_emblem)
            .setContentTitle("HUMAYRA Reminder")
            .setContentText(msg).setAutoCancel(true).build()
        try { nm.notify((System.currentTimeMillis() % 10000).toInt() + 2000, n) } catch (_: SecurityException) {}
        return Result.success()
    }
}
