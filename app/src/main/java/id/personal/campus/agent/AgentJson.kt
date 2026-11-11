package id.personal.campus.agent

/**
 * Pembaca/penulis JSON kecil milik paket `agent`.
 *
 * **Kenapa tidak memakai `org.json`?** `org.json` memang ada di Android, tetapi kelasnya
 * disediakan oleh `android.jar` yang di unit test JVM diganti dengan versi "mockable"
 * (metode tanpa isi). Akibatnya parser SSE dan pemanggilan tool — yang justru logika inti
 * Tahap 2 — tidak bisa diuji di CI, padahal CI adalah satu-satunya tempat kode ini dikompilasi
 * (lihat AGENTS.md). Berkas ini **bebas `android.*` dan bebas dependency baru**, jadi seluruh
 * pemetaan data di paket ini bisa diuji lewat `AgentJsonTest`.
 *
 * Batasan yang disengaja: hanya untuk membaca/menulis JSON (tanpa validasi skema), ada batas
 * kedalaman dan panjang, dan semua kegagalan diterjemahkan menjadi `null`/nilai default —
 * input dari jaringan tidak boleh membuat aplikasi berhenti.
 */
sealed interface AgentJsonValue {
    data class Obj(val fields: Map<String, AgentJsonValue>) : AgentJsonValue
    data class Arr(val items: List<AgentJsonValue>) : AgentJsonValue
    data class Str(val value: String) : AgentJsonValue

    /** Angka disimpan sebagai teks mentah supaya `0.3` tidak berubah menjadi `0.30000000000000004`. */
    data class Num(val raw: String) : AgentJsonValue
    data class Bool(val value: Boolean) : AgentJsonValue
    data object Null : AgentJsonValue

    val isNull: Boolean get() = this is Null
}

fun objOf(vararg fields: Pair<String, AgentJsonValue>): AgentJsonValue = AgentJsonValue.Obj(linkedMapOf(*fields))
fun arrOf(vararg items: AgentJsonValue): AgentJsonValue = AgentJsonValue.Arr(items.toList())
fun strOf(value: String): AgentJsonValue = AgentJsonValue.Str(value)
fun numOf(value: Int): AgentJsonValue = AgentJsonValue.Num(value.toString())
fun numOf(value: Double): AgentJsonValue = AgentJsonValue.Num(formatAgentNumber(value))
fun boolOf(value: Boolean): AgentJsonValue = AgentJsonValue.Bool(value)

private fun formatAgentNumber(value: Double): String {
    if (!value.isFinite()) return "0"
    val whole = value.toLong()
    return if (whole.toDouble() == value && whole in -1_000_000_000_000_000L..1_000_000_000_000_000L) {
        whole.toString()
    } else {
        value.toString()
    }
}

object AgentJson {
    private const val MAX_INPUT_CHARS = 4_000_000
    private const val MAX_DEPTH = 48

    /** `null` bila teks bukan JSON yang utuh (bukan exception: isi dari jaringan tidak dipercaya). */
    fun parse(text: String): AgentJsonValue? {
        if (text.isEmpty() || text.length > MAX_INPUT_CHARS) return null
        val reader = JsonReader(text)
        return try {
            val value = reader.readValue(0)
            reader.skipWhitespace()
            if (reader.hasMore) null else value
        } catch (_: JsonFormatException) {
            null
        }
    }

    /** Kebalikan [parse]; nilai yang tidak bisa ditulis diganti `null`. */
    fun write(value: AgentJsonValue): String {
        val out = StringBuilder(256)
        appendValue(value, out)
        return out.toString()
    }

    private fun appendValue(value: AgentJsonValue, out: StringBuilder) {
        when (value) {
            is AgentJsonValue.Obj -> {
                out.append('{')
                var first = true
                for ((key, item) in value.fields) {
                    if (!first) out.append(',')
                    first = false
                    appendString(key, out)
                    out.append(':')
                    appendValue(item, out)
                }
                out.append('}')
            }
            is AgentJsonValue.Arr -> {
                out.append('[')
                value.items.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    appendValue(item, out)
                }
                out.append(']')
            }
            is AgentJsonValue.Str -> appendString(value.value, out)
            is AgentJsonValue.Num -> out.append(if (value.raw.isBlank()) "0" else value.raw)
            is AgentJsonValue.Bool -> out.append(if (value.value) "true" else "false")
            AgentJsonValue.Null -> out.append("null")
        }
    }

    private fun appendString(value: String, out: StringBuilder) {
        out.append('"')
        for (char in value) {
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (char < ' ') {
                    out.append("\\u").append(char.code.toString(16).padStart(4, '0'))
                } else {
                    out.append(char)
                }
            }
        }
        out.append('"')
    }
}

// ---------------------------------------------------------------------------
// Akses aman: field hilang atau tipe salah menghasilkan default, bukan crash.
// ---------------------------------------------------------------------------

fun AgentJsonValue?.field(name: String): AgentJsonValue? = (this as? AgentJsonValue.Obj)?.fields?.get(name)

fun AgentJsonValue?.stringOrNull(name: String): String? = (field(name) as? AgentJsonValue.Str)?.value

fun AgentJsonValue?.string(name: String): String = stringOrNull(name).orEmpty()

fun AgentJsonValue?.intOrNull(name: String): Int? =
    (field(name) as? AgentJsonValue.Num)?.raw?.toDoubleOrNull()?.toInt()

fun AgentJsonValue?.boolOrNull(name: String): Boolean? = (field(name) as? AgentJsonValue.Bool)?.value

val AgentJsonValue?.asText: String? get() = (this as? AgentJsonValue.Str)?.value

val AgentJsonValue?.asItems: List<AgentJsonValue>
    get() = when (this) {
        is AgentJsonValue.Arr -> items
        null, AgentJsonValue.Null -> emptyList()
        else -> listOf(this)
    }

val AgentJsonValue?.asObjectFields: Map<String, AgentJsonValue>
    get() = (this as? AgentJsonValue.Obj)?.fields ?: emptyMap()

/** Nilai yang mungkin datang sebagai objek **atau** sebagai string JSON (kebiasaan sebagian server). */
fun AgentJsonValue?.asParsedJson(): AgentJsonValue? = when (this) {
    is AgentJsonValue.Str -> AgentJson.parse(value)
    else -> this
}

private class JsonFormatException : Exception()

private class JsonReader(private val text: String) {
    private var index = 0

    val hasMore: Boolean get() = index < text.length

    fun skipWhitespace() {
        while (index < text.length && text[index].isJsonWhitespace()) index++
    }

    fun readValue(depth: Int): AgentJsonValue {
        if (depth > MAX_DEPTH_LOCAL) throw JsonFormatException()
        skipWhitespace()
        if (!hasMore) throw JsonFormatException()
        return when (val char = text[index]) {
            '{' -> readObject(depth)
            '[' -> readArray(depth)
            '"' -> AgentJsonValue.Str(readString())
            't' -> literal("true", AgentJsonValue.Bool(true))
            'f' -> literal("false", AgentJsonValue.Bool(false))
            'n' -> literal("null", AgentJsonValue.Null)
            else -> if (char == '-' || char.isDigit()) AgentJsonValue.Num(readNumber()) else throw JsonFormatException()
        }
    }

    private fun readObject(depth: Int): AgentJsonValue {
        index++ // '{'
        val fields = LinkedHashMap<String, AgentJsonValue>()
        skipWhitespace()
        if (hasMore && text[index] == '}') {
            index++
            return AgentJsonValue.Obj(fields)
        }
        while (true) {
            skipWhitespace()
            if (!hasMore || text[index] != '"') throw JsonFormatException()
            val key = readString()
            skipWhitespace()
            if (!hasMore || text[index] != ':') throw JsonFormatException()
            index++
            fields[key] = readValue(depth + 1)
            skipWhitespace()
            if (!hasMore) throw JsonFormatException()
            when (text[index]) {
                ',' -> index++
                '}' -> {
                    index++
                    return AgentJsonValue.Obj(fields)
                }
                else -> throw JsonFormatException()
            }
        }
    }

    private fun readArray(depth: Int): AgentJsonValue {
        index++ // '['
        val items = ArrayList<AgentJsonValue>()
        skipWhitespace()
        if (hasMore && text[index] == ']') {
            index++
            return AgentJsonValue.Arr(items)
        }
        while (true) {
            items += readValue(depth + 1)
            skipWhitespace()
            if (!hasMore) throw JsonFormatException()
            when (text[index]) {
                ',' -> index++
                ']' -> {
                    index++
                    return AgentJsonValue.Arr(items)
                }
                else -> throw JsonFormatException()
            }
        }
    }

    private fun readString(): String {
        index++ // '"'
        val out = StringBuilder()
        while (true) {
            if (!hasMore) throw JsonFormatException()
            when (val char = text[index]) {
                '"' -> {
                    index++
                    return out.toString()
                }
                '\\' -> {
                    index++
                    if (!hasMore) throw JsonFormatException()
                    when (val escape = text[index]) {
                        '"' -> out.append('"')
                        '\\' -> out.append('\\')
                        '/' -> out.append('/')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            if (index + 4 >= text.length) throw JsonFormatException()
                            val hex = text.substring(index + 1, index + 5)
                            val code = hex.toIntOrNull(16) ?: throw JsonFormatException()
                            out.append(code.toChar())
                            index += 4
                        }
                        else -> throw JsonFormatException()
                    }
                    index++
                }
                else -> {
                    if (char < ' ') throw JsonFormatException()
                    out.append(char)
                    index++
                }
            }
        }
    }

    private fun readNumber(): String {
        val start = index
        if (hasMore && text[index] == '-') index++
        while (hasMore && (text[index].isDigit() || text[index] in ".eE+-")) index++
        val raw = text.substring(start, index)
        if (raw.toDoubleOrNull() == null) throw JsonFormatException()
        return raw
    }

    private fun literal(expected: String, value: AgentJsonValue): AgentJsonValue {
        if (!text.startsWith(expected, index)) throw JsonFormatException()
        index += expected.length
        return value
    }

    private companion object {
        const val MAX_DEPTH_LOCAL = 48
    }
}

private fun Char.isJsonWhitespace(): Boolean = this == ' ' || this == '\t' || this == '\n' || this == '\r'
