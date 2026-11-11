package id.personal.campus.agent

/**
 * Jalur cadangan untuk server yang **menolak atau mengabaikan `tools`** (Ollama, LM Studio,
 * sebagian gateway). Model diminta menulis **satu blok JSON berpagar**; parser lokal ini yang
 * membacanya. Tanpa jalur ini, penyedia lokal tampak "rusak" padahal hanya tidak punya tool
 * calling.
 *
 * Bebas `android.*` dan bebas JSON pihak ketiga supaya bisa diuji di CI JVM.
 */

object PromptToolProtocol {
    /** Ditambahkan sebagai pesan sistem pada percobaan kedua (tanpa `tools`). */
    const val Instruction: String =
        "Server ini tidak menerima parameter tools. Jangan menjelaskan alasan. Bila kamu perlu " +
            "menjalankan salah satu tool di atas, balas HANYA dengan satu blok kode JSON seperti " +
            "ini dan tanpa teks lain di luar blok:\n" +
            "```json\n" +
            "{\"tool\":\"nama_tool\",\"arguments\":{}}\n" +
            "```\n" +
            "Bila tidak perlu tool, jawab dengan bahasa Indonesia biasa tanpa blok JSON."

    /**
     * Bentuk teks yang dipakai riwayat percakapan saat server tidak mendukung tool-calling:
     * hasil tool ditulis kembali sebagai blok JSON supaya model tetap paham apa yang terjadi.
     */
    fun callBlock(call: AgentToolCall): String {
        val arguments = AgentJson.parse(call.argumentsJson)
            ?.takeIf { it is AgentJsonValue.Obj }
            ?: objOf()
        return "```json\n" + AgentJson.write(
            objOf("tool" to strOf(call.name), "arguments" to arguments),
        ) + "\n```"
    }
}

data class PromptToolCallResult(
    val calls: List<AgentToolCall> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    val hasCalls: Boolean get() = calls.isNotEmpty()
}

object PromptFallback {

    /** Blok JSON pertama yang bisa dibaca dari jawaban model; `null` bila tidak ada. */
    fun extractJsonBlock(text: String): String? {
        for (block in fencedBlocks(text)) {
            jsonObjectInside(block)?.let { return it }
        }
        return jsonObjectInside(text)
    }

    /**
     * Terjemahkan jawaban model menjadi pemanggilan tool. Bentuk yang diterima:
     * `{"tool":"x","arguments":{...}}`, `{"name":"x","arguments":{...}}`, atau
     * `{"tool_calls":[{"name":"x","arguments":{...}}]}`. Argumen juga boleh berupa string JSON.
     */
    fun parse(text: String, allowedTools: Collection<String>): PromptToolCallResult {
        val block = extractJsonBlock(text)
            ?: return PromptToolCallResult(warnings = listOf("Model tidak mengeluarkan blok JSON."))
        val root = AgentJson.parse(block)
            ?: return PromptToolCallResult(warnings = listOf("Blok JSON dari model tidak dapat dibaca."))

        val warnings = mutableListOf<String>()
        val nodes = buildList {
            addAll(root.field("tool_calls").asItems)
            if (isEmpty() && (root.stringOrNull("tool") != null || root.stringOrNull("name") != null)) add(root)
        }
        if (nodes.isEmpty()) {
            return PromptToolCallResult(warnings = listOf("Blok JSON dari model tidak memuat nama tool."))
        }

        val calls = ArrayList<AgentToolCall>(nodes.size)
        for ((position, node) in nodes.withIndex()) {
            val name = (node.stringOrNull("tool")
                ?: node.stringOrNull("name")
                ?: node.field("function").stringOrNull("name")).orEmpty().trim()
            if (name.isEmpty()) {
                warnings += "Pemanggilan tool tanpa nama diabaikan."
                continue
            }
            if (allowedTools.isNotEmpty() && name !in allowedTools) {
                warnings += "Model meminta tool yang tidak diizinkan: $name (diabaikan)."
                continue
            }
            calls += AgentToolCall(
                id = node.stringOrNull("id") ?: "prompt_$position",
                name = name,
                argumentsJson = normalizeArguments(node.field("arguments")),
            )
        }
        if (calls.isEmpty()) warnings += "Tidak ada tool yang bisa dijalankan dari blok JSON itu."
        return PromptToolCallResult(calls = calls, warnings = warnings)
    }

    private fun normalizeArguments(node: AgentJsonValue?): String {
        if (node == null) return "{}"
        val parsed = node.asParsedJson()
        return when {
            parsed is AgentJsonValue.Obj -> AgentJson.write(parsed)
            parsed is AgentJsonValue.Str -> parsed.value.ifBlank { "{}" }
            parsed == null -> "{}"
            else -> AgentJson.write(parsed)
        }
    }

    private fun fencedBlocks(text: String): List<String> {
        val blocks = ArrayList<String>()
        var cursor = 0
        while (true) {
            val open = text.indexOf("```", cursor)
            if (open < 0) break
            val close = text.indexOf("```", open + 3)
            if (close < 0) break
            blocks += stripLanguageTag(text.substring(open + 3, close))
            cursor = close + 3
        }
        return blocks
    }

    private fun stripLanguageTag(block: String): String {
        val trimmed = block.trim()
        if (!trimmed.startsWith("{")) {
            val newline = trimmed.indexOf('\n')
            if (newline > 0 && trimmed.substring(0, newline).none { it == '{' || it == '"' }) {
                return trimmed.substring(newline + 1).trim()
            }
        }
        return trimmed
    }

    /** Ambil objek JSON pertama dengan kurung seimbang, lalu pastikan bisa di-parse. */
    private fun jsonObjectInside(text: String): String? {
        var start = text.indexOf('{')
        while (start >= 0) {
            var depth = 0
            var inString = false
            var escaped = false
            var index = start
            while (index < text.length) {
                val char = text[index]
                when {
                    escaped -> escaped = false
                    char == '\\' && inString -> escaped = true
                    char == '"' -> inString = !inString
                    !inString && char == '{' -> depth++
                    !inString && char == '}' -> {
                        depth--
                        if (depth == 0) {
                            val candidate = text.substring(start, index + 1)
                            if (AgentJson.parse(candidate) is AgentJsonValue.Obj) return candidate
                            break
                        }
                    }
                }
                index++
            }
            start = text.indexOf('{', start + 1)
        }
        return null
    }
}
