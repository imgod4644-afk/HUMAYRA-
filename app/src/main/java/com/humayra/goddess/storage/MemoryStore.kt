package com.humayra.goddess.storage

import android.content.Context
import com.humayra.goddess.security.CryptoVault
import org.json.*
import java.io.File

data class ChatEntry(val role: String, val text: String, val ts: Long)
data class ReminderEntry(val id: Long, val triggerAtMs: Long, val message: String, val done: Boolean = false)
data class SkillEntry(val trigger: String, val actionsJson: String)

class MemoryStore(private val ctx: Context) {
    private val memFile = File(ctx.filesDir, "humayra_memory.json")
    private val histFile = File(ctx.filesDir, "humayra_history.json")
    private val remFile = File(ctx.filesDir, "humayra_reminders.json")
    private val skillFile = File(ctx.filesDir, "humayra_skills.json")
    private fun readArr(f: File): JSONArray {
        if (!f.exists()) return JSONArray()
        return runCatching { JSONArray(CryptoVault.decrypt(f.readText())) }.getOrDefault(JSONArray())
    }
    private fun writeArr(f: File, arr: JSONArray) = f.writeText(CryptoVault.encrypt(arr.toString()))
    @Synchronized fun getMemories() = readArr(memFile).let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } } }
    @Synchronized fun addMemory(fact: String) {
        val c = fact.trim(); if (c.isEmpty() || AppConfig(ctx).incognitoChat) return
        val list = (getMemories().filterNot { it.equals(c, true) } + c).takeLast(120)
        writeArr(memFile, JSONArray().apply { list.forEach { put(it) } })
    }
    @Synchronized fun replaceAllMemories(lines: List<String>) = writeArr(memFile, JSONArray().apply { lines.map { it.trim() }.filter { it.isNotEmpty() }.forEach { put(it) } })
    @Synchronized fun getHistory() = readArr(histFile).let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { ChatEntry(it.optString("role", "user"), it.optString("text"), it.optLong("ts")) } } }
    @Synchronized fun appendHistory(role: String, text: String) {
        if (AppConfig(ctx).incognitoChat) return
        val list = (getHistory() + ChatEntry(role, text, System.currentTimeMillis())).takeLast(150)
        writeArr(histFile, JSONArray().apply { list.forEach { put(JSONObject().put("role", it.role).put("text", it.text).put("ts", it.ts)) } })
    }
    @Synchronized fun clearHistory() = writeArr(histFile, JSONArray())
    @Synchronized fun getReminders() = readArr(remFile).let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { ReminderEntry(it.optLong("id"), it.optLong("triggerAtMs"), it.optString("message"), it.optBoolean("done")) } } }
    @Synchronized fun saveReminders(items: List<ReminderEntry>) = writeArr(remFile, JSONArray().apply { items.takeLast(50).forEach { put(JSONObject().put("id", it.id).put("triggerAtMs", it.triggerAtMs).put("message", it.message).put("done", it.done)) } })
    fun addReminder(delayMin: Int, msg: String) = ReminderEntry(System.currentTimeMillis(), System.currentTimeMillis() + delayMin * 60_000L, msg, false).also { saveReminders(getReminders() + it) }
    @Synchronized fun getSkills() = readArr(skillFile).let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { SkillEntry(it.optString("trigger"), it.optString("actions")) } } }
    @Synchronized fun saveSkill(tr: String, act: String) { val l = getSkills().filterNot { it.trigger.equals(tr, true) } + SkillEntry(tr.trim(), act); writeArr(skillFile, JSONArray().apply { l.takeLast(60).forEach { put(JSONObject().put("trigger", it.trigger).put("actions", it.actionsJson)) } }) }
    fun exportBundle(): String = JSONObject().put("mem", readArr(memFile)).put("skills", readArr(skillFile)).toString()
    fun importBundle(json: String) { val o = JSONObject(json); o.optJSONArray("mem")?.let { writeArr(memFile, it) }; o.optJSONArray("skills")?.let { writeArr(skillFile, it) } }
    fun panicWipeAll(wipeKeys: Boolean = false) {
        listOf(memFile, histFile, remFile, skillFile).forEach { if (it.exists()) it.delete() }
        NotificationVault(ctx).clear(); File(ctx.filesDir, "intruders").deleteRecursively(); if (wipeKeys) AppConfig(ctx).clearSecrets()
    }
    companion object { val recentEvents = kotlin.collections.ArrayDeque<String>(16); @Synchronized fun pushEvent(e: String) { if (recentEvents.size >= 15) recentEvents.removeFirst(); recentEvents.addLast(e) } }
}
