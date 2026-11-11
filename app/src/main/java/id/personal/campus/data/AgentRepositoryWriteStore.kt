package id.personal.campus.data

import id.personal.campus.agent.AgentWriteStore

/**
 * Implementasi [AgentWriteStore] untuk aplikasi: **semua tulis lewat [CampusRepository]**,
 * tidak ada akses DAO langsung dan tidak ada jalur penyimpanan kedua.
 *
 * [onSaved] dipanggil setelah setiap penyimpanan. Pemanggilnya (ViewModel) mengisinya dengan
 * jalur penyegaran yang **sudah** dipakai fitur manual: `ScheduleCoordinator.refresh(...)` lalu
 * `refreshAccess()`. Widget dan pengingat jadi ikut berubah persis seperti setelah edit manual —
 * jangan menambah pemanggilan `ScheduleAlarms` baru di sini.
 *
 * Catatan id: `@Upsert` Room tidak mengembalikan id. Untuk data baru, entitas tersimpan dicari
 * lewat isi yang sama (id terbesar bila ada duplikat). Ini hanya dipakai agar Undo tahu baris
 * mana yang harus dibatalkan.
 */
class AgentRepositoryWriteStore(
    private val repository: CampusRepository,
    private val onSaved: suspend () -> Unit = {},
) : AgentWriteStore {

    override suspend fun snapshot(): CampusSnapshot = repository.snapshot()

    override suspend fun saveCourse(course: CourseEntity): CourseEntity {
        repository.saveCourse(course)
        onSaved()
        return resolveCourse(course)
    }

    override suspend fun saveTask(task: TaskEntity): TaskEntity {
        repository.saveTask(task)
        onSaved()
        return resolveTask(task)
    }

    override suspend fun saveNote(note: NoteEntity): NoteEntity {
        repository.saveNote(note)
        onSaved()
        return resolveNote(note)
    }

    /** Undo atas mata kuliah yang baru dibuat agent. Tidak dipanggil oleh tool mana pun. */
    override suspend fun removeCourse(id: Long) {
        repository.deleteCourse(id)
        onSaved()
    }

    /** Undo atas tugas yang baru dibuat agent. */
    override suspend fun removeTask(id: Long) {
        repository.deleteTask(id)
        onSaved()
    }

    /** Undo atas catatan yang baru dibuat agent. */
    override suspend fun removeNote(id: Long) {
        repository.deleteNote(id)
        onSaved()
    }

    private suspend fun resolveCourse(saved: CourseEntity): CourseEntity {
        val courses = repository.snapshot().courses
        if (saved.id != 0L) return courses.firstOrNull { it.id == saved.id } ?: saved
        val normalized = saved.copy(
            name = saved.name.trim(),
            room = saved.room.trim(),
            section = saved.section.trim(),
            roomNote = saved.roomNote.trim(),
        )
        return courses.filter { it.sameContentAs(normalized) }.maxByOrNull { it.id } ?: normalized
    }

    private suspend fun resolveTask(saved: TaskEntity): TaskEntity {
        val tasks = repository.snapshot().tasks
        if (saved.id != 0L) return tasks.firstOrNull { it.id == saved.id } ?: saved
        val normalized = saved.copy(title = saved.title.trim(), notes = saved.notes.trim())
        return tasks.filter { it.sameContentAs(normalized) }.maxByOrNull { it.id } ?: normalized
    }

    private suspend fun resolveNote(saved: NoteEntity): NoteEntity {
        val notes = repository.snapshot().notes
        if (saved.id != 0L) return notes.firstOrNull { it.id == saved.id } ?: saved
        val normalized = saved.copy(title = saved.title.trim(), body = saved.body.trim())
        return notes.filter { it.sameContentAs(normalized) }.maxByOrNull { it.id } ?: normalized
    }

    private fun CourseEntity.sameContentAs(other: CourseEntity): Boolean =
        name == other.name && weekday == other.weekday && startMinute == other.startMinute &&
            endMinute == other.endMinute && room == other.room && section == other.section &&
            roomNote == other.roomNote && enabled == other.enabled

    private fun TaskEntity.sameContentAs(other: TaskEntity): Boolean =
        title == other.title && courseId == other.courseId &&
            dueEpochDay == other.dueEpochDay && notes == other.notes && completed == other.completed

    private fun NoteEntity.sameContentAs(other: NoteEntity): Boolean =
        title == other.title && body == other.body
}