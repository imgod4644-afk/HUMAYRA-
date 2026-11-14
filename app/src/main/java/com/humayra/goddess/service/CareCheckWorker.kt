package com.humayra.goddess.service

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class CareCheckWorker(private val ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result { CompanionCareService.runCarePass(ctx); return Result.success() }
}
