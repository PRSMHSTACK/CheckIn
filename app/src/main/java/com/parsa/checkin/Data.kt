package com.parsa.checkin

import android.app.Application
import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tasks")
data class Task(@PrimaryKey(autoGenerate = true) val id: Long = 0, val title: String,
    val date: String, val time: String = "", val notes: String = "", val important: Boolean = false,
    val completed: Boolean = false)
@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY date, completed, important DESC, id") fun observe(): Flow<List<Task>>
    @Query("SELECT * FROM tasks ORDER BY date, completed, important DESC, id") suspend fun all(): List<Task>
    @Upsert suspend fun save(task: Task)
    @Query("UPDATE tasks SET completed = :done WHERE id = :id") suspend fun complete(id: Long, done: Boolean)
    @Query("DELETE FROM tasks WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE tasks SET date = :date WHERE id = :id") suspend fun move(id: Long, date: String)
}
@Database(entities = [Task::class], version = 1, exportSchema = false)
abstract class TaskDatabase : RoomDatabase() { abstract fun tasks(): TaskDao }

class CheckInApp : Application() {
    val db by lazy { Room.databaseBuilder(this, TaskDatabase::class.java, "check-in.db").build() }
    val prefs by lazy { Preferences(this) }
    val reminders by lazy { ReminderEngine(this) }
    override fun onCreate() { super.onCreate(); reminders.channels(); reminders.ensureRecovery() }
}
val Context.app: CheckInApp get() = applicationContext as CheckInApp

data class Settings(val enabled: Boolean = true, val intensity: String = "Normal",
    val custom: String = "09:00, 13:00, 18:00", val quiet: Boolean = true,
    val quietStart: String = "23:00", val quietEnd: String = "08:00")
class Preferences(context: Context) {
    private val p = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    fun read() = Settings(p.getBoolean("enabled", true), p.getString("intensity", "Normal")!!,
        p.getString("custom", "09:00, 13:00, 18:00")!!, p.getBoolean("quiet", true),
        p.getString("start", "23:00")!!, p.getString("end", "08:00")!!)
    fun save(s: Settings) { p.edit().putBoolean("enabled", s.enabled).putString("intensity", s.intensity)
        .putString("custom", s.custom).putBoolean("quiet", s.quiet).putString("start", s.quietStart).putString("end", s.quietEnd).commit() }
    var snooze: Long get() = p.getLong("snooze", 0); set(v) { p.edit().putLong("snooze", v).commit() }
    var last: Long get() = p.getLong("last", 0); set(v) { p.edit().putLong("last", v).commit() }
}
