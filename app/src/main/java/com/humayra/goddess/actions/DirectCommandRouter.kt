package com.humayra.goddess.actions

import android.content.Context
import com.humayra.goddess.storage.MemoryStore
import org.json.*

object DirectCommandRouter {
    fun matchQuick(ctx: Context, rawText: String): List<JSONObject> {
        val t = rawText.trim(); val low = t.lowercase()
        MemoryStore(ctx).getSkills().firstOrNull { low.contains(it.trigger.lowercase()) }?.let { sk ->
            return runCatching { val arr = JSONArray(sk.actionsJson); (0 until arr.length()).mapNotNull { arr.optJSONObject(it) } }.getOrDefault(emptyList())
        }
        fun dc(s: String, v: String) = JSONObject().put("type", "device_control").put("setting", s).put("value", v)
        val isOff = low.contains("off") || low.contains("disable") || low.contains("বন্ধ") || low.contains("অফ") || low.contains("band")
        val isOn = low.contains("on") || low.contains("enable") || low.contains("চালু") || low.contains("অন") || low.contains("khol")
        val st = if (isOff) "off" else "on"
        val list = mutableListOf<JSONObject>()
        if (isOn || isOff) when {
            low.contains("mobile data") || low.contains("cellular") || low.contains("ডাটা") || low.contains("নেট") -> list.add(dc("mobile_data", st))
            low.contains("dark mode") || low.contains("ডার্ক") -> list.add(dc("dark_mode", st))
            low.contains("dnd") || low.contains("do not disturb") -> list.add(dc("dnd", st))
            low.contains("auto rotate") || low.contains("rotation") -> list.add(dc("auto_rotate", st))
            low.contains("battery saver") || low.contains("পাওয়ার সেভ") -> list.add(dc("battery_saver", st))
            low.contains("airplane") || low.contains("flight mode") -> list.add(dc("airplane", st))
            low.contains("hotspot") || low.contains("হটস্পট") -> list.add(dc("hotspot", st))
            low.contains("wifi") || low.contains("ওয়াইফাই") -> list.add(JSONObject().put("type", "wifi").put("state", st))
            low.contains("bluetooth") || low.contains("ব্লুটুথ") -> list.add(JSONObject().put("type", "bluetooth").put("state", st))
            low.contains("vpn") -> list.add(JSONObject().put("type", "vpn_toggle").put("app", if (low.contains("proton")) "Proton VPN" else "").put("state", st))
        }
        if (list.isEmpty()) when {
            low.contains("flashlight") || low.contains("torch") || low.contains("টর্চ") ->
                list.add(JSONObject().put("type", "flashlight").put("state", if (isOff) "off" else if (isOn) "on" else "toggle"))
            low.startsWith("open ") && t.length < 36 ->
                list.add(JSONObject().put("type", "open_app").put("app", t.substring(5).trim()))
            (low.contains("firefox") || low.contains("chrome") || low.contains("browser") || low.contains("focus")) -> { val br = if (low.contains("firefox") || low.contains("focus")) "firefox" else if (low.contains("chrome")) "chrome" else "default"; val q = if (low.contains("youtube")) "https://youtube.com" else t; list.add(JSONObject().put("type", "browser_search").put("browser", br).put("query", q)) }
            (low.contains("youtube") || low.contains("ইউটিউব")) && !low.contains("firefox") && !low.contains("chrome") && (low.contains("search") || low.contains("play") || low.contains("খুঁজ") || low.contains("চালা")) ->
                t.replace(Regex("(?i)(open|youtube|and|search|play|on|এ|খুলে|সার্চ|চালাও|করো)"), "").trim().takeIf { it.isNotBlank() }?.let { list.add(JSONObject().put("type", "youtube_play").put("query", it)) }
        }
        return list
    }
}
