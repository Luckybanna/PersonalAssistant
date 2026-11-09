package com.personal.assistant

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat

// ======================= HOME =======================
@Composable
fun HomeScreen(store: Store, goTab: (String) -> Unit, open: (String) -> Unit) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 12 -> "Suprabhat"
        hour < 17 -> "Namaste"
        hour < 21 -> "Shubh sandhya"
        else -> "Shubh ratri"
    }
    val dateText = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date())
    var quick by remember { mutableStateOf("") }

    val pending = store.tasks.filter { !it.done }
        .sortedWith(compareBy<Task>({ it.priority }, { -it.createdAt }))
    val overdue = store.tasks.count { !it.done && it.due != 0L && it.due < startOfDay(0) }
    val upcoming = store.reminders.filter { it.timeMillis > System.currentTimeMillis() }
        .sortedBy { it.timeMillis }.take(3)

    fun sendQuick(text: String) {
        if (text.isNotBlank()) {
            store.pendingInput = text.trim()
            quick = ""
            goTab("chat")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(greeting + "!", style = MaterialTheme.typography.headlineMedium)
                Text(dateText, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { open("memory") }) { Text("Memory") }
                OutlinedButton(onClick = { open("settings") }) { Text("Settings") }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = quick, onValueChange = { quick = it },
                    placeholder = { Text("Kuch bhi poochiye ya kahiye...") },
                    modifier = Modifier.weight(1f), maxLines = 3
                )
                MicButton { sendQuick(it) }
                Button(onClick = { sendQuick(quick) }) { Text("Bhejo") }
            }
        }
        item {
            Button(
                onClick = {
                    store.pendingInput = "Mere pending tasks aur reminders dekhkar aaj ka ek practical plan banao."
                    goTab("chat")
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("AI se aaj ka plan banao") }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Aaj ke tasks", style = MaterialTheme.typography.titleMedium)
                    if (overdue > 0) {
                        Text("$overdue task overdue hai", color = MaterialTheme.colorScheme.error)
                    }
                    if (pending.isEmpty()) Text("Koi pending task nahi")
                    pending.take(5).forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = false, onCheckedChange = { store.updateTask(t.copy(done = true)) })
                            Text(t.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    TextButton(onClick = { goTab("tasks") }) { Text("Sab tasks dekhein") }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Aane wale reminders", style = MaterialTheme.typography.titleMedium)
                    if (upcoming.isEmpty()) Text("Koi reminder nahi")
                    upcoming.forEach { r ->
                        Text(r.text + "  •  " + formatTime(r.timeMillis))
                    }
                    TextButton(onClick = { goTab("reminders") }) { Text("Reminders kholein") }
                }
            }
        }
    }
}

// ======================= TASKS =======================
@Composable
fun TaskRow(task: Task, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val overdue = !task.done && task.due != 0L && task.due < startOfDay(0)
    val meta = priorityLabels[(task.priority - 1).coerceIn(0, 2)] + dueText(task.due)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.done, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null
                )
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
        }
    }
}

@Composable
fun TasksScreen(store: Store) {
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }
    val pending = store.tasks.filter { !it.done }
        .sortedWith(compareBy<Task>({ it.priority }, { -it.createdAt }))
    val done = store.tasks.filter { it.done }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Tasks") {
            Button(onClick = { showAdd = true }) { Text("+ Naya") }
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Text("Pending (${pending.size})", style = MaterialTheme.typography.titleMedium) }
            if (pending.isEmpty()) item { Text("Koi pending task nahi") }
            items(pending, key = { it.id }) { t ->
                TaskRow(
                    t,
                    onToggle = { store.updateTask(t.copy(done = true)) },
                    onEdit = { editing = t },
                    onDelete = { store.deleteTask(t.id) }
                )
            }
            item { Text("Completed (${done.size})", style = MaterialTheme.typography.titleMedium) }
            items(done, key = { it.id }) { t ->
                TaskRow(
                    t,
                    onToggle = { store.updateTask(t.copy(done = false)) },
                    onEdit = { editing = t },
                    onDelete = { store.deleteTask(t.id) }
                )
            }
        }
    }

    if (showAdd) {
        TaskDialog(
            initial = null,
            onSave = { title, p, d ->
                store.addTask(title, p, d)
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }
    editing?.let { e ->
        TaskDialog(
            initial = e,
            onSave = { title, p, d ->
                store.updateTask(e.copy(title = title, priority = p, due = d))
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

// ======================= NOTES =======================
@Composable
fun NotesScreen(store: Store) {
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Note?>(null) }
    var summary by remember { mutableStateOf<String?>(null) }
    var summarizing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val shown = store.notes.filter {
        query.isBlank() || it.title.contains(query, ignoreCase = true) || it.body.contains(query, ignoreCase = true)
    }

    fun summarize(n: Note) {
        if (store.apiKey.isBlank()) {
            summary = "Pehle Settings mein API key daalein."
            return
        }
        summarizing = true
        scope.launch {
            val r = GeminiClient.chat(
                store.apiKey, store.model,
                "Tum ek summarizer ho. Note ka chhota summary Hinglish mein 3-4 lines mein do.",
                listOf(Msg(true, n.title + "\n" + n.body))
            )
            summary = r.getOrElse { "⚠ " + (it.message ?: "Error") }
            summarizing = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Notes") {
            Button(onClick = { showAdd = true }) { Text("+ Naya") }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Notes mein search karein...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        if (summarizing) Text("Summary ban rahi hai...", modifier = Modifier.padding(16.dp))
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (shown.isEmpty()) item { Text("Koi note nahi mila") }
            items(shown, key = { it.id }) { n ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(n.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (n.body.isNotBlank()) {
                            Text(n.body, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { summarize(n) }, enabled = !summarizing) { Text("AI Summary") }
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = { editing = n }) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                                IconButton(onClick = { store.deleteNote(n.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        NoteDialog(
            initial = null,
            onSave = { t, b ->
                store.addNote(t, b)
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }
    editing?.let { e ->
        NoteDialog(
            initial = e,
            onSave = { t, b ->
                store.updateNote(e.copy(title = t, body = b))
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
    summary?.let { s ->
        AlertDialog(
            onDismissRequest = { summary = null },
            title = { Text("Summary") },
            text = { Text(s) },
            confirmButton = { TextButton(onClick = { summary = null }) { Text("Theek hai") } }
        )
    }
}

// ======================= REMINDERS =======================
@Composable
fun ReminderDialog(onSave: (String, Long, Boolean) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var millis by remember {
        mutableStateOf(
            Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        )
    }
    var daily by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Naya reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it },
                        label = { Text("Kya yaad dilana hai?") }, modifier = Modifier.weight(1f)
                    )
                    MicButton { text = (text + " " + it).trim() }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDate(context, millis) { millis = it } }) { Text(formatDate(millis)) }
                    OutlinedButton(onClick = { pickTime(context, millis) { millis = it } }) {
                        Text(SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(millis)))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Roz repeat karein", Modifier.weight(1f))
                    Switch(checked = daily, onCheckedChange = { daily = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isBlank()) {
                    Toast.makeText(context, "Reminder ka text likhein", Toast.LENGTH_SHORT).show()
                } else if (millis <= System.currentTimeMillis()) {
                    Toast.makeText(context, "Time aage ka rakhein", Toast.LENGTH_SHORT).show()
                } else {
                    onSave(text.trim(), millis, daily)
                }
            }) { Text("Set karo") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun RemindersScreen(store: Store) {
    val context = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
    val list = store.reminders.sortedBy { it.timeMillis }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Reminders") {
            Button(onClick = { showAdd = true }) { Text("+ Naya") }
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (list.isEmpty()) item { Text("Koi reminder nahi") }
            items(list, key = { it.id }) { r ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.text, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                formatTime(r.timeMillis) + if (r.repeatDaily) "  •  Roz" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { ReminderScheduler.remove(context, store, r.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        ReminderDialog(
            onSave = { t, m, d ->
                ReminderScheduler.add(context, store, t, m, d)
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }
}

// ======================= MEMORY =======================
@Composable
fun MemoryScreen(store: Store, onBack: () -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MemoryItem?>(null) }

    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) { Text("← Wapas") }
        ScreenHeader("Memory") {
            Button(onClick = { showAdd = true }) { Text("+ Naya") }
        }
        Text(
            "Yahan sirf wahi cheezein hain jo aapne approve ki hain. AI inhe jawab dete waqt use karta hai.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (store.memories.isEmpty()) item { Text("Abhi kuch save nahi hai") }
            items(store.memories, key = { it.id }) { m ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(m.text, Modifier.weight(1f))
                        IconButton(onClick = { editing = m }) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                        IconButton(onClick = { store.deleteMemory(m.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                    }
                }
            }
        }
    }

    if (showAdd) {
        TextInputDialog("Naya memory", "", onSave = {
            store.addMemory(it.take(300))
            showAdd = false
        }, onDismiss = { showAdd = false })
    }
    editing?.let { e ->
        TextInputDialog("Memory edit", e.text, onSave = {
            store.updateMemory(e.id, it.take(300))
            editing = null
        }, onDismiss = { editing = null })
    }
}

// ======================= SETTINGS =======================
@Composable
fun SettingsScreen(store: Store, onBack: () -> Unit) {
    val context = LocalContext.current
    var key by remember { mutableStateOf(store.apiKey) }
    var model by remember { mutableStateOf(store.model) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Wapas") }
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Text("Gemini API key (free). aistudio.google.com/apikey par banayein.")
        OutlinedTextField(
            value = key, onValueChange = { key = it },
            label = { Text("API key") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = model, onValueChange = { model = it },
            label = { Text("Model naam") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            store.saveApiKey(key)
            store.saveModel(model)
            Toast.makeText(context, "Save ho gaya", Toast.LENGTH_SHORT).show()
        }) { Text("Save") }

        Text("Theme")
        ChoiceRow(listOf("System", "Light", "Dark"), store.themeMode) { store.setThemeMode(it) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AI ka jawab bolkar sunao", Modifier.weight(1f))
            Switch(checked = store.speak, onCheckedChange = { store.setSpeak(it) })
        }

        Text(
            "Privacy: Tasks, notes aur memory sirf aapke phone mein rehte hain. " +
                "Jab aap AI se baat karte hain tab aapka message aur pending task titles Google Gemini ko jaate hain. " +
                "Free tier mein Google data product improve karne ke liye use kar sakta hai, isliye password, " +
                "bank details ya OTP kabhi na likhein. API key sirf is phone mein save hoti hai.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}