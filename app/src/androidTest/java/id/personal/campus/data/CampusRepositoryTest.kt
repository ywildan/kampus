package id.personal.campus.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CampusRepositoryTest {
    private lateinit var database: CampusDatabase
    private lateinit var repository: CampusRepository

    @Before fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), CampusDatabase::class.java,
        ).build()
        repository = CampusRepository(database)
    }

    @After fun closeDatabase() = database.close()

    @Test fun initializationIsIdempotentAndRetainsEdits() = runBlocking {
        repository.initialize()
        val initial = repository.snapshot().courses
        assertEquals(8, initial.size)
        repository.saveCourse(initial.first().copy(room = "B.1"))
        repository.initialize()
        val reopened = CampusRepository(database).snapshot()
        assertEquals(8, reopened.courses.size)
        assertEquals("B.1", reopened.courses.first().room)
    }

    @Test fun deletingAllCoursesDoesNotReseed() = runBlocking {
        repository.snapshot().courses.forEach { repository.deleteCourse(it.id) }
        repository.initialize()
        assertTrue(CampusRepository(database).snapshot().courses.isEmpty())
    }

    @Test fun deletingCourseKeepsTaskAndClearsItsReference() = runBlocking {
        val course = repository.snapshot().courses.first()
        repository.saveTask(TaskEntity(title = "Latihan", courseId = course.id))
        repository.deleteCourse(course.id)
        val task = repository.snapshot().tasks.single()
        assertEquals("Latihan", task.title)
        assertNull(task.courseId)
    }

    @Test fun taskAndNoteChangesPersistAcrossRepositoryInstances() = runBlocking {
        repository.initialize()
        repository.saveTask(TaskEntity(title = "  Tugas  ", dueEpochDay = 20768))
        repository.saveNote(NoteEntity(title = "Info", body = "Pindah ruang"))
        val second = CampusRepository(database)
        val snapshot = second.snapshot()
        assertEquals("Tugas", snapshot.tasks.single().title)
        assertEquals("Pindah ruang", snapshot.notes.single().body)
        second.saveTask(snapshot.tasks.single().copy(completed = true))
        assertTrue(repository.snapshot().tasks.single().completed)
    }

    @Test fun reminderClaimIsPersistentAndUnique() = runBlocking {
        repository.initialize()
        val start = Instant.parse("2026-11-11T05:30:00Z")
        assertTrue(repository.claimReminder("7:${start.toEpochMilli()}", start))
        assertFalse(CampusRepository(database).claimReminder("7:${start.toEpochMilli()}", start))
        assertEquals(1, repository.deliveredKeys().size)
    }
}
