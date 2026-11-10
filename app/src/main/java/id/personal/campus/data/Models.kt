package id.personal.campus.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.ZoneId

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val weekday: Int,
    val startMinute: Int,
    val endMinute: Int,
    val room: String,
    val section: String = "01",
    val roomNote: String = "",
    val enabled: Boolean = true,
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val courseId: Long? = null,
    val dueEpochDay: Long? = null,
    val notes: String = "",
    val completed: Boolean = false,
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String = "",
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val semesterName: String = "Gasal 2026/2027",
    val startEpochDay: Long? = null,
    val endEpochDay: Long? = null,
    val zoneId: String = "Asia/Jakarta",
    val remindersEnabled: Boolean = false,
    val reminderMinutes: Int = 15,
    val seedVersion: Int = 1,
) {
    val semesterConfirmed: Boolean
        get() = startEpochDay != null && endEpochDay != null && startEpochDay <= endEpochDay
    val zone: ZoneId get() = ZoneId.of(zoneId)
    val startDate: LocalDate? get() = startEpochDay?.let(LocalDate::ofEpochDay)
    val endDate: LocalDate? get() = endEpochDay?.let(LocalDate::ofEpochDay)
}

@Entity(tableName = "delivered_reminders")
data class DeliveredReminder(
    @PrimaryKey val occurrenceKey: String,
    val startEpochMillis: Long,
)

data class CampusSnapshot(
    val courses: List<CourseEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val settings: SettingsEntity = SettingsEntity(),
)
