package id.personal.campus.data

import androidx.room.withTransaction
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CampusRepository(private val database: CampusDatabase) {
    private val dao = database.dao()
    private val seedMutex = Mutex()

    val snapshots: Flow<CampusSnapshot> = combine(
        dao.observeCourses(), dao.observeTasks(), dao.observeNotes(), dao.observeSettings(),
    ) { courses, tasks, notes, settings ->
        CampusSnapshot(courses, tasks, notes, settings ?: SettingsEntity())
    }

    suspend fun initialize() = seedMutex.withLock {
        database.withTransaction {
            if (dao.settings() == null) {
                dao.insertCourses(InitialSchedule.courses)
                dao.saveSettings(SettingsEntity())
            }
        }
    }

    suspend fun snapshot(): CampusSnapshot {
        initialize()
        return database.withTransaction {
            CampusSnapshot(dao.courses(), dao.tasks(), dao.notes(), checkNotNull(dao.settings()))
        }
    }

    suspend fun saveCourse(course: CourseEntity) {
        require(course.name.isNotBlank() && course.room.isNotBlank())
        require(course.weekday in 1..7)
        require(course.startMinute in 0..1439 && course.endMinute in 1..1439)
        require(course.startMinute < course.endMinute)
        initialize()
        dao.saveCourse(course.copy(
            name = course.name.trim(), room = course.room.trim(),
            section = course.section.trim(), roomNote = course.roomNote.trim(),
        ))
    }

    suspend fun deleteCourse(id: Long) = database.withTransaction {
        dao.detachCourse(id)
        dao.deleteCourse(id)
    }

    suspend fun saveTask(task: TaskEntity) {
        require(task.title.isNotBlank())
        dao.saveTask(task.copy(title = task.title.trim(), notes = task.notes.trim()))
    }

    suspend fun deleteTask(id: Long) = dao.deleteTask(id)

    suspend fun saveNote(note: NoteEntity) {
        require(note.title.isNotBlank())
        dao.saveNote(note.copy(title = note.title.trim(), body = note.body.trim()))
    }

    suspend fun deleteNote(id: Long) = dao.deleteNote(id)

    suspend fun saveSettings(settings: SettingsEntity) {
        require(settings.id == 1)
        require(settings.reminderMinutes in listOf(5, 10, 15, 30, 60))
        ZoneId.of(settings.zoneId)
        require((settings.startEpochDay == null && settings.endEpochDay == null) ||
            settings.semesterConfirmed)
        require(!settings.remindersEnabled || settings.semesterConfirmed)
        initialize()
        dao.saveSettings(settings.copy(semesterName = settings.semesterName.trim()))
    }

    suspend fun deliveredKeys(): Set<String> = dao.deliveredKeys().toSet()

    suspend fun claimReminder(key: String, start: Instant): Boolean =
        dao.claimReminder(DeliveredReminder(key, start.toEpochMilli())) != -1L

    suspend fun pruneReminders(before: Instant) = dao.pruneReminders(before.toEpochMilli())
}
