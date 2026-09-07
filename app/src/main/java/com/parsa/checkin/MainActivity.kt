package com.parsa.checkin

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val review = mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge(); setupWindowFlags(); review.value = intent.getBooleanExtra("review", false)
        setContent {
            val vm: TaskViewModel = viewModel()
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF9ED5BD))
                else lightColorScheme(primary = Color(0xFF306B55), background = Color(0xFFF8FAF6), surface = Color(0xFFF8FAF6))) {
                Home(vm, review.value, { review.value = it })
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); setupWindowFlags(); review.value = intent.getBooleanExtra("review", false) }

    private fun setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home(vm: TaskViewModel, review: Boolean, setReview: (Boolean) -> Unit) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var today by remember { mutableStateOf(LocalDate.now()) }
    var selected by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var editor by remember { mutableStateOf<Task?>(null) }
    var deleting by remember { mutableStateOf<Task?>(null) }
    var snooze by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        enabled = NotificationManagerCompat.from(context).areNotificationsEnabled(); vm.recover()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { enabled = NotificationManagerCompat.from(context).areNotificationsEnabled(); vm.recover() }
    LaunchedEffect(Unit) { while (true) { val date = LocalDate.now(); if (date != today) { if (selected == today.toString()) selected = date.toString(); today = date }; delay(15_000) } }
    val day = if (review) today.toString() else selected
    val visible = tasks.filter { it.date == day }.sortedWith(compareBy<Task> { it.completed }.thenByDescending { it.important })
    val overdue = tasks.filter { !it.completed && it.date < today.toString() }
    fun pickDate() { val d = LocalDate.parse(selected); DatePickerDialog(context, { _, y, m, n -> selected = LocalDate.of(y,m+1,n).toString() }, d.year,d.monthValue-1,d.dayOfMonth).show() }
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (review) "A little check-in" else "Check In", fontWeight = FontWeight.Bold) },
            actions = { TextButton(onClick = { settingsOpen = true }) { Text("Reminders") } }) },
        floatingActionButton = { if (!review) ExtendedFloatingActionButton(onClick = { editor = Task(title = "", date = day) }) { Text("+ Add task") } },
        bottomBar = { if (review) Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            Button(onClick = { snooze = true }, modifier = Modifier.fillMaxWidth(), enabled = visible.any { !it.completed }) { Text("Remind me later") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { setReview(false); selected = today.toString() }) { Text("Open task list") }
                TextButton(onClick = { setReview(false) }) { Text("Dismiss") }
            }
        } }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp,12.dp,20.dp,100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(LocalDate.parse(day).format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text(if (review) "How are your\ntasks going?" else "What's on your\nlist today?", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text("${visible.count { it.completed }} of ${visible.size} completed", color = MaterialTheme.colorScheme.primary)
                if (visible.isNotEmpty()) LinearProgressIndicator(progress = { visible.count { it.completed }.toFloat()/visible.size }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            }
            if (!enabled) item { Card { Column(Modifier.padding(16.dp)) {
                Text("Turn on reminders", fontWeight = FontWeight.Bold)
                Text("Allow notifications so your checklist can come back to you.")
                TextButton(onClick = { if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Enable notifications") }
                TextButton(onClick = { context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Open notification settings") }
            } } }
            if (!review) item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = day == today.toString(), onClick = { selected = today.toString() }, label = { Text("Today") })
                FilterChip(selected = day == today.plusDays(1).toString(), onClick = { selected = today.plusDays(1).toString() }, label = { Text("Tomorrow") })
                AssistChip(onClick = { pickDate() }, label = { Text("Pick date") })
            } }
            if (visible.isEmpty()) item { Text("A clear page. Add one thing you want to get done.", modifier = Modifier.padding(vertical = 28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (visible.isNotEmpty() && visible.all { it.completed }) item { Text("All done. Enjoy the breathing room.", color = MaterialTheme.colorScheme.primary) }
            items(visible, key = { it.id }) { task ->
                Card(Modifier.fillMaxWidth().alpha(if (task.completed) .5f else 1f), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(task.completed, role = Role.Checkbox, onValueChange = { vm.complete(task,it) }).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = task.completed, onCheckedChange = null)
                        Column(Modifier.weight(1f).padding(8.dp)) {
                            Text(task.title, style = MaterialTheme.typography.titleMedium, textDecoration = if (task.completed) TextDecoration.LineThrough else null)
                            if (task.time.isNotBlank() || task.important) Text(listOfNotNull(task.time.takeIf { it.isNotBlank() }, "Important".takeIf { task.important }).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { editor = task }) { Text("Edit") }
                    }
                }
            }
            if (!review && overdue.isNotEmpty()) {
                item { Text("Still unfinished", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp)) }
                items(overdue, key = { "old${it.id}" }) { task -> Card { Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(task.title, fontWeight = FontWeight.SemiBold); Text(task.date, style = MaterialTheme.typography.bodySmall)
                    Row { TextButton(onClick = { vm.move(task, today.toString()) }) { Text("Today") }; TextButton(onClick = { vm.move(task,today.plusDays(1).toString()) }) { Text("Tomorrow") }; TextButton(onClick = { editor = task }) { Text("Review") } }
                } } }
            }
            if (!review) {
                val upcoming = tasks.filter { it.date > today.toString() }.groupBy { it.date }.toSortedMap()
                if (upcoming.isNotEmpty()) item { Text("Upcoming", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp)) }
                items(upcoming.keys.toList()) { date -> TextButton(onClick = { selected = date }, modifier = Modifier.fillMaxWidth()) { Text("${LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE, MMM d"))} — ${upcoming[date]!!.size} tasks") } }
            }
        }
    }
    editor?.let { task -> TaskEditor(task, { editor = null }, { vm.save(it); editor = null }, { deleting = task }, { vm.move(task,today.plusDays(1).toString()); editor = null }) }
    deleting?.let { task -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete this task?") }, text = { Text(task.title) }, confirmButton = { TextButton(onClick = { vm.delete(task); deleting = null; editor = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }) }
    if (settingsOpen) SettingsDialog(settings, { vm.settings(it); settingsOpen = false }, { settingsOpen = false }, { vm.preview() })
    if (snooze) SnoozeDialog({ snooze = false }, { vm.snooze(it); snooze = false; setReview(false) })
}

@Composable
fun TaskEditor(task: Task, close: () -> Unit, save: (Task) -> Unit, delete: () -> Unit, move: () -> Unit) {
    val context = LocalContext.current
    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }
    var date by rememberSaveable(task.id) { mutableStateOf(task.date) }
    var time by rememberSaveable(task.id) { mutableStateOf(task.time) }
    var notes by rememberSaveable(task.id) { mutableStateOf(task.notes) }
    var important by rememberSaveable(task.id) { mutableStateOf(task.important) }
    var details by rememberSaveable { mutableStateOf(task.id != 0L) }
    val valid = title.isNotBlank() && (time.isBlank() || runCatching { LocalTime.parse(time) }.isSuccess)
    AlertDialog(onDismissRequest = close, title = { Text(if (task.id == 0L) "One thing to do" else "Edit task") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Task name") }, singleLine = true)
            TextButton(onClick = { val d = LocalDate.parse(date); DatePickerDialog(context, { _, y,m,n -> date = LocalDate.of(y,m+1,n).toString() },d.year,d.monthValue-1,d.dayOfMonth).show() }) { Text("Date: $date") }
            TextButton(onClick = { details = !details }) { Text(if (details) "Fewer options" else "Time, notes & priority") }
            if (details) {
                OutlinedTextField(time, { time = it }, label = { Text("Optional time (HH:mm)") }, singleLine = true, isError = time.isNotBlank() && runCatching { LocalTime.parse(time) }.isFailure)
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, maxLines = 4)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(important, { important = it }); Text("Important · shown first") }
            }
            if (task.id != 0L) { TextButton(onClick = move) { Text("Move to tomorrow") }; TextButton(onClick = delete) { Text("Delete task", color = MaterialTheme.colorScheme.error) } }
        }
    }, confirmButton = { Button(enabled = valid, onClick = { save(task.copy(title = title.trim(),date = date,time = time,notes = notes,important = important)) }) { Text("Save") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable
fun SettingsDialog(initial: Settings, save: (Settings) -> Unit, close: () -> Unit, preview: () -> Unit) {
    var s by remember { mutableStateOf(initial) }
    val context = LocalContext.current
    val valid = runCatching { LocalTime.parse(s.quietStart); LocalTime.parse(s.quietEnd) }.isSuccess &&
        (!s.quiet || s.quietStart != s.quietEnd) && (s.intensity != "Custom" || (s.custom.split(",").all { runCatching { LocalTime.parse(it.trim()) }.isSuccess }))
    AlertDialog(onDismissRequest = close, title = { Text("Keep checking in") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Reminders", Modifier.weight(1f)); Switch(s.enabled, { s = s.copy(enabled = it) }) }
            listOf("Gentle" to "2 times a day", "Normal" to "4 times a day", "Persistent" to "Every 2 hours, 08–22", "Custom" to "Choose your times").forEach { (name, detail) ->
                Row(Modifier.fillMaxWidth().clickable { s = s.copy(intensity = name) }, verticalAlignment = Alignment.CenterVertically) { RadioButton(s.intensity == name, { s = s.copy(intensity = name) }); Column { Text(name); Text(detail, style = MaterialTheme.typography.bodySmall) } }
            }
            if (s.intensity == "Custom") OutlinedTextField(s.custom, { s = s.copy(custom = it) }, label = { Text("Times, e.g. 09:00, 18:00") })
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Quiet hours", Modifier.weight(1f)); Switch(s.quiet, { s = s.copy(quiet = it) }) }
            if (s.quiet) {
                OutlinedTextField(s.quietStart, { s = s.copy(quietStart = it) }, label = { Text("Start (HH:mm)") }, singleLine = true)
                OutlinedTextField(s.quietEnd, { s = s.copy(quietEnd = it) }, label = { Text("End (HH:mm)") }, singleLine = true)
            }
            Text("Reminders stop when today's list is complete. Times are approximate and may be delayed by Android. Past tasks stay unfinished until you move, complete or delete them.", style = MaterialTheme.typography.bodySmall)
            Text("Allow pop-ups in Android's notification settings for prominent banners. Android controls banners, lock-screen visibility and sound; this app cannot block other apps.", style = MaterialTheme.typography.bodySmall)
            if (!AndroidSettings.canDrawOverlays(context)) {
                TextButton(onClick = { context.startActivity(Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }) { Text("Allow display over other apps") }
            }
            TextButton(onClick = { context.startActivity(Intent(AndroidSettings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName).putExtra(AndroidSettings.EXTRA_CHANNEL_ID, ReminderEngine.CHANNEL)) }) { Text("Sound, vibration & pop-ups") }
            TextButton(onClick = preview) { Text("Test saved reminder settings") }
            if (!valid) Text("Use valid 24-hour times; quiet start and end must differ.", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { Button(onClick = { save(s) }, enabled = valid) { Text("Save") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}
@Composable
fun SnoozeDialog(close: () -> Unit, snooze: (Int) -> Unit) {
    var minutes by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("A little more time") }, text = { Column {
        listOf(15,30,60).forEach { n -> TextButton(onClick = { snooze(n) }, modifier = Modifier.fillMaxWidth()) { Text(if (n == 60) "1 hour" else "$n minutes") } }
        OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(4) }, label = { Text("Custom minutes (1–1440)") })
        Text("Quiet hours still apply.", style = MaterialTheme.typography.bodySmall)
    } }, confirmButton = { TextButton(enabled = (minutes.toIntOrNull() ?: 0) in 1..1440, onClick = { snooze(minutes.toInt()) }) { Text("Snooze") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}
