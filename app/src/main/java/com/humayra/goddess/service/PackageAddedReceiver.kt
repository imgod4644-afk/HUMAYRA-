package com.humayra.goddess.service

import android.app.*
import android.content.*
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import com.humayra.goddess.*
import com.humayra.goddess.storage.*
import com.humayra.goddess.ui.MainActivity

class PackageAddedReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED || intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
        val pkg = intent.data?.schemeSpecificPart ?: return
        val pm = ctx.packageManager; val ai = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return
        val lbl = pm.getApplicationLabel(ai).toString()
        val isGame = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ai.category == ApplicationInfo.CATEGORY_GAME
        val store = AppPresetStore(ctx)
        if (store.find(pkg) == null && isGame) store.upsert(AppPreset(pkg, lbl, hideNotifs = true, ecoMode = true, autoOverlay = true))
        MemoryStore.pushEvent("New app installed: $lbl ($pkg)")
        val openI = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("preset_pkg", pkg).putExtra("preset_lbl", lbl)
        val pi = PendingIntent.getActivity(ctx, (pkg.hashCode() and 0x7FFF) + 700, openI, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, HumayraApp.CH_ALERTS).setSmallIcon(R.drawable.ic_humayra_emblem)
            .setContentTitle("🆕 New App: $lbl").setContentText("Tap to set custom Shizuku preset").setContentIntent(pi).setAutoCancel(true).build()
        try { (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify((pkg.hashCode() and 0x7FFF) + 700, n) } catch (_: SecurityException) {}
    }
}
