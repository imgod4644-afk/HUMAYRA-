package com.humayra.goddess.storage

import android.content.Context
import com.humayra.goddess.security.CryptoVault
import org.json.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class VaultNotif(val pkg: String, val app: String, val title: String, val body: String, val ts: Long)

class NotificationVault(ctx: Context) {
    private val file = File(ctx.filesDir, "humayra_notif_vault.json")

    @Synchronized fun getAll(): List<VaultNotif> {
        if (!file.exists()) return emptyList()
        val raw = CryptoVault.decrypt(file.readText()); if (raw.isBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).map { i -> val o = arr.getJSONObject(i); VaultNotif(o.optString("p"), o.optString("a"), o.optString("t"), o.optString("b"), o.optLong("ts")) }
    }
    @Synchronized fun add(n: VaultNotif) {
        val list = (getAll() + n).takeLast(120)
        val arr = JSONArray().apply { list.forEach { x -> put(JSONObject().put("p", x.pkg).put("a", x.app).put("t", x.title).put("b", x.body).put("ts", x.ts)) } }
        file.writeText(CryptoVault.encrypt(arr.toString()))
    }
    fun clear() { if (file.exists()) file.delete() }
    fun formattedDigest(): String {
        val list = getAll().takeLast(25).reversed(); if (list.isEmpty()) return "ভল্টে কোনো জমা নোটিফিকেশন নেই।"
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        return list.joinToString("\n\n") { "[${fmt.format(Date(it.ts))}] ${it.app} — ${it.title}\n${it.body}" }
    }
}
