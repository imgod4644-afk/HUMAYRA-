package com.humayra.goddess.storage

import android.content.Context
import com.humayra.goddess.security.CryptoVault
import org.json.*

data class AvatarProfile(val name: String, val style: String, val path: String)

class AppConfig(context: Context) {
    private val prefs = context.getSharedPreferences("humayra_prefs", Context.MODE_PRIVATE)
    private fun s(k: String, d: String) = prefs.getString(k, d) ?: d
    private fun ps(k: String, v: String) = prefs.edit().putString(k, v).apply()
    private fun es(k: String, d: String) = CryptoVault.decrypt(s(k, d))
    private fun pes(k: String, v: String) = ps(k, if (v.isBlank()) "" else CryptoVault.encrypt(v))
    private fun b(k: String, d: Boolean) = prefs.getBoolean(k, d)
    private fun pb(k: String, v: Boolean) = prefs.edit().putBoolean(k, v).apply()
    var masterEnabled: Boolean get() = b("master_enabled", true); set(v) = pb("master_enabled", v)
    var companionName: String get() = s("companion_name", "HUMAYRA"); set(v) = ps("companion_name", v.ifBlank { "HUMAYRA" })
    var userName: String get() = s("user_name", "Boss"); set(v) = ps("user_name", v.ifBlank { "Boss" })
    var brainMode: String get() = s("brain_mode", "HYBRID"); set(v) = ps("brain_mode", v)
    var councilMode: Boolean get() = b("council_mode", false); set(v) = pb("council_mode", v)
    var uncensoredMode: Boolean get() = b("uncensored_mode", true); set(v) = pb("uncensored_mode", v)
    var ecoMode: Boolean get() = b("eco_mode", true); set(v) = pb("eco_mode", v)
    var customPrompt: String get() = s("custom_prompt", ""); set(v) = ps("custom_prompt", v)
    var vibePreset: String get() = s("vibe_preset", "HUMAN_COMPANION"); set(v) = ps("vibe_preset", v)
    var cloudProvider: String get() = s("cloud_provider", "OPENROUTER"); set(v) = ps("cloud_provider", v)
    var geminiApiKey: String get() = es("key_gemini", if (es("cloud_key", "").startsWith("AIza")) es("cloud_key", "") else ""); set(v) = pes("key_gemini", v)
    var geminiModel: String get() = s("mod_gemini", "gemini-2.5-flash"); set(v) = ps("mod_gemini", v)
    var openrouterApiKey: String get() = es("key_or", if (es("cloud_key", "").startsWith("sk-or-")) es("cloud_key", "") else ""); set(v) = pes("key_or", v)
    var openrouterModel: String get() = s("mod_or", "openrouter/free"); set(v) = ps("mod_or", v)
    var groqApiKey: String get() = es("key_groq", if (es("cloud_key", "").startsWith("gsk_")) es("cloud_key", "") else ""); set(v) = pes("key_groq", v)
    var groqModel: String get() = s("mod_groq", "llama-3.3-70b-versatile"); set(v) = ps("mod_groq", v)
    var openaiApiKey: String get() = es("key_openai", ""); set(v) = pes("key_openai", v)
    var openaiModel: String get() = s("mod_openai", "gpt-4o-mini"); set(v) = ps("mod_openai", v)
    var cloudBaseUrl: String get() = s("cloud_url", "https://openrouter.ai/api/v1/chat/completions"); set(v) = ps("cloud_url", v)
    var cloudApiKey: String
        get() = when (cloudProvider.uppercase()) { "GEMINI" -> geminiApiKey; "GROQ" -> groqApiKey; "OPENAI" -> openaiApiKey; else -> openrouterApiKey }.ifBlank { es("cloud_key", "") }
        set(v) { pes("cloud_key", v); when (cloudProvider.uppercase()) { "GEMINI" -> geminiApiKey = v; "GROQ" -> groqApiKey = v; "OPENAI" -> openaiApiKey = v; else -> openrouterApiKey = v } }
    var cloudModel: String
        get() = when (cloudProvider.uppercase()) { "GEMINI" -> geminiModel; "GROQ" -> groqModel; "OPENAI" -> openaiModel; else -> openrouterModel }
        set(v) { when (cloudProvider.uppercase()) { "GEMINI" -> geminiModel = v; "GROQ" -> groqModel = v; "OPENAI" -> openaiModel = v; else -> openrouterModel = v } }
    var localTextModelPath: String get() = s("local_text_model", ""); set(v) = ps("local_text_model", v)
    var localVisionModelPath: String get() = s("local_vision_model", ""); set(v) = ps("local_vision_model", v)
    var localVisionMmprojPath: String get() = s("local_vision_mmproj", ""); set(v) = ps("local_vision_mmproj", v)
    var activeAvatarPath: String get() = s("active_avatar_path", ""); set(v) = ps("active_avatar_path", v)
    var stageWallpaperPath: String get() = s("stage_wallpaper", ""); set(v) = ps("stage_wallpaper", v)
    var stageBgColor: String get() = s("stage_bg", "#000000"); set(v) = ps("stage_bg", v)
    var stageAccentColor: String get() = s("stage_accent", "#60A5FA"); set(v) = ps("stage_accent", v)
    var uiTheme: String get() = s("ui_theme", "ECLIPSE"); set(v) = ps("ui_theme", v)
    var uiTab: Int get() = prefs.getInt("ui_tab", 0); set(v) = prefs.edit().putInt("ui_tab", v).apply()
    var readAloud: Boolean get() = b("read_aloud", true); set(v) = pb("read_aloud", v)
    var handsFreeLoop: Boolean get() = b("hands_free", false); set(v) = pb("hands_free", v)
    var ttsPitch: Float get() = prefs.getFloat("tts_pitch", 1.05f); set(v) = prefs.edit().putFloat("tts_pitch", v).apply()
    var ttsSpeed: Float get() = prefs.getFloat("tts_speed", 1.02f); set(v) = prefs.edit().putFloat("tts_speed", v).apply()
    var proactiveCare: Boolean get() = b("proactive_care", true); set(v) = pb("proactive_care", v)
    var whatsappAssistant: Boolean get() = b("wa_assistant", true); set(v) = pb("wa_assistant", v)
    var useShizuku: Boolean get() = b("use_shizuku", true); set(v) = pb("use_shizuku", v)
    var savedWifiSsid: String get() = s("saved_wifi_ssid", ""); set(v) = ps("saved_wifi_ssid", v)
    var savedWifiPass: String get() = es("saved_wifi_pass", ""); set(v) = pes("saved_wifi_pass", v)
    var lastCareAlertMs: Long get() = prefs.getLong("last_care_alert_ms", 0L); set(v) = prefs.edit().putLong("last_care_alert_ms", v).apply()
    var flagSecure: Boolean get() = b("flag_secure", false); set(v) = pb("flag_secure", v)
    var strictCloudPrivacy: Boolean get() = b("strict_cloud", true); set(v) = pb("strict_cloud", v)
    var piiRedaction: Boolean get() = b("pii_redact", true); set(v) = pb("pii_redact", v)
    var lockScreenPrivacy: Boolean get() = b("lock_notif_priv", true); set(v) = pb("lock_notif_priv", v)
    var appLockEnabled: Boolean get() = b("app_lock", false); set(v) = pb("app_lock", v)
    var biometricEnabled: Boolean get() = b("bio_lock", true); set(v) = pb("bio_lock", v)
    var realPinHash: String get() = s("real_pin_hash", ""); set(v) = ps("real_pin_hash", v)
    var decoyPinHash: String get() = s("decoy_pin_hash", ""); set(v) = ps("decoy_pin_hash", v)
    var decoyActive: Boolean get() = b("decoy_active", false); set(v) = pb("decoy_active", v)
    var failedAttempts: Int get() = prefs.getInt("failed_attempts", 0); set(v) = prefs.edit().putInt("failed_attempts", v).apply()
    var intruderSelfie: Boolean get() = b("intruder_selfie", false); set(v) = pb("intruder_selfie", v)
    var autoWipeOn5Fails: Boolean get() = b("auto_wipe", false); set(v) = pb("auto_wipe", v)
    var autoLockOnLeave: Boolean get() = b("auto_lock_leave", false); set(v) = pb("auto_lock_leave", v)
    var clipboardGuard: Boolean get() = b("clip_guard", true); set(v) = pb("clip_guard", v)
    var vpnDropAlert: Boolean get() = b("vpn_drop_alert", true); set(v) = pb("vpn_drop_alert", v)
    var incognitoChat: Boolean get() = b("incognito_chat", false); set(v) = pb("incognito_chat", v)
    var customBlacklistRaw: String get() = s("custom_blacklist", ""); set(v) = ps("custom_blacklist", v)
    var telegramToken: String get() = es("tg_token", ""); set(v) = pes("tg_token", v)
    var telegramChatId: String get() = s("tg_chat_id", ""); set(v) = ps("tg_chat_id", v)
    var telegramHumanEnabled: Boolean get() = b("tg_enabled", false); set(v) = pb("tg_enabled", v)
    fun blacklistSet() = customBlacklistRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    fun getAvatars(): List<AvatarProfile> { val arr = JSONArray(s("saved_avatars", "[]")); return (0 until arr.length()).map { i -> val o = arr.getJSONObject(i); AvatarProfile(o.optString("name"), o.optString("style"), o.optString("path")) } }
    fun addAvatar(p: AvatarProfile) { val list = getAvatars().filterNot { it.path == p.path } + p; ps("saved_avatars", JSONArray().apply { list.forEach { put(JSONObject().put("name", it.name).put("style", it.style).put("path", it.path)) } }.toString()) }
    fun clearSecrets() = prefs.edit().clear().apply()
}
