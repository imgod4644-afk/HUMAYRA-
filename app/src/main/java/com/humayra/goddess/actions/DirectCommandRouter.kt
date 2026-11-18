package com.humayra.goddess.actions

import android.content.Context
import com.humayra.goddess.storage.MemoryStore
import org.json.JSONArray
import org.json.JSONObject

object DirectCommandRouter {
    fun matchQuick(ctx: Context, rawText: String): List<JSONObject> {
        val t = rawText.trim(); val low = t.lowercase()
        MemoryStore(ctx).getSkills().firstOrNull { low.contains(it.trigger.lowercase()) }?.let { sk ->
            return runCatching {
                val arr = JSONArray(sk.actionsJson); (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            }.getOrDefault(emptyList())
        }
        val list = mutableListOf<JSONObject>()
        when {
            low.contains("proton vpn") || (low.contains("vpn") && (low.contains("on") || low.contains("off") || low.contains("চালু") || low.contains("অন") || low.contains("বন্ধ"))) ->
                list.add(JSONObject().put("type", "vpn_toggle").put("app", if (low.contains("proton")) "Proton VPN" else "").put("state", if (low.contains("off") || low.contains("বন্ধ")) "off" else "on"))
            (low.contains("youtube") || low.contains("ইউটিউব")) && (low.contains("search") || low.contains("play") || low.contains("খুঁজ") || low.contains("চালা")) -> {
                val q = t.replace(Regex("(?i)(open|youtube|and|search|play|on|এ|খুলে|সার্চ|চালাও|করো)"), "").trim()
                if (q.isNotBlank()) list.add(JSONObject().put("type", "youtube_play").put("query", q))
            }
            low.contains("flashlight") || low.contains("torch") || low.contains("টর্চ") ->
                list.add(JSONObject().put("type", "flashlight").put("state", if (low.contains("off") || low.contains("নেভা") || low.contains("বন্ধ")) "off" else if (low.contains("on") || low.contains("জ্বালা")) "on" else "toggle"))
            (low.contains("wifi") || low.contains("ওয়াইফাই")) && (low.contains("on") || low.contains("off") || low.contains("অন") || low.contains("অফ") || low.contains("বন্ধ")) ->
                list.add(JSONObject().put("type", "wifi").put("state", if (low.contains("off") || low.contains("অফ") || low.contains("বন্ধ")) "off" else "on"))
            (low.contains("bluetooth") || low.contains("ব্লুটুথ")) && (low.contains("on") || low.contains("off") || low.contains("অন") || low.contains("অফ") || low.contains("বন্ধ")) ->
                list.add(JSONObject().put("type", "bluetooth").put("state", if (low.contains("off") || low.contains("অফ") || low.contains("বন্ধ")) "off" else "on"))
            low.startsWith("open ") && t.length < 36 ->
                list.add(JSONObject().put("type", "open_app").put("app", t.substring(5).trim()))
        }
        return list
    }
}
