package id.personal.campus.agent

import id.personal.campus.data.CampusSnapshot
import id.personal.campus.data.CourseEntity
import id.personal.campus.data.SettingsEntity
import id.personal.campus.data.TaskEntity
import id.personal.campus.schedule.Occurrence
import id.personal.campus.schedule.OccurrenceResolver
import id.personal.campus.schedule.ScheduleMode
import id.personal.campus.schedule.ScheduleSnapshot
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Sumber data untuk tool agen.
 *
 * Sengaja berupa fungsi `suspend` (bukan `CampusRepository` langsung) supaya berkas ini
 * **bebas `android.*`** dan tool bisa diuji di CI JVM dengan data contoh. Di aplikasi,
 * implementasinya cukup memanggil `CampusRepository.snapshot()`.
 */
fun interface AgentSnapshotSource {
    suspend fun snapshot(): CampusSnapshot
}

/** Hasil satu pemanggilan tool: teks JSON untuk model + ringkasan untuk jejak aksi di UI. */
data class AgentToolOutcome(
    val tool: String,
    val json: String,
    val summary: String,
    val itemCount: Int = 0,
)

/** Tool yang bisa dipanggil model. `writesData` dipakai loop sesi untuk memaksa konfirmasi. */
interface AgentTool {
    val name: String
    val description: String
    val parametersJson: String
    val writesData: Boolean get() = false

    /**
     * Untuk tool tulis: validasi argumen + susun usulan **tanpa menyimpan apa pun**.
     * `null` berarti tool read-only (atau tidak siap) dan hanya [execute] yang dipakai.
     */
    suspend fun prepare(arguments: AgentJsonValue?): AgentWritePreparation? = null

    suspend fun execute(arguments: AgentJsonValue?): AgentToolOutcome
}

class SimpleAgentTool(
    override val name: String,
    override val description: String,
    override val parametersJson: String,
    override val writesData: Boolean = false,
    private val prepareHandler: (suspend (AgentJsonValue?) -> AgentWritePreparation)? = null,
    private val handler: suspend (AgentJsonValue?) -> AgentToolOutcome,
) : AgentTool {
    override suspend fun prepare(arguments: AgentJsonValue?): AgentWritePreparation? =
        prepareHandler?.invoke(arguments)

    override suspend fun execute(arguments: AgentJsonValue?): AgentToolOutcome = handler(arguments)
}

/** Kumpulan tool + daftar `tools` yang dikirim ke model. */
class AgentToolRegistry(tools: List<AgentTool>) {
    private val byName: Map<String, AgentTool> = tools.associateBy { it.name }

    fun specs(allowed: Set<String> = emptySet()): List<AgentToolSpec> = byName.values
        .filter { allowed.isEmpty() || it.name in allowed }
        .map { AgentToolSpec(it.name, it.description, it.parametersJson) }

    fun find(name: String): AgentTool? = byName[name]

    fun names(): Set<String> = byName.keys
}

/**
 * Tool read-only untuk Asisten AI.
 *
 * **Tidak ada perhitungan jadwal baru di sini.** Semua tanggal/jam datang dari
 * [OccurrenceResolver] — sumber kebenaran yang sama dengan layar Jadwal, widget, dan pengingat —
 * sehingga jawaban agen tidak mungkin berbeda dari yang dilihat user.
 *
 * Bebas `android.*`: data masuk lewat [AgentSnapshotSource], waktu lewat [Clock].
 */
class AgentTools(
    private val source: AgentSnapshotSource,
    private val clock: Clock = Clock.systemUTC(),
) {

    val tools: List<AgentTool> = listOf(
            SimpleAgentTool(
                name = "kelas_hari",
                description = "Jadwal mata kuliah pada satu hari tertentu (Senin–Minggu), " +
                    "termasuk jam dan ruangan. Pakai bila user menyebut nama hari.",
                parametersJson = """{"type":"object","properties":{"hari":{"type":"string","description":"Nama hari (Senin, Selasa, Rabu, ...) atau angka 1–7 dengan Senin = 1."}},"required":["hari"]}""",
            ) { arguments -> kelasHari(arguments.stringOrNull("hari") ?: arguments.intOrNull("hari")?.toString()) },
            SimpleAgentTool(
                name = "kelas_berikutnya",
                description = "Kelas yang sedang berlangsung sekarang dan kelas berikutnya " +
                    "menurut jadwal serta semester yang tersimpan.",
                parametersJson = """{"type":"object","properties":{}}""",
            ) { kelasBerikutnya() },
            SimpleAgentTool(
                name = "jadwal_seminggu",
                description = "Ringkasan jadwal tujuh hari ke depan (hari ini sampai enam hari lagi).",
                parametersJson = """{"type":"object","properties":{}}""",
            ) { jadwalSeminggu() },
            SimpleAgentTool(
                name = "cari_ruangan",
                description = "Cari ruangan dan catatan ruangan sebuah mata kuliah.",
                parametersJson = """{"type":"object","properties":{"matkul":{"type":"string","description":"Nama mata kuliah atau sebagiannya."}},"required":["matkul"]}""",
            ) { arguments -> cariRuangan(arguments.stringOrNull("matkul").orEmpty()) },
            SimpleAgentTool(
                name = "daftar_tugas",
                description = "Daftar tugas yang belum selesai atau yang sudah selesai, " +
                    "lengkap dengan tenggat dan nama mata kuliahnya.",
                parametersJson = """{"type":"object","properties":{"sudah_selesai":{"type":"boolean","description":"true untuk tugas selesai, false untuk yang belum selesai."}},"required":["sudah_selesai"]}""",
            ) { arguments -> daftarTugas(arguments.boolOrNull("sudah_selesai") ?: false) },
            SimpleAgentTool(
                name = "cari_catatan",
                description = "Cari catatan kuliah berdasarkan kata kunci pada judul atau isi catatan.",
                parametersJson = """{"type":"object","properties":{"kata_kunci":{"type":"string","description":"Kata kunci pencarian."}},"required":["kata_kunci"]}""",
            ) { arguments -> cariCatatan(arguments.stringOrNull("kata_kunci").orEmpty()) },
    )

    val registry: AgentToolRegistry = AgentToolRegistry(tools)

    // -----------------------------------------------------------------------
    // Tool
    // -----------------------------------------------------------------------

    suspend fun kelasHari(hari: String?): AgentToolOutcome {
        val data = source.snapshot()
        val settings = data.settings
        val today = today(settings)
        val weekday = parseWeekday(hari)
            ?: return invalid(
                "kelas_hari",
                "Hari tidak dikenali: \"${hari.orEmpty()}\". Sebutkan nama hari (Senin–Minggu) " +
                    "atau angka 1–7 (Senin = 1). Tanyakan ke user bila belum jelas.",
            )
        if (data.courses.none { it.enabled }) {
            return outcome(
                "kelas_hari",
                status = "kosong",
                pesan = "Belum ada mata kuliah aktif yang tersimpan.",
                items = emptyList(),
                summary = "0 mata kuliah",
            )
        }

        val date = today.with(TemporalAdjusters.nextOrSame(weekday))
        val snapshot = snapshotAt(date, data.courses, settings)
        if (snapshot.mode == ScheduleMode.FINISHED) {
            return outcome(
                "kelas_hari",
                status = "selesai",
                pesan = "Semester sudah berakhir pada ${settings.endDate}; tidak ada jadwal setelah itu.",
                items = emptyList(),
                summary = "0 mata kuliah",
            )
        }

        val occurrences = occurrencesOn(snapshot, date)
        val payload = buildList {
            add("status" to strOf("ok"))
            add("hari" to strOf(dayName(weekday.value)))
            add("tanggal" to strOf(date.toString()))
            add("jumlah" to numOf(occurrences.size))
            add("kelas" to AgentJsonValue.Arr(occurrences.map(::courseNode)))
            semesterNote(snapshot, settings)?.let { add("catatan_semester" to strOf(it)) }
        }
        return AgentToolOutcome(
            tool = "kelas_hari",
            json = AgentJson.write(AgentJsonValue.Obj(linkedMapOf(*payload.toTypedArray()))),
            summary = "${occurrences.size} mata kuliah ${dayName(weekday.value)}",
            itemCount = occurrences.size,
        )
    }

    suspend fun kelasBerikutnya(): AgentToolOutcome {
        val data = source.snapshot()
        val settings = data.settings
        val snapshot = OccurrenceResolver(clock).resolve(data.courses, settings)
        if (snapshot.mode == ScheduleMode.EMPTY) {
            return outcome(
                "kelas_berikutnya",
                status = "kosong",
                pesan = "Belum ada mata kuliah aktif yang tersimpan.",
                items = emptyList(),
                summary = "jadwal kosong",
            )
        }
        if (snapshot.mode == ScheduleMode.FINISHED) {
            return outcome(
                "kelas_berikutnya",
                status = "selesai",
                pesan = "Semester sudah berakhir; tidak ada kelas berikutnya.",
                items = emptyList(),
                summary = "semester selesai",
            )
        }

        val next = snapshot.upcoming.firstOrNull()
        val fields = linkedMapOf<String, AgentJsonValue>(
            "status" to strOf("ok"),
            "sekarang" to strOf(humanMoment(snapshot.now)),
            "mode" to strOf(snapshot.mode.label()),
            "sedang_berlangsung" to AgentJsonValue.Arr(snapshot.active.map(::occurrenceNode)),
            "berikutnya" to (next?.let(::occurrenceNode) ?: AgentJsonValue.Null),
        )
        if (next != null) {
            fields["menit_ke_berikutnya"] = numOf(minutesBetween(snapshot.now, next.start))
        }
        semesterNote(snapshot, settings)?.let { fields["catatan_semester"] = strOf(it) }

        val summary = when {
            snapshot.active.isNotEmpty() -> "kelas berlangsung"
            next != null -> "kelas berikutnya"
            else -> "jadwal kosong"
        }
        return AgentToolOutcome(
            tool = "kelas_berikutnya",
            json = AgentJson.write(AgentJsonValue.Obj(fields)),
            summary = summary,
            itemCount = snapshot.active.size + if (next == null) 0 else 1,
        )
    }

    suspend fun jadwalSeminggu(): AgentToolOutcome {
        val data = source.snapshot()
        val settings = data.settings
        val today = today(settings)
        if (data.courses.none { it.enabled }) {
            return outcome(
                "jadwal_seminggu",
                status = "kosong",
                pesan = "Belum ada mata kuliah aktif yang tersimpan.",
                items = emptyList(),
                summary = "0 mata kuliah",
            )
        }

        var total = 0
        val days = (0L..6L).map { offset ->
            val date = today.plusDays(offset)
            val snapshot = snapshotAt(date, data.courses, settings)
            val occurrences = if (snapshot.mode == ScheduleMode.FINISHED) emptyList() else occurrencesOn(snapshot, date)
            total += occurrences.size
            objOf(
                "tanggal" to strOf(date.toString()),
                "hari" to strOf(dayName(date.dayOfWeek.value)),
                "jumlah" to numOf(occurrences.size),
                "kelas" to AgentJsonValue.Arr(occurrences.map(::courseNode)),
            )
        }
        val finished = OccurrenceResolver(clock).resolve(data.courses, settings).mode == ScheduleMode.FINISHED
        val fields = linkedMapOf<String, AgentJsonValue>(
            "status" to strOf(if (finished) "selesai" else "ok"),
            "mulai_dari" to strOf(today.toString()),
            "total_kelas" to numOf(total),
            "hari" to AgentJsonValue.Arr(days),
        )
        if (finished) {
            fields["pesan"] = strOf("Semester sudah berakhir; jadwal mingguan kosong.")
        } else {
            semesterNote(null, settings)?.let { fields["catatan_semester"] = strOf(it) }
        }
        return AgentToolOutcome(
            tool = "jadwal_seminggu",
            json = AgentJson.write(AgentJsonValue.Obj(fields)),
            summary = "$total mata kuliah",
            itemCount = total,
        )
    }

    suspend fun cariRuangan(matkul: String): AgentToolOutcome {
        val data = source.snapshot()
        val query = normalize(matkul)
        val active = data.courses.filter { it.enabled }

        if (query.isEmpty()) {
            val all = active.sortedBy { it.name.lowercase() }
            return AgentToolOutcome(
                tool = "cari_ruangan",
                json = AgentJson.write(
                    objOf(
                        "status" to strOf("ok"),
                        "pesan" to strOf("Nama mata kuliah tidak disebutkan; ini seluruh ruangan yang tersimpan."),
                        "jumlah" to numOf(all.size),
                        "hasil" to AgentJsonValue.Arr(all.map(::roomNode)),
                    ),
                ),
                summary = "${all.size} ruangan",
                itemCount = all.size,
            )
        }

        val matches = active.filter { normalize(it.name).contains(query) || query.contains(normalize(it.name)) }
        val limited = matches.take(MAX_MATCHES)
        val fields = linkedMapOf<String, AgentJsonValue>(
            "status" to strOf(if (matches.isEmpty()) "tidak_ditemukan" else "ok"),
            "kueri" to strOf(matkul.trim()),
            "jumlah" to numOf(matches.size),
            "hasil" to AgentJsonValue.Arr(limited.map(::roomNode)),
        )
        if (matches.size > limited.size) {
            fields["terpotong"] = boolOf(true)
            fields["pesan"] = strOf("Hanya ${limited.size} hasil pertama yang ditampilkan.")
        }
        if (matches.isEmpty()) {
            fields["pesan"] = strOf("Tidak ada mata kuliah yang cocok. Tanyakan nama mata kuliah yang benar ke user.")
        }
        return AgentToolOutcome(
            tool = "cari_ruangan",
            json = AgentJson.write(AgentJsonValue.Obj(fields)),
            summary = "${matches.size} ruangan",
            itemCount = matches.size,
        )
    }

    suspend fun daftarTugas(sudahSelesai: Boolean): AgentToolOutcome {
        val data = source.snapshot()
        val settings = data.settings
        val today = today(settings)
        val courseNames = data.courses.associate { it.id to it.name }

        val selected = data.tasks
            .filter { it.completed == sudahSelesai }
            .sortedWith(compareBy<TaskEntity> { it.dueEpochDay ?: Long.MAX_VALUE }.thenBy { it.title.lowercase() })
        val limited = selected.take(MAX_TASKS)
        val fields = linkedMapOf<String, AgentJsonValue>(
            "status" to strOf("ok"),
            "filter" to strOf(if (sudahSelesai) "selesai" else "belum_selesai"),
            "jumlah" to numOf(selected.size),
            "tugas" to AgentJsonValue.Arr(
                limited.map { task ->
                    objOf(
                        "judul" to strOf(task.title),
                        "matkul" to strOf(task.courseId?.let { courseNames[it] } ?: ""),
                        "tenggat" to strOf(task.dueEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: ""),
                        "terlambat" to boolOf(
                            !task.completed && task.dueEpochDay != null && LocalDate.ofEpochDay(task.dueEpochDay) < today,
                        ),
                        "catatan" to strOf(task.notes.take(MAX_NOTE_CHARS)),
                    )
                },
            ),
        )
        if (selected.size > limited.size) {
            fields["terpotong"] = boolOf(true)
            fields["pesan"] = strOf("Hanya ${limited.size} tugas pertama yang ditampilkan.")
        }
        return AgentToolOutcome(
            tool = "daftar_tugas",
            json = AgentJson.write(AgentJsonValue.Obj(fields)),
            summary = "${selected.size} tugas",
            itemCount = selected.size,
        )
    }

    suspend fun cariCatatan(kataKunci: String): AgentToolOutcome {
        val data = source.snapshot()
        val query = kataKunci.trim().lowercase(Locale.ROOT)
        val matches = if (query.isEmpty()) {
            data.notes
        } else {
            data.notes.filter {
                it.title.lowercase(Locale.ROOT).contains(query) || it.body.lowercase(Locale.ROOT).contains(query)
            }
        }.sortedBy { it.title.lowercase(Locale.ROOT) }
        val limited = matches.take(MAX_NOTES)

        val fields = linkedMapOf<String, AgentJsonValue>(
            "status" to strOf(if (matches.isEmpty()) "tidak_ditemukan" else "ok"),
            "kata_kunci" to strOf(kataKunci.trim()),
            "jumlah" to numOf(matches.size),
            "catatan" to AgentJsonValue.Arr(
                limited.map { note ->
                    objOf(
                        "judul" to strOf(note.title),
                        "cuplikan" to strOf(snippet(note.body, query)),
                    )
                },
            ),
        )
        if (matches.isEmpty()) {
            fields["pesan"] = strOf("Tidak ada catatan yang cocok dengan kata kunci itu.")
        }
        if (matches.size > limited.size) {
            fields["terpotong"] = boolOf(true)
        }
        return AgentToolOutcome(
            tool = "cari_catatan",
            json = AgentJson.write(AgentJsonValue.Obj(fields)),
            summary = "${matches.size} catatan",
            itemCount = matches.size,
        )
    }

    // -----------------------------------------------------------------------
    // Bantuan
    // -----------------------------------------------------------------------

    private fun today(settings: SettingsEntity): LocalDate =
        clock.instant().atZone(settings.zone).toLocalDate()

    /**
     * Snapshot diambil pada awal hari yang diminta, **memakai [OccurrenceResolver] yang sama**.
     * Dengan begitu kelas yang sudah lewat hari itu tetap terlihat (user bertanya "jadwal Rabu",
     * bukan "jadwal mulai sekarang"), tanpa menulis ulang aturan semester/bentrok.
     */
    private fun snapshotAt(
        date: LocalDate,
        courses: List<CourseEntity>,
        settings: SettingsEntity,
    ): ScheduleSnapshot {
        val zone: ZoneId = settings.zone
        val start = date.atStartOfDay(zone)
        return OccurrenceResolver(Clock.fixed(start.toInstant(), zone)).resolve(courses, settings)
    }

    private fun occurrencesOn(snapshot: ScheduleSnapshot, date: LocalDate): List<Occurrence> =
        (snapshot.active + snapshot.upcoming)
            .filter { it.start.toLocalDate().toEpochDay() == date.toEpochDay() }
            .distinctBy { it.course.id }
            .sortedWith(compareBy<Occurrence> { it.start.toInstant() }.thenBy { it.course.id })

    private fun occurrenceNode(occurrence: Occurrence): AgentJsonValue {
        val date = occurrence.start.toLocalDate()
        return objOf(
            "matkul" to strOf(occurrence.course.name),
            "hari" to strOf(dayName(date.dayOfWeek.value)),
            "tanggal" to strOf(date.toString()),
            "mulai" to strOf(clockText(occurrence.course.startMinute)),
            "selesai" to strOf(clockText(occurrence.course.endMinute)),
            "ruangan" to strOf(occurrence.course.room),
            "catatan_ruangan" to strOf(occurrence.course.roomNote),
            "kelas" to strOf(occurrence.course.section),
        )
    }

    private fun courseNode(occurrence: Occurrence): AgentJsonValue = occurrenceNode(occurrence)

    private fun roomNode(course: CourseEntity): AgentJsonValue = objOf(
        "matkul" to strOf(course.name),
        "ruangan" to strOf(course.room),
        "catatan_ruangan" to strOf(course.roomNote),
        "hari" to strOf(dayName(course.weekday)),
        "mulai" to strOf(clockText(course.startMinute)),
        "selesai" to strOf(clockText(course.endMinute)),
        "kelas" to strOf(course.section),
    )

    private fun outcome(
        tool: String,
        status: String,
        pesan: String,
        items: List<AgentJsonValue>,
        summary: String,
    ): AgentToolOutcome = AgentToolOutcome(
        tool = tool,
        json = AgentJson.write(
            objOf(
                "status" to strOf(status),
                "jumlah" to numOf(items.size),
                "hasil" to AgentJsonValue.Arr(items),
                "pesan" to strOf(pesan),
            ),
        ),
        summary = summary,
        itemCount = items.size,
    )

    private fun invalid(tool: String, pesan: String): AgentToolOutcome = AgentToolOutcome(
        tool = tool,
        json = AgentJson.write(objOf("status" to strOf("argumen_tidak_valid"), "pesan" to strOf(pesan))),
        summary = "argumen tidak valid",
        itemCount = 0,
    )

    private fun semesterNote(snapshot: ScheduleSnapshot?, settings: SettingsEntity): String? = when {
        snapshot?.mode == ScheduleMode.FINISHED -> "Semester sudah berakhir pada ${settings.endDate}."
        !settings.semesterConfirmed ->
            "Tanggal semester belum dikonfirmasi user, jadi tanggal di atas hanya perkiraan " +
                "berdasarkan jadwal mingguan."
        else -> null
    }

    private fun minutesBetween(now: ZonedDateTime, target: ZonedDateTime): Int =
        java.time.Duration.between(now, target).toMinutes().toInt()
}

// ---------------------------------------------------------------------------
// Utilitas teks/tanggal (bebas android.*)
// ---------------------------------------------------------------------------

private const val MAX_MATCHES = 5
private const val MAX_TASKS = 50
private const val MAX_NOTES = 5
private const val MAX_NOTE_CHARS = 200
private const val SNIPPET_RADIUS = 120

private val AGENT_LOCALE: Locale = Locale.forLanguageTag("id-ID")
private val HUMAN_MOMENT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy HH:mm", AGENT_LOCALE)

private val DAY_NAMES = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")

private val DAY_NAMES_EN = listOf(
    "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
)

fun dayName(weekday: Int): String =
    DAY_NAMES.getOrElse(weekday - 1) { "Hari $weekday" }

fun clockText(minutes: Int): String =
    "%02d:%02d".format(Locale.ROOT, (minutes / 60) % 24, minutes % 60)

fun humanMoment(moment: ZonedDateTime): String = moment.format(HUMAN_MOMENT)

private fun ScheduleMode.label(): String = when (this) {
    ScheduleMode.PREVIEW -> "PRATINJAU"
    ScheduleMode.SCHEDULED -> "TERJADWAL"
    ScheduleMode.FINISHED -> "SELESAI"
    ScheduleMode.EMPTY -> "KOSONG"
}

/** Menerima "Rabu", "rabu", "3", "Rabu, 11 November", atau nama Inggris dari model. */
internal fun parseWeekday(raw: String?): DayOfWeek? {
    val text = raw?.trim()?.lowercase(Locale.ROOT).orEmpty()
    if (text.isEmpty()) return null
    text.toIntOrNull()?.let { number ->
        return if (number in 1..7) DayOfWeek.of(number) else null
    }
    DAY_NAMES.indexOfFirst { text == it.lowercase(Locale.ROOT) || text.startsWith(it.lowercase(Locale.ROOT)) }
        .takeIf { it >= 0 }?.let { return DayOfWeek.of(it + 1) }
    DAY_NAMES_EN.indexOfFirst { text.startsWith(it) }.takeIf { it >= 0 }
        ?.let { return DayOfWeek.of(it + 1) }
    return null
}

internal fun normalize(text: String): String =
    text.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

private fun snippet(body: String, query: String): String {
    if (body.length <= SNIPPET_RADIUS * 2) return body.trim()
    val at = if (query.isEmpty()) 0 else body.lowercase(Locale.ROOT).indexOf(query).coerceAtLeast(0)
    val from = (at - SNIPPET_RADIUS).coerceAtLeast(0)
    val to = (from + SNIPPET_RADIUS * 2).coerceAtMost(body.length)
    val prefix = if (from > 0) "…" else ""
    val suffix = if (to < body.length) "…" else ""
    return (prefix + body.substring(from, to).trim() + suffix)
}