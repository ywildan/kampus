package id.personal.campus.agent

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `GET {base}/models` untuk tombol "Tes koneksi" dan "Ambil daftar model".
 *
 * Tanpa dependency baru: `HttpURLConnection` + [AgentJson] (bukan `org.json`, supaya
 * [parseModels] tetap bisa diuji di CI JVM — lihat modul KDoc di AgentJson.kt).
 * Klien streaming (Tahap 2, [OpenAiModel]) memakai [failureForStatus]/[failureForException]
 * yang sama supaya pesan errornya konsisten.
 */
object ModelCatalog {
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MAX_BODY_CHARS = 200_000
    private const val MAX_DETAIL_CHARS = 300

    suspend fun listModels(baseUrl: String, apiKey: String?): AgentResult<List<String>> =
        withContext(Dispatchers.IO) {
            val normalized = when (val check = normalizeBaseUrl(baseUrl)) {
                is AgentUrlResult.Valid -> check.baseUrl
                is AgentUrlResult.Invalid -> return@withContext AgentResult.Failure(
                    AgentFailure(AgentFailureKind.BadRequest, check.message),
                )
            }
            var active: HttpURLConnection? = null
            try {
                val connection = (URL("$normalized/models").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                    setRequestProperty("Accept", "application/json")
                    if (!apiKey.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
                }
                active = connection
                val code = connection.responseCode
                val body = readBody(if (code in 200..299) connection.inputStream else connection.errorStream)
                if (code !in 200..299) {
                    return@withContext AgentResult.Failure(failureForStatus(code, serverDetail(body, apiKey)))
                }
                AgentResult.Success(parseModels(body))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AgentResult.Failure(failureForException(error))
            } finally {
                active?.disconnect()
            }
        }

    /** Menerima `{"data":[{"id":...}]}`, `{"models":[{"name":...}]}` (Ollama), atau array di akar. */
    internal fun parseModels(body: String): List<String> {
        val root = AgentJson.parse(body.trim()) ?: return emptyList()
        val nodes = if (root is AgentJsonValue.Arr) {
            root.items
        } else {
            root.field("data").asItems.ifEmpty { root.field("models").asItems }
        }
        val ids = LinkedHashSet<String>()
        for (node in nodes) {
            val id = (node.stringOrNull("id") ?: node.stringOrNull("name")).orEmpty().trim()
            if (id.isNotEmpty()) ids += id
        }
        return ids.sorted()
    }

    /** Cuplikan pesan server untuk ditampilkan; selalu disensor sebelum keluar dari sini. */
    private fun serverDetail(body: String, apiKey: String?): String {
        val root = AgentJson.parse(body.trim())
        val message = when (val error = root.field("error")) {
            null -> root.string("message")
            is AgentJsonValue.Str -> error.value
            is AgentJsonValue.Obj -> error.field("message")?.asText ?: root.string("message")
            else -> root.string("message")
        }.trim()
        val fallback = body.trim().take(MAX_DETAIL_CHARS)
        return redact(message.ifBlank { fallback }, listOfNotNull(apiKey))
    }

    private fun readBody(stream: InputStream?): String {
        if (stream == null) return ""
        return try {
            stream.bufferedReader(Charsets.UTF_8).use { reader ->
                val builder = StringBuilder()
                val buffer = CharArray(8_192)
                while (builder.length < MAX_BODY_CHARS) {
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
}
