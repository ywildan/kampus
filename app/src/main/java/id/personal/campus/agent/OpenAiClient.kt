package id.personal.campus.agent

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/** Permintaan mentah ke endpoint chat. `toString()` sengaja tidak memuat kunci API. */
data class ChatHttpRequest(
    val url: String,
    val apiKey: String,
    val bodyJson: String,
) {
    override fun toString(): String = "ChatHttpRequest(url=$url, body=${bodyJson.length} karakter)"
}

data class ChatHttpResponse(
    val code: Int,
    val errorBody: String = "",
)

/** Abstraksi transport supaya percobaan ulang tanpa `tools` bisa diuji di JVM tanpa jaringan. */
interface ChatTransport {
    /**
     * Kirim permintaan dan serahkan potongan teks mentah dari socket ke [onChunk].
     * Melempar exception jaringan apa adanya (`IOException` dsb) — pemetaan ke pesan Indonesia
     * dilakukan [OpenAiModel] lewat [failureForException].
     */
    suspend fun stream(request: ChatHttpRequest, onChunk: suspend (String) -> Unit): ChatHttpResponse
}

/**
 * Transport nyata: `HttpURLConnection` (tanpa OkHttp/Retrofit).
 * Batas waktu: connect 20 detik, baca 120 detik (jawaban model bisa lambat di awal).
 */
class HttpUrlChatTransport(
    private val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = READ_TIMEOUT_MS,
) : ChatTransport {

    override suspend fun stream(
        request: ChatHttpRequest,
        onChunk: suspend (String) -> Unit,
    ): ChatHttpResponse = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doOutput = true
                instanceFollowRedirects = false
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "text/event-stream")
                if (request.apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${request.apiKey}")
                }
            }
            val payload = request.bodyJson.toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { stream -> stream.write(payload) }

            val code = connection.responseCode
            if (code !in 200..299) {
                return@withContext ChatHttpResponse(code, readText(connection.errorStream))
            }

            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(4_096)
                while (true) {
                    // Pemeriksaan aktif di tiap putaran: read() yang sedang menunggu tidak bisa
                    // diinterupsi, jadi pembatalan harus terdeteksi di antara potongan.
                    currentCoroutineContext().ensureActive()
                    val read = reader.read(buffer)
                    if (read < 0) break
                    if (read > 0) onChunk(String(buffer, 0, read))
                }
            }
            ChatHttpResponse(code)
        } finally {
            connection?.disconnect()
        }
    }

    private fun readText(stream: InputStream?): String {
        if (stream == null) return ""
        return try {
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                val builder = StringBuilder()
                val buffer = CharArray(2_048)
                while (builder.length < MAX_ERROR_CHARS) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    builder.append(buffer, 0, read)
                }
                builder.toString()
            }
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 120_000
        private const val MAX_ERROR_CHARS = 4_000
    }
}

/**
 * Klien OpenAI-compatible: `POST {base}/chat/completions` dengan `stream: true`.
 *
 * Alur satu permintaan:
 * 1. kirim pesan + `tools` + `tool_choice: auto`;
 * 2. baca SSE, terbitkan [AgentEvent.Token] bertahap dan kumpulkan `delta.tool_calls`;
 * 3. bila aliran selesai dan ada tool call, terbitkan [AgentEvent.ToolCalls] lalu [AgentEvent.Done].
 *
 * Bila server menolak `tools` (400/422), permintaan diulang **sekali** tanpa `tools` sambil
 * meminta model menulis satu blok JSON berpagar ([PromptToolProtocol]); [AgentEvent.Done]
 * membawa tanda `usedPromptFallback = true` supaya loop sesi memakai [PromptFallback].
 * Server yang diam-diam mengabaikan `tools` (menjawab teks biasa) ditangani di Tahap 3.
 *
 * Kunci API tidak pernah masuk pesan error: badan error selalu lewat [redact]. Pembatalan user
 * menutup koneksi lewat `finally { disconnect() }` di [HttpUrlChatTransport].
 */
class OpenAiModel(
    private val transport: ChatTransport = HttpUrlChatTransport(),
) : AgentModel {

    override fun stream(request: AgentRequest): Flow<AgentEvent> = flow {
        val url = request.config.chatCompletionsUrl
        if (url == null) {
            emit(AgentEvent.Failure(invalidUrlFailure(request.config.baseUrl)))
            return@flow
        }
        if (request.model.isBlank()) {
            emit(
                AgentEvent.Failure(
                    AgentFailure(
                        AgentFailureKind.BadRequest,
                        "Nama model belum dipilih. Isi di Setelan → Asisten AI.",
                    ),
                ),
            )
            return@flow
        }

        val parser = ChatStreamParser()
        val hasTools = request.tools.isNotEmpty()

        when (val first = attempt(url, request, parser, request.messages, withTools = hasTools) { emit(it) }) {
            AttemptResult.Streamed -> Unit
            is AttemptResult.Failed -> {
                if (!hasTools || first.code !in TOOLS_REJECTED_CODES) {
                    emit(AgentEvent.Failure(first.failure))
                    return@flow
                }
                // Percobaan kedua: tanpa `tools`, dengan instruksi blok JSON.
                // Server menolak pada tahap status HTTP, jadi belum ada token yang terkirim ke UI.
                parser.reset()
                val messages = request.messages + AgentMessage(
                    role = AgentRole.System,
                    content = PromptToolProtocol.Instruction,
                )
                when (val retry = attempt(url, request, parser, messages, withTools = false) { emit(it) }) {
                    AttemptResult.Streamed -> Unit
                    is AttemptResult.Failed -> {
                        emit(AgentEvent.Failure(retry.failure))
                        return@flow
                    }
                }
                for (event in parser.finish()) {
                    emit(if (event is AgentEvent.Done) event.copy(usedPromptFallback = true) else event)
                }
                return@flow
            }
        }

        parser.finish().forEach { emit(it) }
    }

    private suspend fun attempt(
        url: String,
        request: AgentRequest,
        parser: ChatStreamParser,
        messages: List<AgentMessage>,
        withTools: Boolean,
        onEvent: suspend (AgentEvent) -> Unit,
    ): AttemptResult {
        val body = ChatRequestBuilder.body(
            model = request.model,
            messages = messages,
            tools = if (withTools) request.tools else emptyList(),
            temperature = request.config.temperature,
            maxTokens = request.config.maxTokens,
        )
        val lineBuffer = SseLineBuffer()
        val response = try {
            transport.stream(ChatHttpRequest(url, request.apiKey, body)) { chunk ->
                for (line in lineBuffer.feed(chunk)) consume(line, parser, onEvent)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return AttemptResult.Failed(failureForException(error), code = -1)
        }
        lineBuffer.flush()?.let { consume(it, parser, onEvent) }

        if (response.code !in 200..299) {
            val detail = redact(
                response.errorBody.trim().take(MAX_ERROR_DETAIL_CHARS),
                listOf(request.apiKey),
            )
            return AttemptResult.Failed(failureForStatus(response.code, detail), response.code)
        }
        return AttemptResult.Streamed
    }

    private suspend fun consume(
        line: String,
        parser: ChatStreamParser,
        onEvent: suspend (AgentEvent) -> Unit,
    ) {
        val payload = SseData.payload(line) ?: return
        for (event in parser.feedPayload(payload)) onEvent(event)
    }

    private fun invalidUrlFailure(baseUrl: String): AgentFailure {
        val message = (normalizeBaseUrl(baseUrl) as? AgentUrlResult.Invalid)?.message
            ?: "Alamat server belum diisi. Isi di Setelan → Asisten AI."
        return AgentFailure(AgentFailureKind.BadRequest, message)
    }

    private sealed interface AttemptResult {
        data object Streamed : AttemptResult
        data class Failed(val failure: AgentFailure, val code: Int) : AttemptResult
    }

    private companion object {
        /** Server yang menjawab kode ini dianggap tidak menerima parameter `tools`. */
        val TOOLS_REJECTED_CODES = setOf(400, 422)
        const val MAX_ERROR_DETAIL_CHARS = 300
    }
}
