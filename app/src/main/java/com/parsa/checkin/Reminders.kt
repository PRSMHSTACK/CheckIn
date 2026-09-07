package com.parsa.checkin

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*
import java.util.concurrent.TimeUnit

class ReminderEngine(private val context: Context) {
    companion object { private val mutex = Mutex(); const val CHANNEL = "check_in_reminders"; const val ID = 41 }
    private val app get() = context.app
    private val state = context.getSharedPreferences("schedule", Context.MODE_PRIVATE)
    private val manager get() = context.getSystemService(AlarmManager::class.java)
    private fun alarm() = PendingIntent.getBroadcast(context, 0, Intent(context, ReminderReceiver::class.java).setAction("DUE"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun channels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, "Task check-ins", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Recurring check-ins for unfinished tasks"
            enableVibration(true)
            enableLights(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        nm.createNotificationChannel(channel)
    }
    fun ensureRecovery() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("reminder-recovery", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RecoveryWorker>(15, TimeUnit.MINUTES).build())
    }
    suspend fun update(action: String = "REPLAN", id: Long = -1, minutes: Int = 30) = mutex.withLock {
        val now = ZonedDateTime.now()
        if (action == "COMPLETE" && id > 0) app.db.tasks().complete(id, true)
        if (action == "SNOOZE") {
            app.prefs.snooze = now.plusMinutes(minutes.coerceIn(1,1440).toLong()).toInstant().toEpochMilli()
            clear()
        }
        val settings = app.prefs.read()
        val tasks = app.db.tasks().all()
        val today = tasks.filter { it.date == now.toLocalDate().toString() && !it.completed }
        val due = state.getLong("next", 0)
        val isPreview = action == "PREVIEW"
        val displayTasks = if (today.isNotEmpty()) today else if (isPreview) listOf(Task(title = "Sample task for check-in preview", date = now.toLocalDate().toString())) else emptyList()
        val shouldDeliver = isPreview || ((action == "DUE" || action == "RECOVER") && due > 0 && due <= System.currentTimeMillis())
        val quiet = Schedule.quiet(now.toLocalTime(), settings)

        if (shouldDeliver && displayTasks.isNotEmpty()
            && (isPreview || (settings.enabled && !quiet && app.prefs.snooze <= System.currentTimeMillis() && System.currentTimeMillis() - app.prefs.last > 60_000))) {
            show(displayTasks)
            if (!isPreview) app.prefs.last = System.currentTimeMillis()
            app.prefs.snooze = 0
        }
        if (today.isEmpty() || !settings.enabled) { clear(); app.prefs.snooze = 0 }
        else if (action == "COMPLETE" || (action == "REPLAN" && context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == ID })) show(today, silent = true)
        val next = Schedule.next(now, tasks, settings, app.prefs.snooze)
        manager.cancel(alarm())
        state.edit().putLong("next", next?.toInstant()?.toEpochMilli() ?: 0).commit()
        next?.let { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.toInstant().toEpochMilli(), alarm()) }
    }
    fun clear() { NotificationManagerCompat.from(context).cancel(ID) }
    private fun action(name: String, id: Long = -1, minutes: Int = 30): PendingIntent = PendingIntent.getBroadcast(context,
        (id.hashCode() * 31 + name.hashCode()), Intent(context, ReminderReceiver::class.java).setAction(name)
            .setData(android.net.Uri.parse("checkin://action/$name/$id/$minutes")).putExtra("id", id).putExtra("minutes", minutes),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun show(tasks: List<Task>, silent: Boolean = false) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val openIntent = Intent(context, MainActivity::class.java)
            .putExtra("review", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= 34) {
            options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }

        val openPendingIntent = PendingIntent.getActivity(
            context, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            options.toBundle()
        )
        if (!silent) {
            runCatching { openPendingIntent.send(context, 0, null, null, null, null, options.toBundle()) }
            runCatching { context.startActivity(openIntent, options.toBundle()) }
        }
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle("How are your tasks going?")
            .setContentText("${tasks.size} unfinished • Tap to review your checklist")
            .setStyle(NotificationCompat.InboxStyle().also { style -> tasks.take(6).forEach { style.addLine("□ ${it.title}") } })
            .setContentIntent(openPendingIntent)
            .setFullScreenIntent(openPendingIntent, true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setSilent(silent)
            .addAction(0, "Review checklist", openPendingIntent)
            .addAction(0, "Snooze 30 min", action("SNOOZE"))
            .addAction(0, "Done: ${tasks.first().title.take(22)}", action("COMPLETE", tasks.first().id))

        if (!silent) {
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        }

        NotificationManagerCompat.from(context).notify(ID, builder.build())
    }
}
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { context.app.reminders.update(intent.action ?: "DUE", intent.getLongExtra("id", -1), intent.getIntExtra("minutes", 30)) }
            finally { pending.finish() }
        }
    }
}
class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<RecoveryWorker>().build())
    }
}
class RecoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try { applicationContext.app.reminders.update("RECOVER"); Result.success() } catch (_: Exception) { Result.retry() }
}
