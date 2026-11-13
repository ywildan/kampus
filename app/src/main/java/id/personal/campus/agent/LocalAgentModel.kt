package id.personal.campus.agent

import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * "Model" offline untuk Asisten AI: **bukan LLM**, melainkan pencocok niat berbasis aturan
 * untuk domain tertutup jadwal + tugas + ruangan + catatan.
 *
 * Dipakai saat user memilih mode Offline di pemilih model: tidak ada jaringan, tidak ada
 * kunci API, dan tidak ada unduhan model. Satu-satunya yang "dipahami" adalah pola
 * pertanyaan yang memetakan langsung ke tool yang sudah ada (`AgentTools` baca dan
 * `AgentWriteTools` tulis); sisanya dijawab jujur bahwa mode ini tidak bisa.
 *
 * Cara kerja mengikuti protokol sesi yang sama dengan model online:
 * - Putaran pertama: pesan user terakhir diurai menjadi **satu** pemanggilan tool
 *   ([AgentEvent.ToolCalls]). Tool tulis tetap melewati [AgentSession.executeWrite]:
 *   validasi di `prepare`, kartu persetujuan, dan auto-approval **tidak berlaku** di sini
 *   (ViewModel memaksa konfirmasi saat mode Offline).
 * - Putaran kedua: hasil tool (pesan `Tool` terakhir) diformat menjadi jawaban teks
 *   Bahasa Indonesia dari JSON-nya — tidak ada karangan, semua angka/nama/jam berasal
 *   dari hasil tool yang sama dengan yang dipakai layar lain.
 *
 * Keamanan yang dijaga:
 * - Tidak pernah menulis data sendiri: semua perubahan lewat tool tulis + persetujuan user.
 * - Tidak pernah menebak nilai: argumen yang kurang dibiarkan kosong supaya `prepare`
 *   menanyakannya (aturan yang sama dengan model online).
 * - Di luar cakupan (mis. "jelaskan materi X") dijawab tidak bisa, bukan dikarang.
 *
 * Bebas `android.*` supaya bisa diuji di CI JVM dengan jam tetap.
 */
class LocalAgentModel(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val clock: Clock = Clock.systemDefaultZone(),
) : AgentModel {

    companion object {
        /** Nama model yang dipakai mode Offline di permintaan sesi. */
        const val MODEL_NAME: String = "offline"
    }

    override fun stream(request: AgentRequest): Flow<AgentEvent> = flow {
        val messages = request.messages
        val today = clock.instant().atZone(zoneId).toLocalDate()
        val allowed = request.tools.map { it.name }.toSet()

        // Putaran jawaban: pesan terakhir adalah hasil tool (bukan pesan lama dari
        // riwayat — putaran baru selalu diawali pesan user).
        val last = messages.lastOrNull()
        if (last?.role == AgentRole.Tool) {
            val toolName = messages
                .lastOrNull { it.role == AgentRole.Assistant && it.toolCalls.isNotEmpty() }
                ?.toolCalls?.firstOrNull()?.name.orEmpty()
            val userText = messages.lastOrNull { it.role == AgentRole.User }?.content.orEmpty()
            emit(AgentEvent.Token(formatToolResult(toolName, last.content, userText, today)))
            emit(AgentEvent.Done())
            return@flow
        }

        val userText = messages.lastOrNull { it.role == AgentRole.User }?.content.orEmpty()
        when (val intent = parseLocalIntent(userText, today)) {
            is LocalIntent.Call -> {
                if (allowed.isNotEmpty() && intent.tool !in allowed) {
                    emit(AgentEvent.Token(OFFLINE_SCOPE_TEXT))
                } else {
                    emit(
                        AgentEvent.ToolCalls(
                            listOf(
                                AgentToolCall(
                                    id = UUID.randomUUID().toString(),
                                    name = intent.tool,
                                    argumentsJson = AgentJson.write(intent.args),
                                ),
                            ),
                        ),
                    )
                }
                emit(AgentEvent.Done())
            }
            LocalIntent.Greeting -> {
                emit(AgentEvent.Token(GREETING_TEXT))
                emit(AgentEvent.Done())
            }
            LocalIntent.Unknown -> {
                emit(AgentEvent.Token(OFFLINE_SCOPE_TEXT))
                emit(AgentEvent.Done())
            }
        }
    }
}

/** Hasil uraian pesan user: satu pemanggilan tool, sapaan, atau tidak dikenali. */
internal sealed interface LocalIntent {
    data class Call(val tool: String, val args: AgentJsonValue) : LocalIntent
    data object Greeting : LocalIntent
    data object Unknown : LocalIntent
}

internal const val OFFLINE_SCOPE_TEXT: String =
    "Mode Offline hanya bisa membantu soal jadwal, tugas, ruangan, dan catatan yang " +
        "tersimpan di HP ini — misalnya \"jadwal besok\", \"tugas apa saja\", atau " +
        "\"tambah tugas Latihan 3\". Untuk pertanyaan lain, pilih mode Hemat, Pintar, " +
        "atau Cepat saat ada internet."

private const val GREETING_TEXT: String =
    "Halo! Mode Offline siap membantu soal jadwal dan tugas. Coba \"jadwal besok\" " +
        "atau \"ada tugas apa saja\"."

private val QUERY_ALIASES: Map<String, String> = mapOf(
    "jdwl" to "jadwal",
    "jwdl" to "jadwal",
    "tgs" to "tugas",
    "kls" to "kelas",
    "bsk" to "besok",
    "skrg" to "sekarang",
)

private val DAY_NAMES_ID: List<String> =
    listOf("senin", "selasa", "rabu", "kamis", "jumat", "sabtu", "minggu")

private val TIME_RANGE: Regex =
    Regex("(\\d{1,2}[:.]\\d{2})\\s*(?:-|s/d|sampai|sd)\\s*(\\d{1,2}[:.]\\d{2})")
private val TWO_TIMES: Regex = Regex("(\\d{1,2}[:.]\\d{2})\\s+(\\d{1,2}[:.]\\d{2})")
private val SINGLE_TIME: Regex = Regex("jam\\s+(\\d{1,2}[:.]\\d{2})")
private val ROOM_CODE: Regex = Regex("\\b([A-Za-z]\\.[A-Za-z0-9][A-Za-z0-9.]*)\\b")

/**
 * Normalisasi query: huruf kecil, singkatan umum diluruskan, tanda baca dibuang kecuali
 * yang dipakai jam (`:`/`.`), rentang (`-`), dan tanggal (`/`).
 */
internal fun normalizeQuery(raw: String): String {
    var text = raw.lowercase(Locale.ROOT).trim()
    for ((alias, replacement) in QUERY_ALIASES) {
        text = text.replace(Regex("\\b$alias\\b"), replacement)
    }
    val kept = StringBuilder()
    for (char in text) {
        kept.append(
            if (char.isLetterOrDigit() || char == ' ' || char == '.' || char == ':' ||
                char == '-' || char == '/'
            ) {
                char
            } else {
                ' '
            },
        )
    }
    return kept.toString().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
}

internal fun parseLocalIntent(rawText: String, today: LocalDate): LocalIntent {
    // `text` (normalisasi) hanya untuk MENGENALI niat; nilai yang diekstrak
    // (judul, nama mata kuliah) diambil dari `raw` supaya huruf besar-kecil
    // yang diketik user tetap terjaga di kartu persetujuan.
    // Awalan sopan "tolong" dibuang dulu supaya "tolong tambah tugas ..." tetap dikenali.
    val raw = rawText.trim().replace(Regex("(?i)^tolong\\s+"), "")
    val text = normalizeQuery(raw)
    if (text.isBlank()) return LocalIntent.Unknown
    if (isGreeting(text)) return LocalIntent.Greeting
    parseWriteIntent(text, raw, today)?.let { return it }
    parseReadIntent(text, raw, today)?.let { return it }
    return LocalIntent.Unknown
}

private fun isGreeting(text: String): Boolean = text in setOf(
    "halo", "hai", "hi", "pagi", "siang", "sore", "malam",
    "makasih", "terima kasih", "terima kasih banyak", "selamat pagi",
    "selamat siang", "selamat sore", "selamat malam",
) || text.startsWith("halo ") || text.startsWith("hai ")

/** Kata hari yang disebut user ("hari ini"/"besok"/"lusa"/nama hari) dalam zona user. */
internal fun findDay(text: String, today: LocalDate): DayOfWeek? {
    if ("hari ini" in text) return today.dayOfWeek
    if ("besok" in text) return today.plusDays(1).dayOfWeek
    if ("lusa" in text) return today.plusDays(2).dayOfWeek
    for ((index, name) in DAY_NAMES_ID.withIndex()) {
        if (Regex("\\b$name\\b").containsMatchIn(text)) return DayOfWeek.of(index + 1)
    }
    return null
}

private fun removeDayWords(text: String): String {
    var out = text
    for (word in listOf("hari ini", "besok", "lusa") + DAY_NAMES_ID) {
        out = out.replace(Regex("(?i)\\b$word\\b"), " ")
    }
    return out
}

/** Sisa teks setelah salah satu awalan (cocok tanpa memperhatikan huruf besar-kecil). */
private fun afterPrefix(raw: String, vararg prefixes: String): String? {
    for (prefix in prefixes) {
        val match = Regex("(?i)^\\s*" + Regex.escape(prefix) + "\\b").find(raw)
        if (match != null) return raw.substring(match.range.last + 1)
    }
    return null
}

private fun parseWriteIntent(text: String, raw: String, today: LocalDate): LocalIntent.Call? {
    afterPrefix(raw, "tambah tugas")?.let { rest ->
        if (text.startsWith("tambah tugas")) {
            return LocalIntent.Call("tambah_tugas", addTaskArgs(rest, today))
        }
    }
    afterPrefix(raw, "tambah jadwal", "tambah mata kuliah", "tambah kuliah", "tambah kelas")?.let { rest ->
        if (text.startsWith("tambah jadwal") || text.startsWith("tambah mata kuliah") ||
            text.startsWith("tambah kuliah") || text.startsWith("tambah kelas")
        ) {
            return LocalIntent.Call("ubah_jadwal", changeScheduleArgs(rest, today))
        }
    }
    afterPrefix(raw, "ubah jadwal", "ubah jam", "pindah jadwal", "pindah jam", "ganti jadwal")?.let { rest ->
        if (text.startsWith("ubah jadwal") || text.startsWith("ubah jam") ||
            text.startsWith("pindah jadwal") || text.startsWith("pindah jam") ||
            text.startsWith("ganti jadwal")
        ) {
            return LocalIntent.Call("ubah_jadwal", changeScheduleArgs(rest, today))
        }
    }
    afterPrefix(raw, "tambah catatan", "buat catatan")?.let { rest ->
        if (text.startsWith("tambah catatan") || text.startsWith("buat catatan")) {
            return LocalIntent.Call("tambah_catatan", addNoteArgs(rest))
        }
    }
    // "tandai tugas X selesai" / "tugas X sudah selesai" → tandai selesai.
    // "tugas yang sudah selesai" adalah daftar (baca), bukan tulis — dikecualikan di sini.
    if ("yang sudah selesai" !in text && "yang selesai" !in text) {
        if (text.startsWith("tandai") && "selesai" in text) {
            return LocalIntent.Call(
                "tandai_tugas_selesai",
                objOf("judul_tugas" to strOf(markDoneQuery(raw))),
            )
        }
        if (text.startsWith("selesaikan")) {
            return LocalIntent.Call(
                "tandai_tugas_selesai",
                objOf("judul_tugas" to strOf(markDoneQuery(raw))),
            )
        }
        if ("sudah selesai" in text && text.startsWith("tugas ")) {
            val query = markDoneQuery(raw)
            if (query.isNotBlank()) {
                return LocalIntent.Call(
                    "tandai_tugas_selesai",
                    objOf("judul_tugas" to strOf(query)),
                )
            }
        }
    }
    return null
}

/** Judul tugas dari teks asli: buang kata perintah, sisanya dipertahankan apa adanya. */
private fun markDoneQuery(raw: String): String {
    var out = raw
    for (word in listOf("tandai", "tugas", "sudah selesai", "selesai", "selesaikan")) {
        out = out.replace(Regex("(?i)\\b$word\\b"), " ")
    }
    return out.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
}

private fun stripWords(text: String, vararg words: String): String {
    var out = text
    for (word in words) out = out.replace(Regex("(?i)\\b$word\\b"), " ")
    return out.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
}

/** "tambah tugas Latihan 3 matkul PTE tenggat besok" → judul + mata kuliah + tenggat. */
private fun addTaskArgs(rest: String, today: LocalDate): AgentJsonValue {
    val markers = listOf("mata kuliah", "matkul", "tenggat", "deadline", "tanggal", "catatan", "keterangan")
    val found = markers.mapNotNull { marker ->
        Regex("(?i)\\b$marker\\b").find(rest)?.let { marker to it.range.first }
    }.sortedBy { it.second }
    val title = if (found.isEmpty()) rest.trim() else rest.substring(0, found.first().second).trim()
    var course = ""
    var due = ""
    var notes = ""
    for ((index, markerAndAt) in found.withIndex()) {
        val (marker, at) = markerAndAt
        val valueStart = at + marker.length
        val valueEnd = found.getOrNull(index + 1)?.second ?: rest.length
        val value = rest.substring(valueStart, valueEnd).trim()
        when (marker) {
            "mata kuliah", "matkul" -> course = value
            "tenggat", "deadline", "tanggal" -> due = parseDueWord(value, today)
            "catatan", "keterangan" -> notes = value
        }
    }
    return objOf(
        "judul" to strOf(title),
        "mata_kuliah" to strOf(course),
        "tenggat" to strOf(due),
        "catatan" to strOf(notes),
    )
}

/** Tenggat dari segmen mentah: utuh dulu, lalu kata pertama ("besok", "12-11-2026"). */
private fun parseDueWord(value: String, today: LocalDate): String {
    if (value.isBlank()) return ""
    parseDate(value, today)?.let { return it.toString() }
    val first = value.split(Regex("\\s+")).firstOrNull().orEmpty()
    parseDate(first, today)?.let { return it.toString() }
    return value
}

/** "tambah jadwal X rabu 15.10-17.40 A.3b.4" → nama + hari + jam + ruangan. */
private fun changeScheduleArgs(rest: String, today: LocalDate): AgentJsonValue {
    var remaining = rest.trim()
    var section = ""
    Regex("(?i)(?:kelas|seksi)\\s+(\\d{1,2}[A-Za-z]?|[A-Za-z])\\b").find(remaining)?.let { match ->
        section = match.groupValues[1]
        remaining = remaining.replace(match.value, " ")
    }
    var start = ""
    var end = ""
    TIME_RANGE.find(remaining)?.let { match ->
        start = normalizeClock(match.groupValues[1])
        end = normalizeClock(match.groupValues[2])
        remaining = remaining.replace(match.value, " ")
    } ?: TWO_TIMES.find(remaining)?.let { match ->
        start = normalizeClock(match.groupValues[1])
        end = normalizeClock(match.groupValues[2])
        remaining = remaining.replace(match.value, " ")
    } ?: SINGLE_TIME.find(remaining)?.let { match ->
        start = normalizeClock(match.groupValues[1])
        remaining = remaining.replace(match.value, " ")
    }
    var room = ""
    ROOM_CODE.find(remaining)?.let { match ->
        room = match.value.replaceFirstChar { it.uppercaseChar() }
        remaining = remaining.replace(match.value, " ")
    } ?: Regex("(?i)ruang(?:an)?\\s+(\\S+)").find(remaining)?.let { match ->
        room = match.groupValues[1]
        remaining = remaining.replace(match.value, " ")
    }
    // Kata "ruangan" sendiri bukan bagian nama mata kuliah.
    remaining = stripWords(remaining, "ruangan", "ruang")
    var day = ""
    findDay(normalizeQuery(remaining), today)?.let { weekday ->
        day = dayName(weekday.value)
        remaining = removeDayWords(remaining)
    }
    val name = stripWords(remaining, "mata kuliah", "matkul")
    return objOf(
        "mata_kuliah" to strOf(name),
        "hari" to strOf(day),
        "mulai" to strOf(start),
        "selesai" to strOf(end),
        "ruangan" to strOf(room),
        "kelas" to strOf(section),
    )
}

/** "tambah catatan Judul isi ..." → judul + isi (isi opsional). */
private fun addNoteArgs(rest: String): AgentJsonValue {
    val marker = Regex("(?i)\\bisi\\b").find(rest)
    val title = if (marker == null) rest.trim() else rest.substring(0, marker.range.first).trim()
    val body = if (marker == null) "" else rest.substring(marker.range.last + 1).trim()
    return objOf("judul" to strOf(title), "isi" to strOf(body))
}

private fun parseReadIntent(text: String, raw: String, today: LocalDate): LocalIntent.Call? {
    if ("tugas" in text) {
        val done = "belum" !in text &&
            ("yang sudah selesai" in text || "yang selesai" in text || "sudah selesai" in text)
        return LocalIntent.Call(
            "daftar_tugas",
            objOf("sudah_selesai" to boolOf(done)),
        )
    }
    if ("jadwal seminggu" in text || "jadwal minggu ini" in text || "jadwal 7 hari" in text ||
        ("seminggu" in text && "jadwal" in text)
    ) {
        return LocalIntent.Call("jadwal_seminggu", objOf())
    }
    val day = findDay(text, today)
    if (day != null && ("jadwal" in text || "kuliah" in text || "kelas" in text || "matkul" in text)) {
        return LocalIntent.Call(
            "kelas_hari",
            objOf("hari" to strOf(dayName(day.value))),
        )
    }
    if ("kelas berikutnya" in text || "kelas selanjutnya" in text || "kelas sekarang" in text ||
        "kelas apa sekarang" in text || "habis ini" in text || "kelas setelah ini" in text ||
        ("sekarang" in text && "kelas" in text)
    ) {
        return LocalIntent.Call("kelas_berikutnya", objOf())
    }
    if ("ruangan" in text || "ruang" in text || "di mana" in text || "dimana" in text ||
        "kapan" in text || "jam berapa" in text
    ) {
        val query = stripWords(raw, "ruangan", "ruang", "di mana", "dimana", "kapan", "jam berapa", "jadwal", "kelas", "kuliah", "matkul", "mata kuliah")
        return LocalIntent.Call("cari_ruangan", objOf("matkul" to strOf(query)))
    }
    afterPrefix(raw, "jadwal")?.let { query ->
        if (query.isBlank()) {
            return LocalIntent.Call("kelas_hari", objOf("hari" to strOf(dayName(today.dayOfWeek.value))))
        }
        return LocalIntent.Call("cari_ruangan", objOf("matkul" to strOf(query.trim())))
    }
    if ("catatan" in text) {
        val query = stripWords(raw, "cari catatan", "catatan", "cari", "tentang")
        return LocalIntent.Call("cari_catatan", objOf("kata_kunci" to strOf(query)))
    }
    if (text == "jadwal") {
        return LocalIntent.Call("kelas_hari", objOf("hari" to strOf(dayName(today.dayOfWeek.value))))
    }
    return null
}

private fun normalizeClock(raw: String): String {
    val parts = raw.replace('.', ':').split(':')
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: return raw
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return "%02d:%02d".format(Locale.ROOT, hour, minute)
}

// ---------------------------------------------------------------------------
// Format jawaban dari JSON hasil tool (putaran kedua).
// ---------------------------------------------------------------------------

internal fun formatToolResult(tool: String, jsonText: String, userText: String, today: LocalDate): String {
    val root = AgentJson.parse(jsonText)
    val status = root.string("status")
    val message = root.string("pesan")
    if (status == "perlu_pertanyaan" && message.isNotBlank()) return message
    if (status == "error") {
        return message.ifBlank { "Maaf, permintaan itu tidak bisa diproses." }
    }
    return when (tool) {
        "kelas_hari" -> formatKelasHari(root)
        "kelas_berikutnya" -> formatKelasBerikutnya(root)
        "jadwal_seminggu" -> formatJadwalSeminggu(root)
        "cari_ruangan" -> formatCariRuangan(root)
        "daftar_tugas" -> formatDaftarTugas(root, userText, today)
        "cari_catatan" -> formatCariCatatan(root)
        "tambah_tugas", "ubah_jadwal", "tambah_catatan", "tandai_tugas_selesai" -> formatWriteResult(root)
        else -> message.ifBlank { "Selesai." }
    }
}

private fun formatKelasHari(root: AgentJsonValue?): String {
    val message = root.string("pesan")
    val items = root.field("kelas").asItems
    if (items.isEmpty()) {
        return message.ifBlank {
            "Tidak ada kelas pada ${root.string("hari").ifBlank { "hari itu" }}."
        }
    }
    val header = "Jadwal ${root.string("hari")}, ${formatIsoDate(root.string("tanggal"))}:"
    val lines = items.joinToString("\n") { classLine(it) }
    val note = root.string("catatan_semester")
    return header + "\n" + lines + if (note.isNotBlank()) "\n$note" else ""
}

private fun formatKelasBerikutnya(root: AgentJsonValue?): String {
    val message = root.string("pesan")
    if (root.string("status") in listOf("kosong", "selesai")) return message.ifBlank { "Tidak ada kelas." }
    val active = root.field("sedang_berlangsung").asItems
    val next = root.field("berikutnya")
    val minutes = root.intOrNull("menit_ke_berikutnya")
    val parts = ArrayList<String>()
    if (active.isNotEmpty()) {
        parts += "Sedang berlangsung:\n" + active.joinToString("\n") { classLine(it) }
    }
    if (next != null && !next.isNull) {
        var line = "Berikutnya: ${next.string("matkul")}, ${next.string("hari")} " +
            "${next.string("mulai")}–${next.string("selesai")}"
        val room = next.string("ruangan")
        if (room.isNotBlank()) line += " · $room"
        if (minutes != null) line += " (dalam $minutes menit)"
        parts += line
    }
    if (parts.isEmpty()) return message.ifBlank { "Tidak ada kelas lagi hari ini." }
    return parts.joinToString("\n")
}

private fun formatJadwalSeminggu(root: AgentJsonValue?): String {
    val message = root.string("pesan")
    if (root.string("status") == "selesai") return message.ifBlank { "Semester sudah berakhir." }
    val days = root.field("hari").asItems
    if (days.isEmpty()) return message.ifBlank { "Jadwal minggu ini kosong." }
    val lines = days.joinToString("\n") { day ->
        val classes = day.field("kelas").asItems
        val label = "${day.string("hari")}, ${formatIsoDate(day.string("tanggal"))}"
        if (classes.isEmpty()) {
            "• $label: tidak ada kelas"
        } else {
            "• $label:\n" + classes.joinToString("\n") { classLine(it) }
        }
    }
    return "Jadwal minggu ini:\n$lines"
}

private fun formatCariRuangan(root: AgentJsonValue?): String {
    val message = root.string("pesan")
    val items = root.field("hasil").asItems
    if (items.isEmpty()) return message.ifBlank { "Tidak ada mata kuliah yang cocok." }
    return items.joinToString("\n") { item ->
        var line = "• ${item.string("matkul")} — ${item.string("ruangan")} " +
            "(${item.string("hari")} ${item.string("mulai")}–${item.string("selesai")})"
        val note = item.string("catatan_ruangan")
        if (note.isNotBlank()) line += " ($note)"
        line
    }
}

private fun formatDaftarTugas(root: AgentJsonValue?, userText: String, today: LocalDate): String {
    val query = normalizeQuery(userText)
    val dueDate = when {
        "besok" in query -> today.plusDays(1)
        "lusa" in query -> today.plusDays(2)
        "hari ini" in query -> today
        else -> null
    }
    val titleFilter = Regex("cari tugas\\s+(.+)").find(query)?.groupValues?.getOrNull(1).orEmpty().trim()
    val courseFilter = Regex("(?:mata kuliah|matkul)\\s+(.+?)(?=\\s+(?:tenggat|deadline|tanggal|besok|lusa|hari ini|$))")
        .find(query)?.groupValues?.getOrNull(1).orEmpty().trim()
    var tasks = root.field("tugas").asItems
    if (dueDate != null) tasks = tasks.filter { it.string("tenggat") == dueDate.toString() }
    if (titleFilter.isNotBlank()) {
        tasks = tasks.filter { normalize(it.string("judul")).contains(titleFilter) }
    }
    if (courseFilter.isNotBlank()) {
        tasks = tasks.filter { normalize(it.string("matkul")).contains(courseFilter) }
    }
    if (tasks.isEmpty()) {
        if (dueDate != null) return "Tidak ada tugas bertenggat ${dateText(dueDate)}."
        return if (root.string("filter") == "selesai") {
            "Tidak ada tugas yang sudah selesai."
        } else {
            "Tidak ada tugas yang belum selesai."
        }
    }
    val header = when {
        dueDate != null -> "Tugas bertenggat ${dateText(dueDate)} (${tasks.size}):"
        root.string("filter") == "selesai" -> "Tugas selesai (${tasks.size}):"
        else -> "Tugas belum selesai (${tasks.size}):"
    }
    val lines = tasks.joinToString("\n") { task ->
        var line = "• ${task.string("judul")}"
        val course = task.string("matkul")
        if (course.isNotBlank()) line += " — $course"
        val due = task.string("tenggat")
        if (due.isNotBlank()) line += " · tenggat ${formatIsoDate(due)}"
        if (task.boolOrNull("terlambat") == true) line += " (terlambat)"
        line
    }
    return "$header\n$lines"
}

private fun formatCariCatatan(root: AgentJsonValue?): String {
    val message = root.string("pesan")
    val items = root.field("catatan").asItems
    if (items.isEmpty()) return message.ifBlank { "Tidak ada catatan yang cocok." }
    return items.joinToString("\n") { item ->
        val snippet = item.string("cuplikan")
        "• ${item.string("judul")}" + if (snippet.isNotBlank()) ": $snippet" else ""
    }
}

private fun formatWriteResult(root: AgentJsonValue?): String {
    val message = root.string("pesan")
    val warnings = root.field("peringatan").asItems.mapNotNull { it.asText }.filter { it.isNotBlank() }
    val base = message.ifBlank { "Selesai." }
    if (warnings.isEmpty()) return base
    return base + "\nPerhatian:\n- " + warnings.joinToString("\n- ")
}

private fun classLine(item: AgentJsonValue): String {
    var line = "• ${item.string("mulai")}–${item.string("selesai")} ${item.string("matkul")}"
    val room = item.string("ruangan")
    if (room.isNotBlank()) line += " · $room"
    val section = item.string("kelas")
    if (section.isNotBlank()) line += " · kelas $section"
    val note = item.string("catatan_ruangan")
    if (note.isNotBlank()) line += " ($note)"
    return line
}

private fun formatIsoDate(iso: String): String {
    if (iso.isBlank()) return iso
    return try {
        dateText(LocalDate.parse(iso))
    } catch (_: Exception) {
        iso
    }
}
