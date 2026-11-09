package com.personal.assistant

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

data class Task(
    val id: Long,
    val title: String,
    val priority: Int,
    val done: Boolean,
    val createdAt: Long,
    val due: Long
)

data class Note(val id: Long, val title: String, val body: String, val createdAt: Long)
data class Reminder(val id: Long, val text: String, val timeMillis: Long, val repeatDaily: Boolean)
data class MemoryItem(val id: Long, val text: String)
data class Msg(val fromUser: Boolean, val text: String)

/**
 * Saara data phone ke andar (private SharedPreferences) JSON mein rehta hai.
 */
class Store(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("assistant_data", Context.MODE_PRIVATE)

    private var lastId = 0L

    val tasks = mutableStateListOf<Task>()
    val notes = mutableStateListOf<Note>()
    val reminders = mutableStateListOf<Reminder>()
    val memories = mutableStateListOf<MemoryItem>()
    val chat = mutableStateListOf<Msg>()

    var apiKey by mutableStateOf(prefs.getString("api_key", "") ?: "")
        private set
    var model by mutableStateOf(prefs.getString("model", "gemini-2.5-flash") ?: "gemini-2.5-flash")
        private set
    var speak by mutableStateOf(prefs.getBoolean("speak", false))
        private set
    var themeMode by mutableStateOf(prefs.getInt("theme", 0))
        private set

    var pendingInput by mutableStateOf<String?>(null)
    var pendingMemory by mutableStateOf<String?>(null)

    init {
        load()
    }

    private fun newId(): Long {
        var id = maxOf(System.currentTimeMillis() and 0x7FFFFFFFL, lastId + 1)
        while (reminders.any { it.id == id }) id++
        lastId = id
        return id
    }

    // ---------- Tasks ----------
    fun addTask(title: String, priority: Int, due: Long) {
        tasks.add(0, Task(newId(), title, priority, false, System.currentTimeMillis(), due))
        saveTasks()
    }

    fun updateTask(t: Task) {
        val i = tasks.indexOfFirst { it.id == t.id }
        if (i >= 0) {
            tasks[i] = t
            saveTasks()
        }
    }

    fun deleteTask(id: Long) {
        tasks.removeAll { it.id == id }
        saveTasks()
    }

    // ---------- Notes ----------
    fun addNote(title: String, body: String) {
        notes.add(0, Note(newId(), title, body, System.currentTimeMillis()))
        saveNotes()
    }

    fun updateNote(n: Note) {
        val i = notes.indexOfFirst { it.id == n.id }
        if (i >= 0) {
            notes[i] = n
            saveNotes()
        }
    }

    fun deleteNote(id: Long) {
        notes.removeAll { it.id == id }
        saveNotes()
    }

    // ---------- Reminders ----------
    fun addReminder(text: String, time: Long, daily: Boolean): Reminder {
        val r = Reminder(newId(), text, time, daily)
        reminders.add(r)
        saveReminders()
        return r
    }

    fun updateReminder(r: Reminder) {
        val i = reminders.indexOfFirst { it.id == r.id }
        if (i >= 0) {
            reminders[i] = r
            saveReminders()
        }
    }

    fun removeReminder(id: Long) {
        reminders.removeAll { it.id == id }
        saveReminders()
    }

    // ---------- Memory ----------
    fun addMemory(text: String) {
        memories.add(0, MemoryItem(newId(), text))
        saveMemories()
    }

    fun updateMemory(id: Long, text: String) {
        val i = memories.indexOfFirst { it.id == id }
        if (i >= 0) {
            memories[i] = MemoryItem(id, text)
            saveMemories()
        }
    }

    fun deleteMemory(id: Long) {
        memories.removeAll { it.id == id }
        saveMemories()
    }

    // ---------- Chat ----------
    fun addChat(m: Msg) {
        chat.add(m)
        while (chat.size > 80) chat.removeAt(0)
        saveChat()
    }

    fun clearChat() {
        chat.clear()
        saveChat()
    }

    // ---------- Settings ----------
    fun saveApiKey(v: String) {
        apiKey = v.trim()
        prefs.edit().putString("api_key", apiKey).apply()
    }

    fun saveModel(v: String) {
        val m = v.trim().ifBlank { "gemini-2.5-flash" }
        model = m
        prefs.edit().putString("model", m).apply()
    }
    @JvmName("updateSpeak")
    fun setSpeak(v: Boolean) {
        speak = v
        prefs.edit().putBoolean("speak", v).apply()
    }
 @JvmName("updateThemeMode")
    fun setThemeMode(v: Int) {
        themeMode = v
        prefs.edit().putInt("theme", v).apply()
    }

    // ---------- Load / Save ----------
    private fun <T> readArray(key: String, parse: (JSONObject) -> T): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { parse(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun load() {
        tasks.addAll(readArray("tasks") {
            Task(
                it.getLong("id"), it.getString("title"), it.getInt("priority"),
                it.getBoolean("done"), it.getLong("createdAt"), it.optLong("due", 0L)
            )
        })
        notes.addAll(readArray("notes") {
            Note(it.getLong("id"), it.getString("title"), it.getString("body"), it.getLong("createdAt"))
        })
        reminders.addAll(readArray("reminders") {
            Reminder(it.getLong("id"), it.getString("text"), it.getLong("time"), it.getBoolean("daily"))
        })
        memories.addAll(readArray("memories") {
            MemoryItem(it.getLong("id"), it.getString("text"))
        })
        chat.addAll(readArray("chat") {
            Msg(it.getBoolean("user"), it.getString("text"))
        })
    }

    private fun saveArray(key: String, arr: JSONArray) {
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun saveTasks() {
        val a = JSONArray()
        tasks.forEach {
            a.put(
                JSONObject().put("id", it.id).put("title", it.title).put("priority", it.priority)
                    .put("done", it.done).put("createdAt", it.createdAt).put("due", it.due)
            )
        }
        saveArray("tasks", a)
    }

    private fun saveNotes() {
        val a = JSONArray()
        notes.forEach {
            a.put(
                JSONObject().put("id", it.id).put("title", it.title).put("body", it.body)
                    .put("createdAt", it.createdAt)
            )
        }
        saveArray("notes", a)
    }

    private fun saveReminders() {
        val a = JSONArray()
        reminders.forEach {
            a.put(
                JSONObject().put("id", it.id).put("text", it.text).put("time", it.timeMillis)
                    .put("daily", it.repeatDaily)
            )
        }
        saveArray("reminders", a)
    }

    private fun saveMemories() {
        val a = JSONArray()
        memories.forEach { a.put(JSONObject().put("id", it.id).put("text", it.text)) }
        saveArray("memories", a)
    }

    private fun saveChat() {
        val a = JSONArray()
        chat.forEach { a.put(JSONObject().put("user", it.fromUser).put("text", it.text)) }
        saveArray("chat", a)
    }
}
