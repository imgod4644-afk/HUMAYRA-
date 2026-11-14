package com.humayra.goddess

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class HumayraApp : Application() {
    companion object {
        const val CH_CARE = "humayra_care_service"
        const val CH_ALERTS = "humayra_proactive_alerts"
        const val CH_WHATSAPP = "humayra_whatsapp_drafts"
        const val CH_CAPTURE = "humayra_screen_watcher"
        @Volatile lateinit var instance: HumayraApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val nm = getSystemService(NotificationManager::class.java)
        listOf(
            NotificationChannel(CH_CARE, "HUMAYRA 24/7 Service", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CH_ALERTS, "HUMAYRA Care & Alerts", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CH_WHATSAPP, "HUMAYRA WhatsApp Replies", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CH_CAPTURE, "HUMAYRA Screen Watcher", NotificationManager.IMPORTANCE_LOW)
        ).forEach { nm.createNotificationChannel(it) }
    }
}
