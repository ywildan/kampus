package id.personal.campus.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CampusDao {
    @Query("SELECT * FROM courses ORDER BY weekday, startMinute, id")
    fun observeCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM tasks ORDER BY completed, dueEpochDay IS NULL, dueEpochDay, id DESC")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM notes ORDER BY id DESC")
    fun observeNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM settings WHERE id = 1")
    fun observeSettings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM courses ORDER BY weekday, startMinute, id")
    suspend fun courses(): List<CourseEntity>

    @Query("SELECT * FROM tasks ORDER BY completed, dueEpochDay IS NULL, dueEpochDay, id DESC")
    suspend fun tasks(): List<TaskEntity>

    @Query("SELECT * FROM notes ORDER BY id DESC")
    suspend fun notes(): List<NoteEntity>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun settings(): SettingsEntity?

    @Upsert suspend fun saveCourse(course: CourseEntity)
    @Upsert suspend fun saveTask(task: TaskEntity)
    @Upsert suspend fun saveNote(note: NoteEntity)
    @Upsert suspend fun saveSettings(settings: SettingsEntity)
    @Insert suspend fun insertCourses(courses: List<CourseEntity>)

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteCourse(id: Long)

    @Query("UPDATE tasks SET courseId = NULL WHERE courseId = :id")
    suspend fun detachCourse(id: Long)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: Long)

    @Query("SELECT occurrenceKey FROM delivered_reminders")
    suspend fun deliveredKeys(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun claimReminder(reminder: DeliveredReminder): Long

    @Query("DELETE FROM delivered_reminders WHERE startEpochMillis < :before")
    suspend fun pruneReminders(before: Long)
}

@Database(
    entities = [CourseEntity::class, TaskEntity::class, NoteEntity::class,
        SettingsEntity::class, DeliveredReminder::class],
    version = 1,
    exportSchema = false,
)
abstract class CampusDatabase : RoomDatabase() {
    abstract fun dao(): CampusDao
}
