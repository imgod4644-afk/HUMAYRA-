package com.humayra.goddess.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.widget.*
import com.humayra.goddess.ai.TelegramBridge
import com.humayra.goddess.security.*
import com.humayra.goddess.storage.*
import kotlinx.coroutines.*
import java.io.File

data class CyberShieldCallbacks(
    val onExportDrive: (String) -> Unit, val onImportDrive: (String) -> Unit,
    val onInspectApk: () -> Unit, val onApplyFlags: () -> Unit, val onReqCam: () -> Unit
)

object CyberShieldSheet {
    fun show(ctx: Context, cb: CyberShieldCallbacks) {
        val cfg = AppConfig(ctx)
        val score = 40 + (if (cfg.flagSecure) 15 else 0) + (if (cfg.strictCloudPrivacy) 15 else 0) +
            (if (cfg.piiRedaction) 10 else 0) + (if (cfg.appLockEnabled) 10 else 0) + (if (cfg.clipboardGuard && cfg.lockScreenPrivacy) 10 else 0)
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(42, 20, 42, 16) }
        fun sw(t: String, v: Boolean) = Switch(ctx).apply { text = t; isChecked = v; textSize = 13f }.also { root.addView(it) }
        fun btn(t: String, f: () -> Unit) = root.addView(Button(ctx).apply { text = t; textSize = 12f; setOnClickListener { f() } })
        root.addView(TextView(ctx).apply { text = "🛡️ CyberShield Score: $score/100 (AES-256 Active)"; textSize = 14f; setPadding(0, 0, 0, 12) })
        val sFlag = sw("FLAG_SECURE (স্ক্রিনশট/Recents ব্লক)", cfg.flagSecure)
        val sCloud = sw("Strict Cloud Privacy (স্ক্রিন/মেমোরি ক্লাউডে যাবে না)", cfg.strictCloudPrivacy)
        val sPii = sw("Auto PII Redactor (OTP/কার্ড/নম্বর মুছবে)", cfg.piiRedaction)
        val sLockNotif = sw("Lock-Screen Notification Hide", cfg.lockScreenPrivacy)
        val sAppLock = sw("App Lock (Fingerprint / Master PIN)", cfg.appLockEnabled)
        val sBio = sw("Biometric Unlock সাপোর্ট", cfg.biometricEnabled)
        val sLeave = sw("অ্যাপ ছাড়লেই Instant Lock", cfg.autoLockOnLeave)
        val sIntrud = sw("Intruder Selfie (ভুল PIN দিলে ছবি)", cfg.intruderSelfie)
        val sWipe = sw("৫ ভুল PIN-এ Auto-Wipe (সতর্ক)", cfg.autoWipeOn5Fails)
        val sClip = sw("Clipboard OTP Auto-Clear (৪০ সে.)", cfg.clipboardGuard)
        val sVpn = sw("VPN Drop Alert", cfg.vpnDropAlert)
        val sIncog = sw("Incognito Chat (কিছুই সেভ হবে না)", cfg.incognitoChat)
        val etBl = EditText(ctx).apply { hint = "Custom Blind Apps (pkg1, pkg2)"; setText(cfg.customBlacklistRaw); textSize = 12f }
        root.addView(etBl)
        btn("🔑 Set Master PIN & Decoy PIN") { showPinDialog(ctx, cfg) }
        btn("📸 View Intruder Selfies") { showIntruders(ctx) }
        btn("💾 Export .hvault (Google Drive / Local)") { askPass(ctx, "Set Backup Passphrase") { cb.onExportDrive(it) } }
        btn("📥 Restore .hvault Backup") { askPass(ctx, "Enter Backup Passphrase") { cb.onImportDrive(it) } }
        btn("✈️ Send Encrypted .hvault to Telegram") { askPass(ctx, "Passphrase for Telegram Backup") { p -> sendTgBackup(ctx, cfg, p) } }
        btn("🕵️ Scan Installed Apps Risk") { AlertDialog.Builder(ctx).setTitle("App Risk Report").setMessage(CyberShield.auditInstalledApps(ctx)).setPositiveButton("OK", null).show() }
        btn("📦 Inspect Any .apk Before Install") { cb.onInspectApk() }
        btn("💣 1-Tap Panic Wipe") { confirmPanic(ctx) }
        AlertDialog.Builder(ctx).setTitle("🛡️ CyberShield Security Dept.").setView(ScrollView(ctx).apply { addView(root) })
            .setPositiveButton("Save") { _, _ ->
                cfg.flagSecure = sFlag.isChecked; cfg.strictCloudPrivacy = sCloud.isChecked; cfg.piiRedaction = sPii.isChecked
                cfg.lockScreenPrivacy = sLockNotif.isChecked; cfg.appLockEnabled = sAppLock.isChecked; cfg.biometricEnabled = sBio.isChecked
                cfg.autoLockOnLeave = sLeave.isChecked; cfg.intruderSelfie = sIntrud.isChecked; cfg.autoWipeOn5Fails = sWipe.isChecked
                cfg.clipboardGuard = sClip.isChecked; cfg.vpnDropAlert = sVpn.isChecked; cfg.incognitoChat = sIncog.isChecked
                cfg.customBlacklistRaw = etBl.text.toString(); if (sIntrud.isChecked) cb.onReqCam(); cb.onApplyFlags()
            }.setNegativeButton("Close", null).show()
    }
    private fun showPinDialog(ctx: Context, cfg: AppConfig) {
        val l = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(44, 20, 44, 8) }
        val e1 = EditText(ctx).apply { hint = "Master Real PIN (4-8 digits)" }
        val e2 = EditText(ctx).apply { hint = "Decoy Fake PIN (optional)" }
        l.addView(e1); l.addView(e2)
        AlertDialog.Builder(ctx).setTitle("🔐 Set Vault PINs").setView(l).setPositiveButton("Save") { _, _ ->
            val p1 = e1.text.toString().trim(); val p2 = e2.text.toString().trim()
            if (p1.length >= 4) { cfg.realPinHash = CryptoVault.hashPin(p1); cfg.appLockEnabled = true }
            if (p2.length >= 4 && p2 != p1) cfg.decoyPinHash = CryptoVault.hashPin(p2)
            Toast.makeText(ctx, "✅ PIN Saved", Toast.LENGTH_SHORT).show()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun askPass(ctx: Context, title: String, onOk: (String) -> Unit) {
        val et = EditText(ctx).apply { hint = "Strong Passphrase" }
        AlertDialog.Builder(ctx).setTitle(title).setView(et).setPositiveButton("Continue") { _, _ ->
            val p = et.text.toString(); if (p.length >= 4) onOk(p) else Toast.makeText(ctx, "কমপক্ষে ৪ অক্ষর দিন", Toast.LENGTH_SHORT).show()
        }.setNegativeButton("Cancel", null).show()
    }
    private fun showIntruders(ctx: Context) {
        val dir = File(ctx.filesDir, "intruders"); val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (files.isEmpty()) { Toast.makeText(ctx, "কোনো ইন্ট্রুডার ছবি নেই।", Toast.LENGTH_SHORT).show(); return }
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        files.take(6).forEach { f ->
            box.addView(TextView(ctx).apply { text = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(f.lastModified())); textSize = 12f })
            box.addView(ImageView(ctx).apply { setImageBitmap(BitmapFactory.decodeFile(f.absolutePath)); adjustViewBounds = true; setPadding(0, 4, 0, 16) })
        }
        AlertDialog.Builder(ctx).setTitle("📸 Intruder Attempts (${files.size})").setView(ScrollView(ctx).apply { addView(box) })
            .setNeutralButton("Clear") { _, _ -> dir.deleteRecursively() }.setPositiveButton("OK", null).show()
    }

    private fun sendTgBackup(ctx: Context, cfg: AppConfig, pass: String) {
        if (cfg.telegramToken.isBlank() || cfg.telegramChatId.isBlank()) { Toast.makeText(ctx, "আগে Telegram Token & /start সেট করুন", Toast.LENGTH_SHORT).show(); return }
        val enc = CryptoVault.encryptWithPassphrase(MemoryStore(ctx).exportBundle(), pass)
        val tmp = File(ctx.cacheDir, "humayra_backup.hvault").apply { writeText(enc) }
        CoroutineScope(Dispatchers.IO).launch {
            val ok = TelegramBridge.sendBackupFile(cfg.telegramToken, cfg.telegramChatId, tmp, "🔐 HUMAYRA Zero-Knowledge Encrypted Backup")
            tmp.delete()
            withContext(Dispatchers.Main) { Toast.makeText(ctx, if (ok) "✅ Telegram-এ ব্যাকআপ পাঠানো হয়েছে!" else "❌ Telegram ব্যাকআপ ব্যর্থ", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun confirmPanic(ctx: Context) {
        val cb = CheckBox(ctx).apply { text = "API Keys-সহ সব মুছে ফেলুন" }
        AlertDialog.Builder(ctx).setTitle("💣 Panic Wipe?").setMessage("সব চ্যাট, মেমোরি, ভল্ট ও ছবি স্থায়ীভাবে মুছে যাবে!").setView(cb)
            .setPositiveButton("WIPE NOW") { _, _ -> MemoryStore(ctx).panicWipeAll(cb.isChecked); Toast.makeText(ctx, "🧹 Wiped!", Toast.LENGTH_SHORT).show() }
            .setNegativeButton("Cancel", null).show()
    }
}
