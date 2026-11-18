package com.humayra.goddess.service

import android.content.*
import android.content.pm.ApplicationInfo
import android.os.Build
import com.humayra.goddess.storage.*

class PackageAddedReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED || intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
        val pkg = intent.data?.schemeSpecificPart ?: return
        val pm = ctx.packageManager
        val ai = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return
        val lbl = pm.getApplicationLabel(ai).toString()
        val isGame = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ai.category == ApplicationInfo.CATEGORY_GAME
        val store = AppPresetStore(ctx)
        if (store.find(pkg) == null) {
            store.upsert(AppPreset(pkg, lbl, hideNotifs = isGame, ecoMode = isGame, autoOverlay = isGame))
            MemoryStore.pushEvent("New app installed: $lbl ($pkg) [Game=$isGame]")
        }
    }
}
