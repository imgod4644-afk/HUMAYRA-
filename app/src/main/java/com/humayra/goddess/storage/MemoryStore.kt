package com.humayra.goddess.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ChatEntry(val role: String, val text: String, val ts: Long)
data class ReminderEntry(val id: Long, val triggerAtMs: Long, val message: String, val done: Boolean = false)

class MemoryStore(context: Context) {
    private val memFile = File(context.filesDir, "humayra_memory.json")
    private val histFile = File(context.filesDir, "humayra_history.json")
    private val remFile = File(context.filesDir, "humayra_reminders.json")

    @Synchronized
    fun getMemories(): List<String> {
        if (!memFile.exists()) return emptyList()
        val arr = JSONArray(memFile.readText())
        return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
    }

    @Synchronized
    fun addMemory(fact: String) {
        val clean = fact.trim(); if (clean.isEmpty()) return
        val list = (getMemories().filterNot { it.equals(clean, true) } + clean).takeLast(80)
        memFile.writeText(JSONArray().apply { list.forEach { put(it) } }.toString())
    }

    @Synchronized
    fun replaceAllMemories(lines: List<String>) {
        val arr = JSONArray().apply { lines.map { it.trim() }.filter { it.isNotEmpty() }.forEach { put(it) } }
        memFile.writeText(arr.toString())
    }
    @Synchronized
    fun getHistory(): List<ChatEntry> {
        if (!histFile.exists()) return emptyList()
        val arr = JSONArray(histFile.readText())
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ChatEntry(o.optString("role", "user"), o.optString("text"), o.optLong("ts"))
        }
    }

    @Synchronized
    fun appendHistory(role: String, text: String) {
        val list = (getHistory() + ChatEntry(role, text, System.currentTimeMillis())).takeLast(120)
        val arr = JSONArray()
        list.forEach { e -> arr.put(JSONObject().put("role", e.role).put("text", e.text).put("ts", e.ts)) }
        histFile.writeText(arr.toString())
    }

    @Synchronized
    fun clearHistory() = histFile.writeText("[]")
    @Synchronized
    fun getReminders(): List<ReminderEntry> {
        if (!remFile.exists()) return emptyList()
        val arr = JSONArray(remFile.readText())
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ReminderEntry(o.optLong("id"), o.optLong("triggerAtMs"), o.optString("message"), o.optBoolean("done"))
        }
    }

    @Synchronized
    fun saveReminders(items: List<ReminderEntry>) {
        val arr = JSONArray()
        items.takeLast(50).forEach { r ->
            arr.put(JSONObject().put("id", r.id).put("triggerAtMs", r.triggerAtMs).put("message", r.message).put("done", r.done))
        }
        remFile.writeText(arr.toString())
    }

    fun addReminder(delayMinutes: Int, message: String): ReminderEntry {
        val item = ReminderEntry(System.currentTimeMillis(), System.currentTimeMillis() + delayMinutes * 60_000L, message, false)
        saveReminders(getReminders() + item)
        return item
    }

    companion object {
        val recentEvents = kotlin.collections.ArrayDeque<String>(16)
        @Synchronized
        fun pushEvent(event: String) {
            if (recentEvents.size >= 12) recentEvents.removeFirst()
            recentEvents.addLast(event)
        }
    }
}
