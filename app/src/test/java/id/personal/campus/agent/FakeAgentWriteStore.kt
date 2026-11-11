package id.personal.campus.agent

import id.personal.campus.data.CampusSnapshot
import id.personal.campus.data.CourseEntity
import id.personal.campus.data.NoteEntity
import id.personal.campus.data.TaskEntity

/**
 * Penyimpanan tiruan untuk menguji tool tulis tanpa Room.
 *
 * Meniru perilaku `CampusRepository`: id baru diberikan saat menyimpan data baru, dan
 * penghapusan hanya dipakai jalur Undo (bukan oleh tool).
 */
internal class FakeAgentWriteStore(initial: CampusSnapshot) : AgentWriteStore {
    var data: CampusSnapshot = initial
        private set
    var courseSaves = 0
        private set
    var taskSaves = 0
        private set
    var noteSaves = 0
        private set
    var removes = 0
        private set
    private var nextId = 1_000L

    override suspend fun snapshot(): CampusSnapshot = data

    override suspend fun saveCourse(course: CourseEntity): CourseEntity {
        courseSaves++
        val stored = if (course.id == 0L) course.copy(id = nextId++) else course
        data = data.copy(courses = data.courses.filterNot { it.id == stored.id } + stored)
        return stored
    }

    override suspend fun saveTask(task: TaskEntity): TaskEntity {
        taskSaves++
        val stored = if (task.id == 0L) task.copy(id = nextId++) else task
        data = data.copy(tasks = data.tasks.filterNot { it.id == stored.id } + stored)
        return stored
    }

    override suspend fun saveNote(note: NoteEntity): NoteEntity {
        noteSaves++
        val stored = if (note.id == 0L) note.copy(id = nextId++) else note
        data = data.copy(notes = data.notes.filterNot { it.id == stored.id } + stored)
        return stored
    }

    override suspend fun removeCourse(id: Long) {
        removes++
        data = data.copy(courses = data.courses.filterNot { it.id == id })
    }

    override suspend fun removeTask(id: Long) {
        removes++
        data = data.copy(tasks = data.tasks.filterNot { it.id == id })
    }

    override suspend fun removeNote(id: Long) {
        removes++
        data = data.copy(notes = data.notes.filterNot { it.id == id })
    }
}