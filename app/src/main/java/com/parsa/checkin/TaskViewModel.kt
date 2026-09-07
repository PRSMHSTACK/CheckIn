package com.parsa.checkin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CheckInApp
    val tasks = app.db.tasks().observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _settings = MutableStateFlow(app.prefs.read())
    val settings = _settings.asStateFlow()
    private fun edit(block: suspend () -> Unit) { viewModelScope.launch(Dispatchers.IO) { block(); app.reminders.update() } }
    fun save(task: Task) = edit { app.db.tasks().save(task) }
    fun complete(task: Task, done: Boolean) = edit { app.db.tasks().complete(task.id, done) }
    fun delete(task: Task) = edit { app.db.tasks().delete(task.id) }
    fun move(task: Task, date: String) = edit { app.db.tasks().move(task.id, date) }
    fun settings(s: Settings) { _settings.value = s; edit { app.prefs.save(s) } }
    fun snooze(minutes: Int) { viewModelScope.launch(Dispatchers.IO) { app.reminders.update("SNOOZE", minutes = minutes) } }
    fun recover() { viewModelScope.launch(Dispatchers.IO) { app.reminders.update("RECOVER") } }
    fun preview() { viewModelScope.launch(Dispatchers.IO) { app.reminders.update("PREVIEW") } }
}
