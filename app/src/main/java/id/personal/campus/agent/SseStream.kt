package id.personal.campus.agent

/**
 * Parser aliran SSE (`text/event-stream`) milik OpenAI-compatible.
 *
 * **Bebas `android.*` dan bebas JSON pihak ketiga** supaya bisa diuji di CI JVM: satu potongan
 * `read()` dari socket tidak selalu berisi satu baris utuh, dan argumen `tool_calls` datang
 * bertahap — dua hal itu yang paling mudah salah dan wajib ada test-nya.
 */

/** Menyusun potongan teks mentah dari socket menjadi baris-baris utuh. */
class SseLineBuffer(private val maxLineChars: Int = 200_000) {
    private val pending = StringBuilder()

    fun feed(chunk: String): List<String> {
        if (chunk.isEmpty()) return emptyList()
        pending.append(chunk)
        if (pending.length > maxLineChars && !pending.contains('\n')) {
            // Baris tak pernah berakhir: buang agar memori tidak tumbuh tanpa batas.
            pending.setLength(0)
            return emptyList()
        }
        val text = pending.toString()
        pending.setLength(0)
        val lines = ArrayList<String>()
        var start = 0
        while (true) {
            val newline = text.indexOf('\n', start)
            if (newline < 0) break
            lines += text.substring(start, newline).trimEnd('\r')
            start = newline + 1
        }
        if (start < text.length) pending.append(text, start, text.length)
        return lines
    }

    /** Sisa baris terakhir yang belum diakhiri newline (dipakai setelah socket habis). */
    fun flush(): String? {
        if (pending.isEmpty()) return null
        val rest = pending.toString().trimEnd('\r')
        pending.setLength(0)
        return rest.ifBlank { null }
    }
}

object SseData {
    const val DoneMarker = "[DONE]"

    /**
     * Ambil muatan setelah `data:`. `null` untuk baris kosong, komentar (`:` alias heartbeat),
     * dan baris non-data. JSON polos diterima karena sebagian server (mode non-SSE) mengirimnya.
     */
    fun payload(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith(":")) return null
        if (!trimmed.startsWith("data:")) {
            return if (trimmed.startsWith("{") || trimmed.startsWith("[")) trimmed else null
        }
        val rest = trimmed.removePrefix("data:").trim()
        return rest.ifEmpty { null }
    }
}

/**
 * Menumpuk `delta.tool_calls` dari beberapa potongan SSE.
 *
 * Argumen JSON datang sebagai potongan string ("{\"hari\":", " \"Rabu\"}"), jadi harus
 * disambung per `index` sebelum bisa dipakai.
 */
class ToolCallAccumulator {
    private class Partial {
        var id: String? = null
        var name: String? = null
        val arguments = StringBuilder()
    }

    private val parts = LinkedHashMap<Int, Partial>()

    val hasAny: Boolean get() = parts.isNotEmpty()

    fun accept(index: Int?, id: String?, name: String?, argumentsFragment: String?) {
        val key = index ?: parts.keys.lastOrNull() ?: 0
        val part = parts.getOrPut(key) { Partial() }
        if (!id.isNullOrBlank()) part.id = id
        if (!name.isNullOrBlank()) part.name = name
        if (!argumentsFragment.isNullOrBlank()) part.arguments.append(argumentsFragment)
    }

    /** Tool tanpa nama dibuang: tidak bisa dijalankan dan hanya membingungkan model. */
    fun snapshot(): List<AgentToolCall> = parts.entries.sortedBy { it.key }.map { (key, part) ->
        AgentToolCall(
            id = part.id?.takeIf { it.isNotBlank() } ?: "call_$key",
            name = part.name.orEmpty().trim(),
            argumentsJson = part.arguments.toString().ifBlank { "{}" },
        )
    }.filter { it.name.isNotEmpty() }

    fun reset() = parts.clear()
}

/**
 * Mengubah muatan `data:` menjadi [AgentEvent].
 *
 * Toleran terhadap bentuk yang berbeda-beda: `delta` atau `message`, `content` berupa string
 * atau daftar bagian, dan `error` di akar muatan.
 */
class ChatStreamParser {
    private val accumulator = ToolCallAccumulator()
    private var finishReason: String? = null
    private var payloadCount = 0
    private var brokenPayloads = 0

    val sawAnyPayload: Boolean get() = payloadCount > 0
    val malformedPayloads: Int get() = brokenPayloads

    fun reset() {
        accumulator.reset()
        finishReason = null
        payloadCount = 0
        brokenPayloads = 0
    }

    fun feedPayload(payload: String): List<AgentEvent> {
        val text = payload.trim()
        if (text.isEmpty() || text == SseData.DoneMarker) return emptyList()
        payloadCount++
        val root = AgentJson.parse(text) ?: run {
            brokenPayloads++
            return emptyList()
        }
        root.field("error")?.let { return listOf(AgentEvent.Failure(failureFromErrorNode(it))) }

        val choice = root.field("choices").asItems.firstOrNull() ?: return emptyList()
        val delta = choice.field("delta") ?: choice.field("message")
        val events = ArrayList<AgentEvent>(2)

        deltaText(delta)?.takeIf { it.isNotEmpty() }?.let { events += AgentEvent.Token(it) }

        val toolCalls = delta.field("tool_calls").asItems
        for ((position, node) in toolCalls.withIndex()) {
            val function = node.field("function")
            accumulator.accept(
                index = node.intOrNull("index") ?: if (toolCalls.size == 1 && position == 0) null else position,
                id = node.stringOrNull("id"),
                name = function.stringOrNull("name") ?: node.stringOrNull("name"),
                argumentsFragment = function.stringOrNull("arguments") ?: node.field("arguments").asText,
            )
        }

        choice.stringOrNull("finish_reason")?.let { finishReason = it }
        return events
    }

    /**
     * Dipanggil setelah aliran habis (socket tertutup, `[DONE]`, atau dibatalkan user).
     * Selalu menghasilkan tepat satu [AgentEvent.Done].
     */
    fun finish(): List<AgentEvent> {
        val events = ArrayList<AgentEvent>(2)
        val calls = accumulator.snapshot()
        if (calls.isNotEmpty()) events += AgentEvent.ToolCalls(calls)
        events += AgentEvent.Done(finishReason)
        return events
    }

    private fun deltaText(delta: AgentJsonValue?): String? {
        val direct = delta.stringOrNull("content")
        if (direct != null) return direct
        val parts = delta.field("content").asItems
        if (parts.isEmpty()) return null
        val builder = StringBuilder()
        for (part in parts) {
            builder.append(part.stringOrNull("text") ?: part.asText.orEmpty())
        }
        return builder.toString()
    }
}

/** `{"error":{"message":"..."}}` maupun `{"error":"..."}`. */
internal fun failureFromErrorNode(node: AgentJsonValue): AgentFailure {
    val message = (node.field("message")?.asText ?: node.asText).orEmpty().trim()
    val code = node.intOrNull("code") ?: 0
    if (code in 400..599) return failureForStatus(code, message)
    return AgentFailure(
        kind = AgentFailureKind.Malformed,
        message = "Server menolak permintaan: ${message.ifBlank { "tanpa keterangan" }}",
        detail = message,
    )
}
