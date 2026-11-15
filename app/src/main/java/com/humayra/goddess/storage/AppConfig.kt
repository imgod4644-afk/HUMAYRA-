package com.humayra.goddess.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AvatarProfile(val name: String, val style: String, val path: String)

class AppConfig(context: Context) {
    private val prefs = context.getSharedPreferences("humayra_prefs", Context.MODE_PRIVATE)
    private fun s(k: String, d: String) = prefs.getString(k, d) ?: d
    private fun ps(k: String, v: String) = prefs.edit().putString(k, v).apply()
    private fun b(k: String, d: Boolean) = prefs.getBoolean(k, d)
    private fun pb(k: String, v: Boolean) = prefs.edit().putBoolean(k, v).apply()

    var companionName: String get() = s("companion_name", "HUMAYRA"); set(v) = ps("companion_name", v.ifBlank { "HUMAYRA" })
    var userName: String get() = s("user_name", "Boss"); set(v) = ps("user_name", v.ifBlank { "Boss" })
    var brainMode: String get() = s("brain_mode", "HYBRID"); set(v) = ps("brain_mode", v)
    var uncensoredMode: Boolean get() = b("uncensored_mode", true); set(v) = pb("uncensored_mode", v)
    var ecoMode: Boolean get() = b("eco_mode", true); set(v) = pb("eco_mode", v)
    var customPrompt: String get() = s("custom_prompt", ""); set(v) = ps("custom_prompt", v)
    var localTextModelPath: String get() = s("local_text_model", ""); set(v) = ps("local_text_model", v)
    var localVisionModelPath: String get() = s("local_vision_model", ""); set(v) = ps("local_vision_model", v)
    var localVisionMmprojPath: String get() = s("local_vision_mmproj", ""); set(v) = ps("local_vision_mmproj", v)
    var cloudProvider: String get() = s("cloud_provider", "OPENROUTER"); set(v) = ps("cloud_provider", v)
    var cloudBaseUrl: String get() = s("cloud_url", "https://openrouter.ai/api/v1/chat/completions"); set(v) = ps("cloud_url", v)
    var cloudApiKey: String get() = s("cloud_key", ""); set(v) = ps("cloud_key", v)
    var cloudModel: String get() = s("cloud_model", "openrouter/free"); set(v) = ps("cloud_model", v)
    var activeAvatarPath: String get() = s("active_avatar_path", ""); set(v) = ps("active_avatar_path", v)
    var stageBgColor: String get() = s("stage_bg", "#0B0E14"); set(v) = ps("stage_bg", v)
    var stageAccentColor: String get() = s("stage_accent", "#7C9CFF"); set(v) = ps("stage_accent", v)
    var readAloud: Boolean get() = b("read_aloud", true); set(v) = pb("read_aloud", v)
    var ttsPitch: Float get() = prefs.getFloat("tts_pitch", 1.05f); set(v) = prefs.edit().putFloat("tts_pitch", v).apply()
    var ttsSpeed: Float get() = prefs.getFloat("tts_speed", 1.02f); set(v) = prefs.edit().putFloat("tts_speed", v).apply()
    var proactiveCare: Boolean get() = b("proactive_care", true); set(v) = pb("proactive_care", v)
    var whatsappAssistant: Boolean get() = b("wa_assistant", true); set(v) = pb("wa_assistant", v)
    var lastCareAlertMs: Long get() = prefs.getLong("last_care_alert_ms", 0L); set(v) = prefs.edit().putLong("last_care_alert_ms", v).apply()

    fun getAvatars(): List<AvatarProfile> {
        val arr = JSONArray(s("saved_avatars", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            AvatarProfile(o.optString("name"), o.optString("style"), o.optString("path"))
        }
    }

    fun addAvatar(profile: AvatarProfile) {
        val list = getAvatars().filterNot { it.path == profile.path } + profile
        val arr = JSONArray()
        list.forEach { p -> arr.put(JSONObject().put("name", p.name).put("style", p.style).put("path", p.path)) }
        ps("saved_avatars", arr.toString())
    }
}
