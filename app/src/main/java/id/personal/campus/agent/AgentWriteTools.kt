package id.personal.campus.agent

import id.personal.campus.data.CourseEntity
import id.personal.campus.data.NoteEntity
import id.personal.campus.data.SettingsEntity
import id.personal.campus.data.TaskEntity
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Tool tulis untuk Asisten AI: `tambah_tugas`, `ubah_jadwal`, `tambah_catatan`,
 * `tandai_tugas_selesai`.
 *
 * Aturan yang dijaga:
 * 1. **Validasi lokal dulu**, memakai aturan yang sama dengan formulir manual
 *    (`Editors.kt` + `CampusRepository`): nama & ruang tidak kosong, hari 1–7,
 *    `startMinute < endMinute`, dan rentang semester dihormati.
 * 2. **Tidak ada penulisan sebelum Setuju.** [prepare] hanya memeriksa; penyimpanan terjadi
 *    di `AgentWritePreparation.Ready.apply` yang dipanggil setelah user menyetujui.
 * 3. **Argumen tidak lengkap → bertanya.** Tidak ada nilai default yang dikarang untuk jam,
 *    hari, atau ruangan.
 * 4. **Satu aksi = satu Undo** ([AgentUndoStack]) dan tidak ada tool hapus.
 *
 * Bebas `android.*`: penyimpanan lewat [AgentWriteStore], waktu lewat [Clock].
 */
class AgentWriteTools(
    private val store: AgentWriteStore,
    private val clock: Clock = Clock.systemUTC(),
    private val undo: AgentUndoStack = AgentUndoStack(),
) {

    val tools: List<AgentTool> = listOf(
        SimpleAgentTool(
            name = "tambah_tugas",
            description = "Menambahkan tugas baru. Wajib disetujui user sebelum disimpan.",
            parametersJson = """{"type":"object","properties":{"judul":{"type":"string","description":"Judul tugas."},"mata_kuliah":{"type":"string","description":"Nama mata kuliah terkait (opsional)."},"tenggat":{"type":"string","description":"Tanggal tenggat, format YYYY-MM-DD (opsional)."},"catatan":{"type":"string","description":"Keterangan tugas (opsional)."}},"required":["judul"]}""",
            writesData = true,
            prepareHandler = { arguments -> prepareTambahTugas(arguments) },
        ) { arguments -> notPrepared("tambah_tugas") },
        SimpleAgentTool(
            name = "ubah_jadwal",
            description = "Mengubah jadwal mata kuliah yang sudah ada (hari, jam, ruangan, aktif) " +
                "atau menambah mata kuliah baru bila semua data wajib sudah lengkap. " +
                "Wajib disetujui user sebelum disimpan.",
            parametersJson = """{"type":"object","properties":{"mata_kuliah":{"type":"string","description":"Nama mata kuliah yang sudah ada, atau nama mata kuliah baru."},"hari":{"type":"string","description":"Hari: Senin–Minggu atau angka 1–7."},"mulai":{"type":"string","description":"Jam mulai 24 jam, mis. 13:00."},"selesai":{"type":"string","description":"Jam selesai 24 jam, mis. 14:40."},"ruangan":{"type":"string","description":"Kode ruang, mis. A.3b.6."},"kelas":{"type":"string","description":"Kelas/seksi, mis. 01 (opsional)."},"aktif":{"type":"boolean","description":"true mengaktifkan, false menonaktifkan (opsional)."},"catatan_ruangan":{"type":"string","description":"Keterangan ruang (opsional)."}},"required":["mata_kuliah"]}""",
            writesData = true,
            prepareHandler = { arguments -> prepareUbahJadwal(arguments) },
        ) { arguments -> notPrepared("ubah_jadwal") },
        SimpleAgentTool(
            name = "tambah_catatan",
            description = "Menyimpan catatan kuliah baru. Wajib disetujui user sebelum disimpan.",
            parametersJson = """{"type":"object","properties":{"judul":{"type":"string","description":"Judul catatan."},"isi":{"type":"string","description":"Isi catatan (opsional)."}},"required":["judul"]}""",
            writesData = true,
            prepareHandler = { arguments -> prepareTambahCatatan(arguments) },
        ) { arguments -> notPrepared("tambah_catatan") },
        SimpleAgentTool(
            name = "tandai_tugas_selesai",
            description = "Menandai satu tugas sebagai selesai. Wajib disetujui user.",
            parametersJson = """{"type":"object","properties":{"judul_tugas":{"type":"string","description":"Judul tugas yang sudah selesai."}},"required":["judul_tugas"]}""",
            writesData = true,
            prepareHandler = { arguments -> prepareTandaiSelesai(arguments) },
        ) { arguments -> notPrepared("tandai_tugas_selesai") },
    )

    val registry: AgentToolRegistry = AgentToolRegistry(tools)

    /** Undo terakhir yang masih tersedia, untuk label tombol "Batalkan". */
    fun peekUndo(): AgentUndoAction? = undo.peek()

    /**
     * Membatalkan aksi tulis terakhir. `null` bila tidak ada yang bisa dibatalkan.
     * Satu aksi = satu pembatalan; entri lama dibuang saat melewati batas tumpukan.
     */
    suspend fun undoLast(): AgentUndoOutcome? {
        val action = undo.pop() ?: return null
        when (action) {
            is AgentUndoAction.SaveCourse -> store.saveCourse(action.course)
            is AgentUndoAction.SaveTask -> store.saveTask(action.task)
            is AgentUndoAction.SaveNote -> store.saveNote(action.note)
            is AgentUndoAction.RemoveCourse -> store.removeCourse(action.id)
            is AgentUndoAction.RemoveTask -> store.removeTask(action.id)
            is AgentUndoAction.RemoveNote -> store.removeNote(action.id)
        }
        return AgentUndoOutcome(title = "Perubahan dibatalkan", detail = action.label)
    }

    // -----------------------------------------------------------------------
    // tambah_tugas
    // -----------------------------------------------------------------------

    suspend fun prepareTambahTugas(arguments: AgentJsonValue?): AgentWritePreparation {
        val data = store.snapshot()
        val title = firstString(arguments, "judul", "judul_tugas", "tugas", "nama").trim()
        if (title.isBlank()) {
            return ask("Judul tugasnya apa? Sebutkan judulnya supaya saya bisa membuat draf yang benar.")
        }

        val courseName = firstString(arguments, "mata_kuliah", "matkul", "mata_kuliah_nama").trim()
        val course = if (courseName.isBlank()) null else {
            when (val found = findCourse(data.courses, courseName)) {
                is Lookup.Missing -> return ask(
                    "Mata kuliah \"$courseName\" tidak ada di data. Pilih salah satu: " +
                        data.courses.joinToString(", ") { it.name } + ". Atau sebutkan tanpa mata kuliah.",
                )
                is Lookup.Ambiguous -> return ask(
                    "Ada ${found.courses.size} mata kuliah yang cocok dengan \"$courseName\": " +
                        found.courses.joinToString(", ") { it.name } + ". Sebutkan nama lengkapnya.",
                )
                is Lookup.Hit -> found.course
            }
        }

        val dueRaw = firstString(arguments, "tenggat", "tenggat_hari", "jatuh_tempo", "deadline").trim()
        val settings = data.settings
        val today = today(settings)
        val due = if (dueRaw.isBlank()) null else {
            parseDate(dueRaw, today)
                ?: return ask(
                    "Tanggal tenggat \"$dueRaw\" belum saya pahami. Pakai format YYYY-MM-DD " +
                        "(mis. ${today.plusDays(7)}), atau \"hari ini\", \"besok\", atau \"lusa\".",
                )
        }
        val notes = firstString(arguments, "catatan", "keterangan", "catatan_tugas").trim()

        val draft = TaskEntity(
            title = title,
            courseId = course?.id,
            dueEpochDay = due?.toEpochDay(),
            notes = notes,
            completed = false,
        )
        val warnings = buildList {
            if (due != null && due.isBefore(today)) add("Tenggat ${dateText(due)} sudah lewat.")
        }
        val detail = listOfNotNull(
            course?.let { "Mata kuliah ${it.name}" },
            due?.let { "Tenggat ${dateText(it)}" },
            notes.takeIf { it.isNotBlank() }?.let { "Catatan: ${clip(it, 80)}" },
        ).joinToString(" · ").ifBlank { "Tanpa tenggat dan tanpa mata kuliah" }

        val proposal = AgentWriteProposal(
            tool = "tambah_tugas",
            title = "Tambah tugas",
            question = buildString {
                append("Tambah tugas \"$title\"")
                if (due != null) append(" dengan tenggat ${dateText(due)}")
                append("?")
            },
            detail = detail,
            kind = AgentWriteKind.CREATE,
            changes = listOf(
                AgentFieldChange("Judul", null, title),
                AgentFieldChange("Mata kuliah", null, course?.name ?: "Tanpa mata kuliah"),
                AgentFieldChange("Tenggat", null, due?.let(::dateText) ?: "Tanpa tenggat"),
            ),
            warnings = warnings,
        )
        return AgentWritePreparation.Ready(proposal) {
            val saved = store.saveTask(draft)
            undo.push(AgentUndoAction.RemoveTask(saved.id, saved.title))
            AppliedWrite(
                AgentToolOutcome(
                    tool = "tambah_tugas",
                    json = AgentJson.write(
                        objOf(
                            "status" to strOf("tersimpan"),
                            "tool" to strOf("tambah_tugas"),
                            "pesan" to strOf("Tugas \"${saved.title}\" tersimpan."),
                            "data" to taskNode(saved, data.courses, settings),
                            "peringatan" to AgentJsonValue.Arr(warnings.map(::strOf)),
                        ),
                    ),
                    summary = "menambah tugas ${saved.title}",
                    itemCount = 1,
                ),
            )
        }
    }

    // -----------------------------------------------------------------------
    // ubah_jadwal
    // -----------------------------------------------------------------------

    suspend fun prepareUbahJadwal(arguments: AgentJsonValue?): AgentWritePreparation {
        val data = store.snapshot()
        val settings = data.settings
        val today = today(settings)
        val rawName = firstString(arguments, "mata_kuliah", "matkul", "nama").trim()
        if (rawName.isBlank()) {
            return ask("Mata kuliah mana yang mau diubah? Sebutkan namanya, atau beri nama baru bila mau menambah.")
        }

        val existing = when (val found = findCourse(data.courses, rawName)) {
            is Lookup.Ambiguous -> return ask(
                "Ada ${found.courses.size} mata kuliah yang cocok dengan \"$rawName\": " +
                    found.courses.joinToString(", ") { it.name } + ". Sebutkan nama lengkapnya.",
            )
            is Lookup.Missing -> null
            is Lookup.Hit -> found.course
        }

        val weekdayArg = firstString(arguments, "hari", "day").trim()
        val startArg = firstString(arguments, "mulai", "jam_mulai", "start").trim()
        val endArg = firstString(arguments, "selesai", "jam_selesai", "end").trim()
        val roomArg = firstString(arguments, "ruangan", "ruang", "room").trim()
        val sectionArg = firstString(arguments, "kelas", "seksi", "section").trim()
        val noteArg = firstString(arguments, "catatan_ruangan", "keterangan_ruangan").trim()
        val enabledArg = arguments.boolOrNull("aktif") ?: arguments.boolOrNull("enabled")

        val weekday = if (weekdayArg.isBlank()) null else {
            parseWeekday(weekdayArg)?.value
                ?: return ask(
                    "Hari \"$weekdayArg\" belum saya pahami. Sebutkan Senin sampai Minggu atau angka 1–7.",
                )
        }
        val start = if (startArg.isBlank()) null else {
            parseClock(startArg) ?: return ask(
                "Jam mulai \"$startArg\" belum saya pahami. Pakai format 24 jam, mis. 13:00.",
            )
        }
        val end = if (endArg.isBlank()) null else {
            parseClock(endArg) ?: return ask(
                "Jam selesai \"$endArg\" belum saya pahami. Pakai format 24 jam, mis. 14:40.",
            )
        }

        if (existing == null) {
            val missing = buildList {
                if (weekday == null) add("hari")
                if (start == null) add("jam mulai")
                if (end == null) add("jam selesai")
                if (roomArg.isBlank()) add("kode ruang")
            }
            if (missing.isNotEmpty()) {
                return ask(
                    "Mata kuliah \"$rawName\" belum ada di jadwal. Untuk menambahkannya saya perlu " +
                        missing.joinToString(", ") + ". Boleh disebutkan?",
                )
            }
            checkNotNull(start); checkNotNull(end); checkNotNull(weekday)
            if (start >= end) {
                return ask(
                    "Jam ${clockText(start)}–${clockText(end)} tidak mungkin: jam selesai harus " +
                        "setelah jam mulai pada hari yang sama. Jam berapa yang benar?",
                )
            }
            val draft = CourseEntity(
                name = rawName,
                weekday = weekday,
                startMinute = start,
                endMinute = end,
                room = roomArg,
                section = sectionArg.ifBlank { "01" },
                roomNote = noteArg,
                enabled = enabledArg ?: true,
            )
            val warnings = buildList {
                addAll(conflictWarnings(data.courses, draft, selfId = null))
                addAll(semesterWarnings(settings, weekday, today))
            }
            val proposal = AgentWriteProposal(
                tool = "ubah_jadwal",
                title = "Tambah mata kuliah",
                question = "Tambah mata kuliah ${draft.name} → ${dayName(weekday)} " +
                    "${clockText(start)}–${clockText(end)}, ${draft.room}?",
                detail = "${dayName(weekday)} ${clockText(start)}–${clockText(end)} · ${draft.room}",
                kind = AgentWriteKind.CREATE,
                changes = listOf(
                    AgentFieldChange("Mata kuliah", null, draft.name),
                    AgentFieldChange("Hari", null, dayName(weekday)),
                    AgentFieldChange("Jam", null, "${clockText(start)}–${clockText(end)}"),
                    AgentFieldChange("Ruangan", null, draft.room),
                ),
                warnings = warnings,
            )
            return AgentWritePreparation.Ready(proposal) {
                val saved = store.saveCourse(draft)
                undo.push(AgentUndoAction.RemoveCourse(saved.id, saved.name))
                AppliedWrite(savedCourseOutcome("ubah_jadwal", "Mata kuliah ${saved.name} ditambahkan.", saved, warnings))
            }
        }

        // Ubah mata kuliah yang sudah ada: hanya field yang dikirim model yang berubah.
        val updated = existing.copy(
            weekday = weekday ?: existing.weekday,
            startMinute = start ?: existing.startMinute,
            endMinute = end ?: existing.endMinute,
            room = roomArg.ifBlank { existing.room },
            section = sectionArg.ifBlank { existing.section },
            roomNote = noteArg.ifBlank { existing.roomNote },
            enabled = enabledArg ?: existing.enabled,
        )
        if (updated.startMinute >= updated.endMinute) {
            return ask(
                "Jam ${clockText(updated.startMinute)}–${clockText(updated.endMinute)} tidak mungkin " +
                    "untuk ${existing.name}: jam selesai harus setelah jam mulai. Jam berapa yang benar?",
            )
        }
        val changes = changesBetween(existing, updated)
        if (changes.isEmpty()) {
            return AgentWritePreparation.Unchanged(
                AgentToolOutcome(
                    tool = "ubah_jadwal",
                    json = AgentJson.write(
                        objOf(
                            "status" to strOf("tidak_ada_perubahan"),
                            "pesan" to strOf("Jadwal ${existing.name} sudah seperti itu; tidak ada yang diubah."),
                        ),
                    ),
                    summary = "memeriksa jadwal ${existing.name}",
                ),
            )
        }
        val warnings = buildList {
            addAll(conflictWarnings(data.courses, updated, selfId = existing.id))
            addAll(semesterWarnings(settings, updated.weekday, today))
        }
        val labels = joinLabels(changes.map { it.label.lowercase(Locale.forLanguageTag("id-ID")) })
        val values = changes.joinToString(", ") { it.after }
        val proposal = AgentWriteProposal(
            tool = "ubah_jadwal",
            title = "Ubah jadwal",
            question = "Ubah $labels ${existing.name} → $values?",
            detail = "${dayName(updated.weekday)} ${clockText(updated.startMinute)}–${clockText(updated.endMinute)}" +
                " · ${updated.room}${if (updated.enabled) "" else " · nonaktif"}",
            kind = AgentWriteKind.UPDATE,
            changes = changes,
            warnings = warnings,
        )
        return AgentWritePreparation.Ready(proposal) {
            val saved = store.saveCourse(updated)
            undo.push(AgentUndoAction.SaveCourse(existing))
            AppliedWrite(savedCourseOutcome("ubah_jadwal", "Jadwal ${saved.name} diperbarui.", saved, warnings))
        }
    }

    // -----------------------------------------------------------------------
    // tambah_catatan
    // -----------------------------------------------------------------------

    suspend fun prepareTambahCatatan(arguments: AgentJsonValue?): AgentWritePreparation {
        val data = store.snapshot()
        val title = firstString(arguments, "judul", "judul_catatan", "nama").trim()
        if (title.isBlank()) {
            return ask("Judul catatannya apa? Sebutkan judulnya supaya mudah dicari nanti.")
        }
        val body = firstString(arguments, "isi", "isi_catatan", "catatan", "body").trim()
        val existingTitle = data.notes.firstOrNull { normalize(it.title) == normalize(title) }
        val draft = NoteEntity(title = title, body = body)
        val warnings = buildList {
            if (existingTitle != null) {
                add("Sudah ada catatan berjudul sama (\"${existingTitle.title}\"); keduanya akan tersimpan terpisah.")
            }
        }
        val proposal = AgentWriteProposal(
            tool = "tambah_catatan",
            title = if (existingTitle == null) "Tambah catatan" else "Tambah catatan baru",
            question = "Simpan catatan \"$title\"?",
            detail = body.takeIf { it.isNotBlank() }?.let { clip(it, 120) } ?: "Tanpa isi",
            kind = AgentWriteKind.CREATE,
            changes = listOf(
                AgentFieldChange("Judul", null, title),
                AgentFieldChange("Isi", null, if (body.isBlank()) "kosong" else "${body.length} karakter"),
            ),
            warnings = warnings,
        )
        return AgentWritePreparation.Ready(proposal) {
            val saved = store.saveNote(draft)
            undo.push(AgentUndoAction.RemoveNote(saved.id, saved.title))
            AppliedWrite(
                AgentToolOutcome(
                    tool = "tambah_catatan",
                    json = AgentJson.write(
                        objOf(
                            "status" to strOf("tersimpan"),
                            "tool" to strOf("tambah_catatan"),
                            "pesan" to strOf("Catatan \"${saved.title}\" tersimpan."),
                            "data" to objOf("judul" to strOf(saved.title), "panjang_isi" to numOf(saved.body.length)),
                            "peringatan" to AgentJsonValue.Arr(warnings.map(::strOf)),
                        ),
                    ),
                    summary = "menambah catatan ${saved.title}",
                    itemCount = 1,
                ),
            )
        }
    }

    // -----------------------------------------------------------------------
    // tandai_tugas_selesai
    // -----------------------------------------------------------------------

    suspend fun prepareTandaiSelesai(arguments: AgentJsonValue?): AgentWritePreparation {
        val data = store.snapshot()
        val query = firstString(arguments, "judul_tugas", "judul", "tugas", "nama").trim()
        if (query.isBlank()) {
            return ask("Tugas mana yang sudah selesai? Sebutkan judulnya.")
        }
        val matches = data.tasks.filter { normalize(it.title).contains(normalize(query)) }
        if (matches.isEmpty()) {
            val open = data.tasks.filter { !it.completed }.take(5).map { it.title }
            return ask(
                "Tidak ada tugas yang cocok dengan \"$query\". " +
                    if (open.isEmpty()) "Daftar tugas belum selesai saat ini kosong."
                    else "Yang belum selesai: ${open.joinToString(", ")}.",
            )
        }
        val open = matches.filter { !it.completed }
        if (open.isEmpty()) {
            val done = matches.first()
            return AgentWritePreparation.Unchanged(
                AgentToolOutcome(
                    tool = "tandai_tugas_selesai",
                    json = AgentJson.write(
                        objOf(
                            "status" to strOf("sudah_selesai"),
                            "pesan" to strOf("Tugas \"${done.title}\" memang sudah ditandai selesai."),
                        ),
                    ),
                    summary = "memeriksa tugas ${done.title}",
                ),
            )
        }
        if (open.size > 1) {
            return ask(
                "Ada ${open.size} tugas yang cocok dengan \"$query\": " +
                    open.joinToString(", ") { it.title } + ". Yang mana yang selesai?",
            )
        }
        val task = open.single()
        val proposal = AgentWriteProposal(
            tool = "tandai_tugas_selesai",
            title = "Tandai selesai",
            question = "Tandai tugas \"${task.title}\" selesai?",
            detail = listOfNotNull(
                data.courses.firstOrNull { it.id == task.courseId }?.let { "Mata kuliah ${it.name}" },
                task.dueEpochDay?.let { "Tenggat ${dateText(LocalDate.ofEpochDay(it))}" },
            ).joinToString(" · ").ifBlank { "Tanpa mata kuliah dan tanpa tenggat" },
            kind = AgentWriteKind.UPDATE,
            changes = listOf(AgentFieldChange("Status", "belum selesai", "selesai")),
        )
        return AgentWritePreparation.Ready(proposal) {
            val saved = store.saveTask(task.copy(completed = true))
            undo.push(AgentUndoAction.SaveTask(task))
            AppliedWrite(
                AgentToolOutcome(
                    tool = "tandai_tugas_selesai",
                    json = AgentJson.write(
                        objOf(
                            "status" to strOf("tersimpan"),
                            "tool" to strOf("tandai_tugas_selesai"),
                            "pesan" to strOf("Tugas \"${saved.title}\" ditandai selesai."),
                            "data" to taskNode(saved, data.courses, data.settings),
                        ),
                    ),
                    summary = "menandai tugas ${saved.title} selesai",
                    itemCount = 1,
                ),
            )
        }
    }

    // -----------------------------------------------------------------------
    // Validasi & bantuan
    // -----------------------------------------------------------------------

    private sealed interface Lookup {
        data class Hit(val course: CourseEntity) : Lookup
        data class Ambiguous(val courses: List<CourseEntity>) : Lookup
        data object Missing : Lookup
    }

    /**
     * Mencocokkan nama mata kuliah. Persis dulu, baru "mengandung", supaya
     * "Bahasa Indonesia" tidak ikut cocok ke dua mata kuliah lain.
     */
    private fun findCourse(courses: List<CourseEntity>, query: String): Lookup {
        val key = normalize(query)
        if (key.isEmpty()) return Lookup.Missing
        val exact = courses.filter { normalize(it.name) == key }
        if (exact.size == 1) return Lookup.Hit(exact.single())
        if (exact.size > 1) return Lookup.Ambiguous(exact)
        val partial = courses.filter { normalize(it.name).contains(key) }
        return when (partial.size) {
            0 -> Lookup.Missing
            1 -> Lookup.Hit(partial.single())
            else -> Lookup.Ambiguous(partial)
        }
    }

    /** Bentrok jam: jadwal aktif lain di hari yang sama yang rentangnya beririsan. */
    private fun conflictWarnings(
        courses: List<CourseEntity>,
        candidate: CourseEntity,
        selfId: Long?,
    ): List<String> = courses
        .filter {
            it.enabled && it.id != selfId && it.weekday == candidate.weekday &&
                it.startMinute < candidate.endMinute && candidate.startMinute < it.endMinute
        }
        .sortedBy { it.startMinute }
        .map {
            "Bentrok dengan ${it.name} (${clockText(it.startMinute)}–${clockText(it.endMinute)}) " +
                "di hari yang sama."
        }

    /** Rentang semester dihormati: beri tahu bila perubahan tidak akan pernah muncul. */
    private fun semesterWarnings(settings: SettingsEntity, weekday: Int, today: LocalDate): List<String> {
        val start = settings.startDate
        val end = settings.endDate
        if (!settings.semesterConfirmed || start == null || end == null) {
            return listOf("Tanggal semester belum diisi di Setelan, jadi ini masih pratiraan sampai tanggalnya dikonfirmasi.")
        }
        if (end.isBefore(today)) {
            return listOf("Semester sudah berakhir pada ${dateText(end)}, jadi perubahan ini tidak muncul di jadwal berjalan.")
        }
        val next = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(weekday)))
        if (next.isAfter(end)) {
            return listOf("Hari ${dayName(weekday)} tidak akan muncul lagi sebelum semester berakhir (${dateText(end)}).")
        }
        return emptyList()
    }

    private fun changesBetween(before: CourseEntity, after: CourseEntity): List<AgentFieldChange> = buildList {
        if (before.name != after.name) add(AgentFieldChange("Nama", before.name, after.name))
        if (before.weekday != after.weekday) {
            add(AgentFieldChange("Hari", dayName(before.weekday), dayName(after.weekday)))
        }
        if (before.startMinute != after.startMinute || before.endMinute != after.endMinute) {
            add(
                AgentFieldChange(
                    "Jam",
                    "${clockText(before.startMinute)}–${clockText(before.endMinute)}",
                    "${clockText(after.startMinute)}–${clockText(after.endMinute)}",
                ),
            )
        }
        if (before.room != after.room) add(AgentFieldChange("Ruangan", before.room, after.room))
        if (before.section != after.section) add(AgentFieldChange("Kelas", before.section, after.section))
        if (before.roomNote != after.roomNote) {
            add(AgentFieldChange("Catatan ruangan", before.roomNote.ifBlank { "kosong" }, after.roomNote.ifBlank { "kosong" }))
        }
        if (before.enabled != after.enabled) {
            add(AgentFieldChange("Status", if (before.enabled) "aktif" else "nonaktif", if (after.enabled) "aktif" else "nonaktif"))
        }
    }

    private fun savedCourseOutcome(
        tool: String,
        message: String,
        saved: CourseEntity,
        warnings: List<String>,
    ): AgentToolOutcome = AgentToolOutcome(
        tool = tool,
        json = AgentJson.write(
            objOf(
                "status" to strOf("tersimpan"),
                "tool" to strOf(tool),
                "pesan" to strOf(message),
                "data" to objOf(
                    "matkul" to strOf(saved.name),
                    "hari" to strOf(dayName(saved.weekday)),
                    "mulai" to strOf(clockText(saved.startMinute)),
                    "selesai" to strOf(clockText(saved.endMinute)),
                    "ruangan" to strOf(saved.room),
                    "kelas" to strOf(saved.section),
                    "aktif" to boolOf(saved.enabled),
                ),
                "peringatan" to AgentJsonValue.Arr(warnings.map(::strOf)),
            ),
        ),
        summary = if (tool == "ubah_jadwal" && message.contains("ditambahkan")) {
            "menambah mata kuliah ${saved.name}"
        } else {
            "mengubah jadwal ${saved.name}"
        },
        itemCount = 1,
    )

    private fun taskNode(task: TaskEntity, courses: List<CourseEntity>, settings: SettingsEntity): AgentJsonValue =
        objOf(
            "judul" to strOf(task.title),
            "matkul" to strOf(courses.firstOrNull { it.id == task.courseId }?.name.orEmpty()),
            "tenggat" to strOf(task.dueEpochDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty()),
            "selesai" to boolOf(task.completed),
            "zona" to strOf(settings.zoneId),
        )

    private fun notPrepared(tool: String): AgentToolOutcome = AgentToolOutcome(
        tool = tool,
        json = AgentJson.write(
            objOf(
                "status" to strOf("error"),
                "pesan" to strOf("Tool tulis hanya berjalan setelah user menyetujui usulannya."),
            ),
        ),
        summary = "tidak dijalankan",
    )

    private fun today(settings: SettingsEntity): LocalDate =
        clock.instant().atZone(settings.zone).toLocalDate()

    private fun ask(question: String): AgentWritePreparation = AgentWritePreparation.Invalid(question)

    private fun firstString(value: AgentJsonValue?, vararg names: String): String {
        for (name in names) {
            value.stringOrNull(name)?.let { return it }
            value.intOrNull(name)?.let { return it.toString() }
        }
        return ""
    }

    private fun joinLabels(labels: List<String>): String = when (labels.size) {
        0 -> "data"
        1 -> labels.single()
        2 -> "${labels[0]} dan ${labels[1]}"
        else -> labels.dropLast(1).joinToString(", ") + ", dan " + labels.last()
    }

    private fun clip(text: String, max: Int): String {
        val single = text.replace('\n', ' ').trim()
        return if (single.length <= max) single else single.take(max).trimEnd() + "…"
    }
}

fun dateText(date: LocalDate): String = date.format(
    DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.forLanguageTag("id-ID")),
)

/**
 * Menerima "13:00", "13.00", "1300", "13", atau "13:00:00".
 * Angka tanpa pemisah dianggap HHMM bila 3–4 digit, selain itu jam.
 */
internal fun parseClock(raw: String?): Int? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    if (text.contains(':') || text.contains('.')) {
        val parts = text.replace('.', ':').split(':')
        val hour = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }
    val digits = text.filter { it.isDigit() }
    if (digits.isEmpty() || digits.length != text.length) return null
    return when (digits.length) {
        1, 2 -> digits.toInt().takeIf { it in 0..23 }?.times(60)
        3 -> {
            val hour = digits.substring(0, 1).toInt()
            val minute = digits.substring(1).toInt()
            if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
        }
        4 -> {
            val hour = digits.substring(0, 2).toInt()
            val minute = digits.substring(2).toInt()
            if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
        }
        else -> null
    }
}

/** Tanggal tenggat dari model: ISO, d/M/uuuu, d-M-uuuu, atau "hari ini"/"besok"/"lusa". */
internal fun parseDate(raw: String, today: LocalDate): LocalDate? {
    val text = raw.trim().lowercase(Locale.ROOT)
    when (text) {
        "hari ini", "today" -> return today
        "besok", "tomorrow" -> return today.plusDays(1)
        "lusa" -> return today.plusDays(2)
    }
    for (format in LIST_OF_DATE_FORMATS) {
        try {
            return LocalDate.parse(raw.trim(), format)
        } catch (_: DateTimeParseException) {
            // coba format berikutnya
        }
    }
    return null
}

private val LIST_OF_DATE_FORMATS: List<DateTimeFormatter> = listOf(
    DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT),
    DateTimeFormatter.ofPattern("d/M/uuuu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT),
    DateTimeFormatter.ofPattern("d-M-uuuu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT),
    DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.forLanguageTag("id-ID")).withResolverStyle(ResolverStyle.SMART),
)