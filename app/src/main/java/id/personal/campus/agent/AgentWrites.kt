package id.personal.campus.agent

import id.personal.campus.data.CampusSnapshot
import id.personal.campus.data.CourseEntity
import id.personal.campus.data.NoteEntity
import id.personal.campus.data.TaskEntity

/**
 * Kontrak tulis untuk tool agen.
 *
 * Aturan yang dijaga di lapisan ini (lihat brief §2.4 dan §7):
 * - Agent **tidak pernah** menulis sebelum user menyetujui. Alurnya selalu
 *   `prepare` (validasi, belum menyimpan) → kartu konfirmasi → `apply` (baru menyimpan).
 * - Agent **tidak punya tool hapus**. `remove*` di bawah hanya dipakai untuk membatalkan
 *   (Undo) baris yang barusan dibuat agent sendiri.
 * - Semua penyimpanan tetap lewat `CampusRepository`. Implementasi aplikasi ada di
 *   `ui/AgentWriteBridge.kt`, yang juga memakai jalur penyegaran widget/pengingat yang
 *   sudah dipakai fitur manual (`ScheduleCoordinator.refresh`), bukan jalur baru.
 *
 * Bebas `android.*` supaya bisa diuji di CI JVM dengan penyimpanan tiruan.
 */
interface AgentWriteStore {
    suspend fun snapshot(): CampusSnapshot

    /** Menyimpan lalu mengembalikan entitas tersimpan (id baru terisi untuk data baru). */
    suspend fun saveCourse(course: CourseEntity): CourseEntity
    suspend fun saveTask(task: TaskEntity): TaskEntity
    suspend fun saveNote(note: NoteEntity): NoteEntity

    /** Hanya untuk Undo atas baris yang baru dibuat agent. Tidak ada tool yang memanggilnya. */
    suspend fun removeCourse(id: Long)
    suspend fun removeTask(id: Long)
    suspend fun removeNote(id: Long)
}

enum class AgentWriteKind { CREATE, UPDATE }

/** Satu baris perubahan, dipakai kartu konfirmasi ("Jam: 13:00–14:00 → 13:00–14:40"). */
data class AgentFieldChange(val label: String, val before: String?, val after: String)

/**
 * Usulan perubahan yang **wajib** disetujui user.
 *
 * [question] adalah kalimat kartu konfirmasi, mis.
 * "Ubah jam Bahasa Indonesia → 13:00–14:40?".
 */
data class AgentWriteProposal(
    val tool: String,
    val title: String,
    val question: String,
    val detail: String,
    val kind: AgentWriteKind,
    val changes: List<AgentFieldChange> = emptyList(),
    val warnings: List<String> = emptyList(),
)

/** Aksi yang bisa dibatalkan. Satu aksi tulis = satu entri Undo. */
sealed interface AgentUndoAction {
    val label: String

    data class SaveCourse(val course: CourseEntity) : AgentUndoAction {
        override val label: String get() = "Jadwal ${course.name} kembali seperti semula"
    }

    data class SaveTask(val task: TaskEntity) : AgentUndoAction {
        override val label: String get() = "Tugas \"${task.title}\" kembali seperti semula"
    }

    data class SaveNote(val note: NoteEntity) : AgentUndoAction {
        override val label: String get() = "Catatan \"${note.title}\" kembali seperti semula"
    }

    data class RemoveCourse(val id: Long, val name: String) : AgentUndoAction {
        override val label: String get() = "Mata kuliah ${name} dibatalkan"
    }

    data class RemoveTask(val id: Long, val title: String) : AgentUndoAction {
        override val label: String get() = "Tugas \"$title\" dibatalkan"
    }

    data class RemoveNote(val id: Long, val title: String) : AgentUndoAction {
        override val label: String get() = "Catatan \"$title\" dibatalkan"
    }
}

/**
 * Tumpukan Undo terbatas (LIFO). Batasnya sengaja kecil: satu aksi = satu Undo, dan
 * riwayat tidak menumpuk tanpa ujung selama sesi berjalan.
 */
class AgentUndoStack(private val capacity: Int = 10) {
    private val entries = ArrayDeque<AgentUndoAction>()

    val size: Int get() = entries.size

    fun push(action: AgentUndoAction) {
        entries.addLast(action)
        while (entries.size > capacity) entries.removeFirst()
    }

    fun peek(): AgentUndoAction? = entries.lastOrNull()

    fun pop(): AgentUndoAction? = entries.removeLastOrNull()

    fun clear() = entries.clear()
}

/** Hasil satu pembatalan, untuk pesan di UI. */
data class AgentUndoOutcome(val title: String, val detail: String)

/** Tool tulis yang sudah lolos validasi tetapi belum menyimpan apa pun. */
sealed interface AgentWritePreparation {
    /** Argumen tidak lengkap/valid: agent harus **bertanya**, bukan mengarang nilai. */
    data class Invalid(val question: String) : AgentWritePreparation

    /** Tidak ada yang perlu diubah (mis. tugas sudah selesai). Tidak perlu konfirmasi. */
    data class Unchanged(val outcome: AgentToolOutcome) : AgentWritePreparation

    /** Siap dikonfirmasi. [apply] baru menyimpan setelah user menekan "Setuju". */
    data class Ready(
        val proposal: AgentWriteProposal,
        val apply: suspend () -> AppliedWrite,
    ) : AgentWritePreparation
}

data class AppliedWrite(val outcome: AgentToolOutcome) {
    /** Ringkasan untuk jejak aksi, mis. "mengubah jadwal Bahasa Indonesia". */
    val traceSummary: String get() = outcome.summary
}