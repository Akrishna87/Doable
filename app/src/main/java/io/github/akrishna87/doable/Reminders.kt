package io.github.akrishna87.doable

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.akrishna87.doable.model.Data
import io.github.akrishna87.doable.model.Dates
import io.github.akrishna87.doable.model.Task
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Notifications for task reminders (each task can have several) and the optional morning summary.
 * Alarms are kept in step with the tasks every time they change.
 */
object Reminders {
    private const val CHANNEL = "reminders"
    private const val SUMMARY_CHANNEL = "summary"
    private const val PREFS = "reminders"
    private const val KEY_SCHEDULED = "scheduled2"
    private const val SUMMARY_ID = 7_000_001
    const val ACTION_REMIND = "io.github.akrishna87.doable.REMIND"
    const val ACTION_COMPLETE = "io.github.akrishna87.doable.COMPLETE"
    const val ACTION_SNOOZE = "io.github.akrishna87.doable.SNOOZE"
    const val ACTION_SUMMARY = "io.github.akrishna87.doable.SUMMARY"
    const val EXTRA_TASK = "task"
    const val EXTRA_KEY = "key"

    fun sync(context: Context, data: Data) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val before = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()
        // One alarm per reminder, keyed "task|reminder".
        val wanted: Map<String, Long> = if (!data.settings.reminders) {
            emptyMap()
        } else {
            data.tasks.flatMap { t ->
                Dates.reminderTimes(t).mapNotNull { (rid, at) ->
                    if (at.isAfter(now) && at.isBefore(now.plusDays(90))) "${t.id}|$rid" to at.atZone(zone).toInstant().toEpochMilli() else null
                }
            }.toMap()
        }
        for (key in before - wanted.keys) alarms.cancel(alarm(context, key))
        for ((key, at) in wanted) set(context, alarms, at, alarm(context, key))
        prefs.edit().putStringSet(KEY_SCHEDULED, wanted.keys).apply()

        // The morning summary.
        val summary = summaryIntent(context)
        val time = data.settings.dailySummary
        if (time == null) {
            alarms.cancel(summary)
        } else {
            var next = LocalDateTime.of(LocalDate.now(), time)
            if (!next.isAfter(now)) next = next.plusDays(1)
            set(context, alarms, next.atZone(zone).toInstant().toEpochMilli(), summary)
        }
    }

    private fun set(context: Context, alarms: AlarmManager, at: Long, pi: PendingIntent) {
        runCatching {
            if (canScheduleExact(context)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun alarm(context: Context, key: String): PendingIntent = PendingIntent.getBroadcast(
        context, key.hashCode(),
        Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
            .setData(Uri.parse("doable://reminder/${Uri.encode(key)}"))
            .putExtra(EXTRA_KEY, key)
            .putExtra(EXTRA_TASK, key.substringBefore('|')),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun summaryIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, SUMMARY_ID,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_SUMMARY),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun channel(context: Context, id: String, name: String, about: String, importance: Int) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(id) == null) {
            manager.createNotificationChannel(NotificationChannel(id, name, importance).apply { description = about })
        }
    }

    private fun openApp(context: Context, taskId: String?): PendingIntent = PendingIntent.getActivity(
        context, taskId?.hashCode() ?: 0,
        Intent(context, MainActivity::class.java)
            .setData(Uri.parse("doable://task/${taskId ?: "today"}"))
            .apply { if (taskId != null) putExtra(MainActivity.EXTRA_OPEN_TASK, taskId) }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun show(context: Context, task: Task, data: Data) {
        if (!allowed(context)) return
        channel(context, CHANNEL, "Task reminders", "A notification when a task's reminder is due", NotificationManager.IMPORTANCE_HIGH)
        val today = LocalDate.now()
        val is24 = android.text.format.DateFormat.is24HourFormat(context)
        val project = data.project(task.projectId).name
        val text = listOfNotNull(Dates.dueLabel(task, today, is24), project).joinToString(" · ")
        fun action(act: String) = PendingIntent.getBroadcast(
            context, (task.id + act).hashCode(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(act)
                .setData(Uri.parse("doable://task/${task.id}/$act"))
                .putExtra(EXTRA_TASK, task.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_doable)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setContentTitle(task.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (task.description.isBlank()) text else "$text\n${task.description}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, task.id))
            .addAction(0, "Complete", action(ACTION_COMPLETE))
            .addAction(0, "Snooze 15 min", action(ACTION_SNOOZE))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(task.id.hashCode(), notification) }
    }

    /** The morning list: overdue and today's tasks. */
    fun showSummary(context: Context, data: Data) {
        if (!allowed(context)) return
        val today = LocalDate.now()
        val overdue = Dates.overdue(data.tasks, today)
        val due = Dates.dueOn(data.tasks, today)
        if (overdue.isEmpty() && due.isEmpty()) return
        channel(context, SUMMARY_CHANNEL, "Daily summary", "Each morning, the tasks for the day", NotificationManager.IMPORTANCE_DEFAULT)
        val title = when {
            overdue.isEmpty() -> if (due.size == 1) "1 task today" else "${due.size} tasks today"
            due.isEmpty() -> if (overdue.size == 1) "1 overdue task" else "${overdue.size} overdue tasks"
            else -> "${due.size} today, ${overdue.size} overdue"
        }
        val lines = (overdue + due).take(7)
        val style = NotificationCompat.InboxStyle()
        for (t in lines) style.addLine(t.title)
        if (overdue.size + due.size > lines.size) style.setSummaryText("and ${overdue.size + due.size - lines.size} more")
        val n = NotificationCompat.Builder(context, SUMMARY_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_doable)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setContentTitle(title)
            .setContentText(lines.joinToString(", ") { it.title })
            .setStyle(style)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, null))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(SUMMARY_ID, n) }
    }
}

/** Shows due reminders, completes or snoozes tasks from the notification, and puts alarms back after a restart. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val id = intent.getStringExtra(Reminders.EXTRA_TASK)
        when (intent.action) {
            Reminders.ACTION_REMIND -> {
                val d = Store.data.value
                val task = d.task(id) ?: return
                if (!task.done && d.settings.reminders) Reminders.show(context, task, d)
            }
            Reminders.ACTION_COMPLETE -> {
                if (id == null) return
                Store.completeTask(id)
                NotificationManagerCompat.from(context).cancel(id.hashCode())
            }
            Reminders.ACTION_SNOOZE -> {
                if (id == null) return
                val t = Store.data.value.task(id) ?: return
                Store.saveTask(t.copy(reminders = t.reminders + io.github.akrishna87.doable.model.Reminder(at = LocalDateTime.now().plusMinutes(15).withSecond(0).withNano(0))))
                NotificationManagerCompat.from(context).cancel(id.hashCode())
            }
            Reminders.ACTION_SUMMARY -> {
                Reminders.showSummary(context, Store.data.value)
                Reminders.sync(context, Store.data.value)
            }
            else -> Reminders.sync(context, Store.data.value)
        }
    }
}
