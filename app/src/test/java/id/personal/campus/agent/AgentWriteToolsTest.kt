package id.personal.campus.agent

import id.personal.campus.data.CampusSnapshot
import id.personal.campus.data.CourseEntity
import id.personal.campus.data.InitialSchedule
import id.personal.campus.data.NoteEntity
import id.personal.campus.data.SettingsEntity
import id.personal.campus.data.TaskEntity
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tool tulis diuji tanpa Room dan tanpa jaringan: penyimpanan tiruan + jam tetap
 * (Rabu, 11 November 2026, 09:00 WIB).
 *
 * Yang dijaga: validasi terjadi **sebelum** konfirmasi, tidak ada penulisan saat argumen
 * kurang, hasilnya sama dengan edit manual, dan setiap aksi punya satu Undo.
 */
class AgentWriteToolsTest {

    private val zone: ZoneId = ZoneId.of("Asia/Jakarta")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-11T02:00:00Z"), ZoneId.of("UTC"))

    private fun semester(
        start: LocalDate? = LocalDate.of(2026, 8, 1),
        end: LocalDate? = LocalDate.of(2027, 1, 31),
    ) = SettingsEntity(startEpochDay = start?.toEpochDay(), endEpochDay = end?.toEpochDay())

    private fun courses(): List<CourseEntity> =
        InitialSchedule.courses.mapIndexed { index, course -> course.copy(id = index + 1L) }

    private fun data(
        courses: List<CourseEntity> = courses(),
        tasks: List<TaskEntity> = emptyList(),
        notes: List<NoteEntity> = emptyList(),
        settings: SettingsEntity = semester(),
    ) = CampusSnapshot(courses = courses, tasks = tasks, notes = notes, settings = settings)

    // Penyimpanan tiruan dipakai bersama: FakeAgentWriteStore.kt

    private fun tools(store: FakeAgentWriteStore, undo: AgentUndoStack = AgentUndoStack()) =
        AgentWriteTools(store, clock, undo)

    private fun args(json: String): AgentJsonValue = checkNotNull(AgentJson.parse(json))

    private fun ready(preparation: AgentWritePreparation): AgentWritePreparation.Ready {
        assertTrue("diharapkan Ready, dapat $preparation", preparation is AgentWritePreparation.Ready)
        return preparation as AgentWritePreparation.Ready
    }

    private fun invalid(preparation: AgentWritePreparation): AgentWritePreparation.Invalid {
        assertTrue("diharapkan Invalid, dapat $preparation", preparation is AgentWritePreparation.Invalid)
        return preparation as AgentWritePreparation.Invalid
    }

    // ---------------------------------------------------------------- tambah_tugas

    @Test fun addTaskSavesOnlyAfterApply() = runTest {
        val store = FakeAgentWriteStore(data())
        val ventura = tools(store)

        val preparation = ventura.prepareTambahTugas(
            args("""{"judul":"Ringkasan akuntansi","mata_kuliah":"Pengantar Akuntansi","tenggat":"2026-11-20"}"""),
        )
        val proposal = ready(preparation).proposal
        assertEquals("tambah_tugas", proposal.tool)
        assertEquals("Tambah tugas \"Ringkasan akuntansi\" dengan tenggat 20 November 2026?", proposal.question)
        // Belum ada penulisan pada tahap validasi.
        assertEquals(0, store.taskSaves)
        assertTrue(store.data.tasks.isEmpty())

        val applied = ready(preparation).apply()
        assertEquals(1, store.taskSaves)
        val saved = store.data.tasks.single()
        assertEquals("Ringkasan akuntansi", saved.title)
        assertEquals(6L, saved.courseId) // Pengantar Akuntansi (id 6 sesuai urutan seed)
        assertEquals(LocalDate.of(2026, 11, 20).toEpochDay(), saved.dueEpochDay)
        assertFalse(saved.completed)
        assertTrue(applied.outcome.json.contains("tersimpan"))
        assertTrue(applied.traceSummary.startsWith("menambah tugas"))
    }

    @Test fun addTaskWithoutTitleAsksInsteadOfGuessing() = runTest {
        val store = FakeAgentWriteStore(data())
        val result = invalid(tools(store).prepareTambahTugas(args("""{"mata_kuliah":"Pengantar Akuntansi"}""")))
        assertTrue(result.question.contains("Judul tugasnya apa?"))
        assertEquals(0, store.taskSaves)
    }

    @Test fun unknownCourseNameIsAskedBack() = runTest {
        val store = FakeAgentWriteStore(data())
        val result = invalid(tools(store).prepareTambahTugas(args("""{"judul":"Latihan","mata_kuliah":"Statistika"}""")))
        assertTrue(result.question.contains("Statistika"))
        assertTrue(result.question.contains("Pengantar Akuntansi"))
        assertEquals(0, store.taskSaves)
    }

    @Test fun unreadableDueDateIsAskedBack() = runTest {
        val store = FakeAgentWriteStore(data())
        val result = invalid(tools(store).prepareTambahTugas(args("""{"judul":"Latihan","tenggat":"minggu depan"}""")))
        assertTrue(result.question.contains("belum saya pahami"))
        assertEquals(0, store.taskSaves)
    }

    @Test fun relativeDueDateIsUnderstood() = runTest {
        val store = FakeAgentWriteStore(data())
        val preparation = tools(store).prepareTambahTugas(args("""{"judul":"Latihan","tenggat":"besok"}"""))
        ready(preparation).apply()
        assertEquals(LocalDate.of(2026, 11, 12).toEpochDay(), store.data.tasks.single().dueEpochDay)
    }

    // ---------------------------------------------------------------- ubah_jadwal

    @Test fun changedScheduleMatchesManualEdit() = runTest {
        val store = FakeAgentWriteStore(data())
        val ventura = tools(store)

        val preparation = ventura.prepareUbahJadwal(
            args("""{"mata_kuliah":"Bahasa Indonesia","ruangan":"A.3b.9"}"""),
        )
        val proposal = ready(preparation).proposal
        assertEquals(AgentWriteKind.UPDATE, proposal.kind)
        assertEquals("Ubah ruangan Bahasa Indonesia → A.3b.9?", proposal.question)
        assertEquals(0, store.courseSaves)

        ready(preparation).apply()
        val saved = store.data.courses.single { it.name == "Bahasa Indonesia" }
        // Sama persis dengan hasil edit manual: id tetap, jam tetap, teks dirapikan.
        val manual = courses().single { it.name == "Bahasa Indonesia" }.copy(room = "A.3b.9")
        assertEquals(manual, saved)
    }

    @Test fun timeChangeProducesExpectedQuestion() = runTest {
        val store = FakeAgentWriteStore(data())
        val preparation = tools(store).prepareUbahJadwal(
            args("""{"mata_kuliah":"Bahasa Indonesia","mulai":"13:00","selesai":"14:40"}"""),
        )
        val proposal = ready(preparation).proposal
        assertEquals("Ubah jam Bahasa Indonesia → 13:00–14:40?", proposal.question)
        // 13:00–14:40 beririsan dengan Pengantar Akuntansi 12:30–15:00.
        assertTrue(proposal.warnings.any { it.contains("Bentrok dengan Pengantar Akuntansi") })
    }

    @Test fun impossibleTimeRangeIsRejectedBeforeWrite() = runTest {
        val store = FakeAgentWriteStore(data())
        val result = invalid(
            tools(store).prepareUbahJadwal(
                args("""{"mata_kuliah":"Bahasa Indonesia","mulai":"14:40","selesai":"13:00"}"""),
            ),
        )
        assertTrue(result.question.contains("tidak mungkin"))
        assertEquals(0, store.courseSaves)
    }

    @Test fun unknownCourseNeedsCompleteData() = runTest {
        val store = FakeAgentWriteStore(data())
        val result = invalid(tools(store).prepareUbahJadwal(args("""{"mata_kuliah":"Statistika","hari":"Kamis"}""")))
        assertTrue(result.question.contains("belum ada di jadwal"))
        assertTrue(result.question.contains("jam mulai"))
        assertTrue(result.question.contains("kode ruang"))
        assertEquals(0, store.courseSaves)
    }

    @Test fun newCourseIsCreatedWithDefaults() = runTest {
        val store = FakeAgentWriteStore(data())
        val preparation = tools(store).prepareUbahJadwal(
            args(
                """{"mata_kuliah":"Statistika","hari":"Jumat","mulai":"08:00","selesai":"09:40","ruangan":"B.2.1"}""",
            ),
        )
        val proposal = ready(preparation).proposal
        assertEquals(AgentWriteKind.CREATE, proposal.kind)
        assertEquals("Tambah mata kuliah Statistika → Jumat 08:00–09:40, B.2.1?", proposal.question)
        ready(preparation).apply()
        val created = store.data.courses.single { it.name == "Statistika" }
        assertEquals("01", created.section)
        assertTrue(created.enabled)
        assertEquals(5, created.weekday)
    }

    @Test fun ambiguousNameAsksWhichOne() = runTest {
        val store = FakeAgentWriteStore(
            data(
                courses = listOf(
                    CourseEntity(id = 1, name = "Akuntansi Biaya", weekday = 2, startMinute = 480, endMinute = 600, room = "A.1"),
                    CourseEntity(id = 2, name = "Akuntansi Manajemen", weekday = 4, startMinute = 480, endMinute = 600, room = "A.2"),
                ),
            ),
        )
        val result = invalid(tools(store).prepareUbahJadwal(args("""{"mata_kuliah":"Akuntansi","ruangan":"A.9"}""")))
        assertTrue(result.question.contains("Akuntansi Biaya"))
        assertTrue(result.question.contains("Akuntansi Manajemen"))
        assertEquals(0, store.courseSaves)
    }

    @Test fun switchingDayToEmptyDayDoesNotWarnAboutConflict() = runTest {
        val store = FakeAgentWriteStore(data())
        val preparation = tools(store).prepareUbahJadwal(
            args("""{"mata_kuliah":"Bahasa Indonesia","hari":"Sabtu"}"""),
        )
        val proposal = ready(preparation).proposal
        assertTrue(proposal.warnings.none { it.contains("Bentrok") })
        assertEquals("Ubah hari Bahasa Indonesia → Sabtu?", proposal.question)
    }

    @Test fun finishedSemesterIsWarned() = runTest {
        val store = FakeAgentWriteStore(
            data(
                settings = semester(start = LocalDate.of(2025, 8, 1), end = LocalDate.of(2025, 12, 31)),
            ),
        )
        val preparation = tools(store).prepareUbahJadwal(
            args("""{"mata_kuliah":"Bahasa Indonesia","ruangan":"A.3b.9"}"""),
        )
        val proposal = ready(preparation).proposal
        assertTrue(proposal.warnings.any { it.contains("Semester sudah berakhir") })
    }

    @Test fun unconfirmedSemesterIsMarkedAsEstimate() = runTest {
        val store = FakeAgentWriteStore(data(settings = SettingsEntity(startEpochDay = null, endEpochDay = null)))
        val proposal = ready(
            tools(store).prepareUbahJadwal(args("""{"mata_kuliah":"Bahasa Indonesia","ruangan":"A.3b.9"}""")),
        ).proposal
        assertTrue(proposal.warnings.any { it.contains("pratiraan") })
    }

    @Test fun disablingCourseIsAnUpdate() = runTest {
        val store = FakeAgentWriteStore(data())
        val preparation = tools(store).prepareUbahJadwal(args("""{"mata_kuliah":"Bahasa Indonesia","aktif":false}"""))
        assertEquals("Ubah status Bahasa Indonesia → nonaktif?", ready(preparation).proposal.question)
        ready(preparation).apply()
        assertFalse(store.data.courses.single { it.name == "Bahasa Indonesia" }.enabled)
    }

    @Test fun noChangeNeedsNoConfirmation() = runTest {
        val store = FakeAgentWriteStore(data())
        val result = tools(store).prepareUbahJadwal(args("""{"mata_kuliah":"Bahasa Indonesia","ruangan":"A.3b.2"}"""))
        assertTrue(result is AgentWritePreparation.Unchanged)
        assertEquals(0, store.courseSaves)
    }

    // ---------------------------------------------------------------- tambah_catatan

    @Test fun noteIsSavedWithUndo() = runTest {
        val store = FakeAgentWriteStore(data())
        val ventura = tools(store)
        val preparation = ventura.prepareTambahCatatan(args("""{"judul":"Jurnal penyesuaian","isi":"Ayat jurnal 31 Desember"}"""))
        val proposal = ready(preparation).proposal
        assertEquals("Simpan catatan \"Jurnal penyesuaian\"?", proposal.question)
        ready(preparation).apply()
        assertEquals("Ayat jurnal 31 Desember", store.data.notes.single().body)

        val undo = checkNotNull(ventura.undoLast())
        assertTrue(undo.detail.contains("Jurnal penyesuaian"))
        assertTrue(store.data.notes.isEmpty())
    }

    // ---------------------------------------------------------------- tandai_tugas_selesai

    @Test fun completingTaskCanBeUndone() = runTest {
        val store = FakeAgentWriteStore(
            data(
                tasks = listOf(
                    TaskEntity(id = 1, title = "Laporan Pajak", dueEpochDay = LocalDate.of(2026, 11, 20).toEpochDay()),
                    TaskEntity(id = 2, title = "Latihan Akuntansi"),
                ),
            ),
        )
        val ventura = tools(store)
        val preparation = ventura.prepareTandaiSelesai(args("""{"judul_tugas":"Laporan Pajak"}"""))
        assertEquals("Tandai tugas \"Laporan Pajak\" selesai?", ready(preparation).proposal.question)
        ready(preparation).apply()
        assertTrue(store.data.tasks.single { it.id == 1L }.completed)

        ventura.undoLast()
        assertFalse(store.data.tasks.single { it.id == 1L }.completed)
    }

    @Test fun alreadyCompletedTaskIsUnchanged() = runTest {
        val store = FakeAgentWriteStore(data(tasks = listOf(TaskEntity(id = 1, title = "Laporan Pajak", completed = true))))
        val result = tools(store).prepareTandaiSelesai(args("""{"judul_tugas":"Laporan Pajak"}"""))
        assertTrue(result is AgentWritePreparation.Unchanged)
        assertEquals(0, store.taskSaves)
    }

    @Test fun unknownTaskAsksWithOpenTitles() = runTest {
        val store = FakeAgentWriteStore(data(tasks = listOf(TaskEntity(id = 1, title = "Laporan Pajak"))))
        val result = invalid(tools(store).prepareTandaiSelesai(args("""{"judul_tugas":"Kuis"}""")))
        assertTrue(result.question.contains("Laporan Pajak"))
        assertEquals(0, store.taskSaves)
    }

    @Test fun ambiguousTaskAsksWhichOne() = runTest {
        val store = FakeAgentWriteStore(
            data(tasks = listOf(TaskEntity(id = 1, title = "Latihan Akuntansi"), TaskEntity(id = 2, title = "Latihan Pajak"))),
        )
        val result = invalid(tools(store).prepareTandaiSelesai(args("""{"judul_tugas":"Latihan"}""")))
        assertTrue(result.question.contains("Latihan Pajak"))
        assertTrue(result.question.contains("Latihan Akuntansi"))
    }

    // ---------------------------------------------------------------- undangan & keamanan

    @Test fun writeToolDoesNotSaveWhenCalledDirectly() = runTest {
        val store = FakeAgentWriteStore(data())
        val ventura = tools(store)
        val tool = checkNotNull(ventura.registry.find("tambah_tugas"))
        val outcome = tool.execute(args("""{"judul":"Harus ditolak"}"""))
        assertTrue(outcome.json.contains("error"))
        assertEquals(0, store.taskSaves)
    }

    @Test fun onlyNonDestructiveWriteToolsExist() = runTest {
        val store = FakeAgentWriteStore(data())
        val names = tools(store).registry.names()
        assertEquals(setOf("tambah_tugas", "ubah_jadwal", "tambah_catatan", "tandai_tugas_selesai"), names)
        assertTrue(names.none { it.contains("hapus") || it.contains("delete") })
    }

    @Test fun undoStackIsBoundedAndLastInFirstOut() {
        val undo = AgentUndoStack(capacity = 2)
        undo.push(AgentUndoAction.RemoveTask(1, "A"))
        undo.push(AgentUndoAction.RemoveTask(2, "B"))
        undo.push(AgentUndoAction.RemoveTask(3, "C"))
        assertEquals(2, undo.size)
        assertEquals(3L, (checkNotNull(undo.pop()) as AgentUndoAction.RemoveTask).id)
        assertEquals(2L, (checkNotNull(undo.pop()) as AgentUndoAction.RemoveTask).id)
        assertNull(undo.pop())
    }

    @Test fun undoWithoutActionReturnsNull() = runTest {
        val ventura = tools(FakeAgentWriteStore(data()))
        assertNull(ventura.peekUndo())
        assertNull(ventura.undoLast())
    }

    @Test fun undoOfCreatedCourseRemovesOnlyThatRow() = runTest {
        val store = FakeAgentWriteStore(data())
        val ventura = tools(store)
        val before = store.data.courses.size
        val preparation = ventura.prepareUbahJadwal(
            args("""{"mata_kuliah":"Statistika","hari":"Jumat","mulai":"08:00","selesai":"09:40","ruangan":"B.2.1"}"""),
        )
        ready(preparation).apply()
        assertEquals(before + 1, store.data.courses.size)
        val outcome = checkNotNull(ventura.undoLast())
        assertEquals("Perubahan dibatalkan", outcome.title)
        assertEquals(before, store.data.courses.size)
        assertTrue(store.data.courses.none { it.name == "Statistika" })
        assertNotNull(store.data.courses.firstOrNull { it.name == "Bahasa Indonesia" })
    }

    @Test fun clockAndDateParsingAcceptRealModelInput() {
        assertEquals(13 * 60, parseClock("13:00"))
        assertEquals(13 * 60, parseClock("1300"))
        assertEquals(13 * 60, parseClock("13"))
        assertEquals(9 * 60 + 45, parseClock("945"))
        assertEquals(13 * 60, parseClock("13.00"))
        assertEquals(13 * 60, parseClock("13:00:00"))
        assertNull(parseClock("25:00"))
        assertNull(parseClock("jam satu"))

        val today = LocalDate.of(2026, 11, 11)
        assertEquals(today, parseDate("hari ini", today))
        assertEquals(today.plusDays(1), parseDate("besok", today))
        assertEquals(today.plusDays(2), parseDate("lusa", today))
        assertEquals(LocalDate.of(2026, 11, 20), parseDate("2026-11-20", today))
        assertEquals(LocalDate.of(2026, 11, 20), parseDate("20/11/2026", today))
        assertEquals(LocalDate.of(2026, 11, 20), parseDate("20-11-2026", today))
        assertNull(parseDate("31/02/2026", today))
    }
}