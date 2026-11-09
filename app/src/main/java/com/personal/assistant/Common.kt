package com.personal.assistant

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val priorityLabels = listOf("High", "Medium", "Low")

fun startOfDay(offsetDays: Int = 0): Long {
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    c.add(Calendar.DAY_OF_YEAR, offsetDays)
    return c.timeInMillis
}

fun formatTime(millis: Long): String =
    SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(millis))

fun formatDate(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))

fun dueText(due: Long): String = when {
    due == 0L -> ""
    due == startOfDay(0) -> " • Aaj"
    due == startOfDay(1) -> " • Kal"
    due < startOfDay(0) -> " • Overdue"
    else -> " • " + formatDate(due)
}

fun pickDate(context: Context, millis: Long, onPicked: (Long) -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    DatePickerDialog(
        context,
        { _, y, m, d ->
            val n = Calendar.getInstance().apply {
                timeInMillis = millis
                set(y, m, d)
            }
            onPicked(n.timeInMillis)
        },
        c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
    ).show()
}

fun pickTime(context: Context, millis: Long, onPicked: (Long) -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    TimePickerDialog(
        context,
        { _, h, min ->
            val n = Calendar.getInstance().apply {
                timeInMillis = millis
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, min)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            onPicked(n.timeInMillis)
        },
        c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false
    ).show()
}

@Composable
fun ScreenHeader(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun ChoiceRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, label ->
            if (i == selected) {
                Button(onClick = { onSelect(i) }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(label) }
            } else {
                OutlinedButton(onClick = { onSelect(i) }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(label) }
            }
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f))
                MicButton { text = (text + " " + it).trim() }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onSave(text.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun TaskDialog(initial: Task?, onSave: (String, Int, Long) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var priority by remember { mutableStateOf(initial?.priority ?: 2) }
    var due by remember { mutableStateOf(initial?.due ?: 0L) }
    val dueSelected = when (due) {
        0L -> 0
        startOfDay(0) -> 1
        startOfDay(1) -> 2
        else -> -1
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Naya task" else "Task edit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = title, onValueChange = { title = it },
                        label = { Text("Task") }, modifier = Modifier.weight(1f)
                    )
                    MicButton { title = (title + " " + it).trim() }
                }
                Text("Priority")
                ChoiceRow(priorityLabels, priority - 1) { priority = it + 1 }
                Text("Kab tak")
                ChoiceRow(listOf("Koi nahi", "Aaj", "Kal"), dueSelected) {
                    due = when (it) {
                        0 -> 0L
                        1 -> startOfDay(0)
                        else -> startOfDay(1)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (title.isNotBlank()) onSave(title.trim(), priority, due) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun NoteDialog(initial: Note?, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var body by remember { mutableStateOf(initial?.body ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Naya note" else "Note edit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = body, onValueChange = { body = it },
                        label = { Text("Note") }, minLines = 3, maxLines = 6,
                        modifier = Modifier.weight(1f)
                    )
                    MicButton { body = (body + " " + it).trim() }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = title.trim().ifBlank { body.trim().take(30) }
                if (t.isNotBlank()) onSave(t, body.trim())
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}