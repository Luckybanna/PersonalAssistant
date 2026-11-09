package com.personal.assistant

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GeminiClient {

    suspend fun chat(
        apiKey: String,
        model: String,
        system: String,
        history: List<Msg>
    ): Result<String> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val modelName = model.trim()
            if (!Regex("[A-Za-z0-9._-]+").matches(modelName)) {
                return@withContext Result.failure<String>(Exception("Model naam galat hai. Settings check karein."))
            }

            val contents = JSONArray()
            history.forEach { m ->
                contents.put(
                    JSONObject()
                        .put("role", if (m.fromUser) "user" else "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", m.text)))
                )
            }
            val body = JSONObject()
                .put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system)))
                )
                .put("contents", contents)

            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""

            if (code !in 200..299) {
                return@withContext Result.failure<String>(Exception(friendlyError(code, text)))
            }
            val answer = parseAnswer(text)
            if (answer.isNullOrBlank()) {
                Result.failure<String>(Exception("Jawab nahi mila (safety filter ho sakta hai). Dobara try karein."))
            } else {
                Result.success(answer)
            }
        } catch (e: UnknownHostException) {
            Result.failure<String>(Exception("Internet nahi hai. Net chalu karke dobara try karein."))
        } catch (e: SocketTimeoutException) {
            Result.failure<String>(Exception("Server ne der laga di (timeout). Dobara try karein."))
        } catch (e: Exception) {
            Result.failure<String>(Exception("Error: " + (e.message ?: "unknown")))
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseAnswer(json: String): String? {
        return try {
            val parts = JSONObject(json)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
            sb.toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun friendlyError(code: Int, body: String): String {
        val detail = try {
            JSONObject(body).getJSONObject("error").optString("message")
        } catch (e: Exception) {
            ""
        }
        val base = when (code) {
            400 -> "Request galat hai ya API key sahi nahi hai."
            401, 403 -> "API key sahi nahi hai ya permission nahi hai."
            404 -> "Model naam galat hai. Settings mein model naam check karein."
            429 -> "Free limit khatam ho gayi. Thodi der baad try karein."
            in 500..599 -> "Google server mein dikkat hai. Thodi der baad try karein."
            else -> "Error " + code
        }
        return if (detail.isBlank()) base else base + "\n(" + detail.take(150) + ")"
    }
}

/**
 * AI ke jawab mein se allowed tags nikaal kar, validate karke, app ke kaam karta hai.
 * Sirf TASK, NOTE, REMIND, MEMORY allowed hain. Koi aur cheez execute nahi hoti.
 */
object AiActions {

    private val tag = Regex("\\[\\[(TASK|NOTE|REMIND|MEMORY)\\s*:(.*?)\\]\\]", RegexOption.DOT_MATCHES_ALL)

    fun systemPrompt(store: Store): String {
        val now = SimpleDateFormat("yyyy-MM-dd EEEE HH:mm", Locale.US).format(Date())
        val pending = store.tasks.filter { !it.done }.take(15).joinToString("; ") { it.title }
            .ifBlank { "koi nahi" }
        val mem = store.memories.take(30).joinToString("; ") { it.text }.ifBlank { "koi nahi" }
        val rem = store.reminders.sortedBy { it.timeMillis }.take(10)
            .joinToString("; ") { it.text + " (" + formatTime(it.timeMillis) + ")" }
            .ifBlank { "koi nahi" }

        return """
Tum user ke personal assistant ho. Hindi aur Hinglish (Roman Hindi) mein chhote, saaf jawab do. User jis bhasha mein likhe, usi mein jawab do.
Abhi ka local time: $now
User ke pending tasks: $pending
User ke upcoming reminders: $rem
User ki saved memory: $mem

Agar user clearly koi kaam karwana chahe, jawab ke end mein zaroorat ke hisaab se ye tags alag-alag line par likho:
[[TASK: title | priority]]   (priority: 1=high, 2=medium, 3=low)
[[NOTE: title | body]]
[[REMIND: yyyy-MM-dd HH:mm | text | once ya daily]]
[[MEMORY: fact]]   (sirf jab user kahe yaad rakho)
Tags sirf tab likho jab user ne saaf kaha ho. Tag ke bahar normal jawab bhi likho.
Payment, paise, message bhejna jaise kaam tum nahi kar sakte, politely mana kar do.
Password, bank details ya OTP kabhi note ya memory mein save mat karo.
        """.trimIndent()
    }

    fun apply(context: Context, store: Store, reply: String): Pair<String, List<String>> {
        val notes = mutableListOf<String>()
        var count = 0
        for (m in tag.findAll(reply)) {
            if (count >= 5) break
            count++
            val parts = m.groupValues[2].split("|").map { it.trim() }
            val msg = when (m.groupValues[1]) {
                "TASK" -> doTask(store, parts)
                "NOTE" -> doNote(store, parts)
                "REMIND" -> doRemind(context, store, parts)
                "MEMORY" -> doMemory(store, parts)
                else -> null
            }
            if (msg != null) notes.add(msg)
        }
        return Pair(tag.replace(reply, "").trim(), notes)
    }

    private fun doTask(store: Store, p: List<String>): String? {
        val title = p.getOrNull(0).orEmpty().take(200)
        if (title.isBlank()) return null
        val prio = (p.getOrNull(1)?.toIntOrNull() ?: 2).coerceIn(1, 3)
        store.addTask(title, prio, 0L)
        return "Task add kiya: " + title
    }

    private fun doNote(store: Store, p: List<String>): String? {
        val title = p.getOrNull(0).orEmpty().take(100)
        if (title.isBlank()) return null
        val body = p.drop(1).joinToString(" | ").take(2000)
        store.addNote(title, body)
        return "Note save kiya: " + title
    }

    private fun doRemind(context: Context, store: Store, p: List<String>): String? {
        val text = p.getOrNull(1).orEmpty().take(200)
        if (text.isBlank()) return null
        val daily = p.getOrNull(2)?.lowercase() == "daily"
        val millis = try {
            val f = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            f.isLenient = false
            f.parse(p.getOrNull(0).orEmpty())?.time
        } catch (e: Exception) {
            null
        }
        val now = System.currentTimeMillis()
        if (millis == null) return "Reminder ka time samajh nahi aaya, Reminders tab se set kar lein."
        if (millis <= now) return "Reminder ka time beet chuka hai, naya time batayein."
        if (millis > now + 2L * 365 * 86_400_000L) return "Reminder bahut door ka hai, skip kiya."
        ReminderScheduler.add(context, store, text, millis, daily)
        return "Reminder set: " + text + " (" + formatTime(millis) + (if (daily) ", roz" else "") + ")"
    }

    private fun doMemory(store: Store, p: List<String>): String? {
        val fact = p.getOrNull(0).orEmpty().take(300)
        if (fact.isBlank()) return null
        store.pendingMemory = fact
        return "Memory save karne se pehle aapse puchunga."
    }
}