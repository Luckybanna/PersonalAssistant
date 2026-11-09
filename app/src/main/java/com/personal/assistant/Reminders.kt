package com.personal.assistant

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

object ReminderScheduler {

    const val CHANNEL_ID = "reminders"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun pending(context: Context, id: Long, text: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra("id", id)
            .putExtra("text", text)
        return PendingIntent.getBroadcast(
            context, id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(context: Context, r: Reminder) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.timeMillis, pending(context, r.id, r.text))
    }

    fun cancel(context: Context, id: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pending(context, id, ""))
    }

    fun add(context: Context, store: Store, text: String, time: Long, daily: Boolean) {
        val r = store.addReminder(text, time, daily)
        schedule(context, r)
    }

    fun remove(context: Context, store: Store, id: Long) {
        cancel(context, id)
        store.removeReminder(id)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", 0L)
        val text = intent.getStringExtra("text") ?: "Reminder"

        ReminderScheduler.ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Reminder")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            context.getSystemService(NotificationManager::class.java).notify(id.toInt(), notification)
        } catch (e: Exception) {
            // notification permission na ho to chup-chaap skip
        }

        val store = (context.applicationContext as AssistantApp).store
        val r = store.reminders.firstOrNull { it.id == id } ?: return
        if (r.repeatDaily) {
            var next = r.timeMillis
            while (next <= System.currentTimeMillis()) next += 86_400_000L
            val updated = r.copy(timeMillis = next)
            store.updateReminder(updated)
            ReminderScheduler.schedule(context, updated)
        } else {
            store.removeReminder(id)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val store = (context.applicationContext as AssistantApp).store
        val now = System.currentTimeMillis()
        store.reminders.toList().forEach { r ->
            if (r.timeMillis > now) {
                ReminderScheduler.schedule(context, r)
            } else if (r.repeatDaily) {
                var next = r.timeMillis
                while (next <= now) next += 86_400_000L
                val updated = r.copy(timeMillis = next)
                store.updateReminder(updated)
                ReminderScheduler.schedule(context, updated)
            } else {
                store.removeReminder(r.id)
            }
        }
    }
}