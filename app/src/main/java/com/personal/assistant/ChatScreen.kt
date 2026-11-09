package com.personal.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(store: Store, speaker: Speaker) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || loading) return
        store.addChat(Msg(true, text))
        if (store.apiKey.isBlank()) {
            store.addChat(Msg(false, "⚠ Pehle Settings mein Gemini API key daalein."))
            return
        }
        loading = true
        scope.launch {
            val history = store.chat
                .filter { !it.text.startsWith("⚠") }
                .takeLast(14)
                .dropWhile { !it.fromUser }
            val result = GeminiClient.chat(store.apiKey, store.model, AiActions.systemPrompt(store), history)
            result.onSuccess { reply ->
                val (clean, notes) = AiActions.apply(context, store, reply)
                val shown = clean.ifBlank { "Ho gaya." }
                val extra = if (notes.isEmpty()) "" else "\n\n" + notes.joinToString("\n")
                store.addChat(Msg(false, shown + extra))
                if (store.speak) speaker.speak(clean)
            }.onFailure {
                store.addChat(Msg(false, "⚠ " + (it.message ?: "Error")))
            }
            loading = false
        }
    }

    LaunchedEffect(store.pendingInput) {
        val p = store.pendingInput
        if (p != null) {
            store.pendingInput = null
            send(p)
        }
    }

    LaunchedEffect(store.chat.size, loading) {
        val count = store.chat.size + (if (loading) 1 else 0)
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader("AI Chat") {
            TextButton(onClick = { speaker.stop() }) { Text("🔇 Stop") }
            TextButton(onClick = { store.clearChat() }) { Text("Clear") }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (store.chat.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Namaste! Mujhse Hindi ya Hinglish mein baat kijiye. Main task, note aur reminder bhi bana sakta hoon.")
                        OutlinedButton(onClick = { send("Mere pending tasks dekhkar aaj ka plan banao.") }) { Text("Aaj ka plan") }
                        OutlinedButton(onClick = { send("Mere pending tasks ka chhota summary do.") }) { Text("Tasks summary") }
                    }
                }
            }
            items(store.chat) { m ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (m.fromUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(m.text, modifier = Modifier.padding(12.dp).widthIn(max = 300.dp))
                    }
                }
            }
            if (loading) item { Text("Soch raha hoon...") }
        }

        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MicButton { send(it) }
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message likhein...") },
                maxLines = 4
            )
            Spacer(Modifier.width(6.dp))
            Button(onClick = {
                if (!loading && input.isNotBlank()) {
                    send(input)
                    input = ""
                }
            }) { Text("Bhejo") }
        }
    }

    store.pendingMemory?.let { fact ->
        AlertDialog(
            onDismissRequest = { store.pendingMemory = null },
            title = { Text("Ye yaad rakhun?") },
            text = { Text(fact) },
            confirmButton = {
                TextButton(onClick = {
                    store.addMemory(fact)
                    store.pendingMemory = null
                }) { Text("Haan, save karo") }
            },
            dismissButton = { TextButton(onClick = { store.pendingMemory = null }) { Text("Nahi") } }
        )
    }
}