package id.personal.campus.agent

import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiClientTest {

    private val config = AgentConfig(baseUrl = "https://contoh.id/v1", temperature = 0.3, maxTokens = 512)
    private val tool = AgentToolSpec("kelas_berikutnya", "Kelas setelah ini.", """{"type":"object","properties":{}}""")

    private fun request(
        apiKey: String = "sk-rahasia-pemilik-123456",
        model: String = "model-cepat",
        tools: List<AgentToolSpec> = listOf(tool),
        messages: List<AgentMessage> = listOf(AgentMessage(AgentRole.User, "kelas apa setelah ini?")),
    ) = AgentRequest(config = config, apiKey = apiKey, model = model, messages = messages, tools = tools)

    @Test fun streamsTokensAndToolCalls() = runTest {
        val transport = FakeTransport(
            listOf(
                Step(
                    chunks = listOf(
                        "data: {\"choices\":[{\"delta\":{\"content\":\"Se",
                        "karang\"}}]}\n\ndata: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\"," +
                            "\"function\":{\"name\":\"kelas_berikutnya\",\"arguments\":\"{}\"}}]}}]}\n\n",
                        "data: [DONE]\n\n",
                    ),
                ),
            ),
        )
        val events = OpenAiModel(transport).stream(request()).toList()

        assertEquals("Sekarang", events.filterIsInstance<AgentEvent.Token>().joinToString("") { it.text })
        val calls = events.filterIsInstance<AgentEvent.ToolCalls>().single().calls
        assertEquals("kelas_berikutnya", calls.single().name)
        assertFalse(events.filterIsInstance<AgentEvent.Done>().single().usedPromptFallback)
        assertEquals("https://contoh.id/v1/chat/completions", transport.requests.single().url)
        assertTrue(transport.requests.single().bodyJson.contains("\"tool_choice\":\"auto\""))
        assertEquals("sk-rahasia-pemilik-123456", transport.requests.single().apiKey)
    }

    @Test fun retriesWithoutToolsWhenServerRejectsThem() = runTest {
        val transport = FakeTransport(
            listOf(
                Step(code = 400, errorBody = """{"error":{"message":"tools not supported yet"}}"""),
                Step(chunks = listOf("data: {\"choices\":[{\"delta\":{\"content\":\"Tidak ada kelas.\"}}]}\n", "data: [DONE]\n")),
            ),
        )
        val events = OpenAiModel(transport).stream(request()).toList()

        assertEquals(2, transport.requests.size)
        assertTrue(transport.requests[0].bodyJson.contains("\"tools\":"))
        assertFalse(transport.requests[1].bodyJson.contains("\"tools\":"))
        assertFalse(transport.requests[1].bodyJson.contains("\"tool_choice\""))
        assertTrue(transport.requests[1].bodyJson.contains("blok kode JSON"))
        assertEquals("Tidak ada kelas.", events.filterIsInstance<AgentEvent.Token>().joinToString("") { it.text })
        assertTrue(events.filterIsInstance<AgentEvent.Done>().single().usedPromptFallback)
        assertTrue(events.none { it is AgentEvent.Failure })
    }

    @Test fun mapsUnauthorizedWithoutLeakingTheKey() = runTest {
        val key = "sk-rahasia-pemilik-123456"
        val transport = FakeTransport(
            listOf(Step(code = 401, errorBody = """{"error":{"message":"Invalid API key $key (Bearer $key)"}}""")),
        )
        val events = OpenAiModel(transport).stream(request(apiKey = key)).toList()

        val failure = events.filterIsInstance<AgentEvent.Failure>().single().failure
        assertEquals(AgentFailureKind.Unauthorized, failure.kind)
        assertFalse(failure.message.contains(key))
        assertFalse(failure.detail.contains(key))
        assertTrue(events.none { it is AgentEvent.Done })
    }

    @Test fun doesNotRetryWhenToolsWereNotRequested() = runTest {
        val transport = FakeTransport(listOf(Step(code = 400, errorBody = "{\"error\":{\"message\":\"bad request\"}}")))

        val events = OpenAiModel(transport).stream(request(tools = emptyList())).toList()

        assertEquals(1, transport.requests.size)
        assertEquals(AgentFailureKind.BadRequest, events.filterIsInstance<AgentEvent.Failure>().single().failure.kind)
    }

    @Test fun mapsNetworkExceptionsToIndonesianMessages() = runTest {
        val transport = FakeTransport(listOf(Step(failure = UnknownHostException("contoh.id"))))

        val failure = OpenAiModel(transport).stream(request()).toList()
            .filterIsInstance<AgentEvent.Failure>().single().failure

        assertEquals(AgentFailureKind.Dns, failure.kind)
    }

    @Test fun failsFastWhenConfigurationIsIncomplete() = runTest {
        val transport = FakeTransport(emptyList())
        val noUrl = AgentRequest(AgentConfig(), apiKey = "k", model = "m", messages = emptyList())
        val noModel = request(model = "")

        val urlFailure = OpenAiModel(transport).stream(noUrl).toList().filterIsInstance<AgentEvent.Failure>().single()
        val modelFailure = OpenAiModel(transport).stream(noModel).toList().filterIsInstance<AgentEvent.Failure>().single()

        assertEquals(AgentFailureKind.BadRequest, urlFailure.failure.kind)
        assertTrue(urlFailure.failure.message.contains("Alamat server"))
        assertEquals(AgentFailureKind.BadRequest, modelFailure.failure.kind)
        assertTrue(modelFailure.failure.message.contains("model"))
        assertEquals(0, transport.requests.size)
    }

    @Test fun cancellationStopsTheFlowAtTheFirstToken() = runTest {
        val transport = FakeTransport(
            listOf(
                Step(
                    chunks = listOf(
                        "data: {\"choices\":[{\"delta\":{\"content\":\"Satu\"}}]}\n\n",
                        "data: {\"choices\":[{\"delta\":{\"content\":\"Dua\"}}]}\n\n",
                        "data: [DONE]\n\n",
                    ),
                ),
            ),
        )

        val first = OpenAiModel(transport).stream(request()).first()

        assertEquals(AgentEvent.Token("Satu"), first)
    }

    /**
     * Regresi untuk error di HP: "Flow exception transparency is violated".
     * Transport nyata memanggil `onChunk` dari `Dispatchers.IO`, sementara flow dikumpulkan
     * di Main — dengan `flow` biasa, `emit` dari dispatcher lain melempar
     * `IllegalStateException` ("Flow invariant is violated"). `channelFlow` harus
     * menerimanya tanpa error.
     */
    @Test fun streamsWhenTransportEmitsFromAnotherDispatcher() = runTest {
        val transport = object : ChatTransport {
            override suspend fun stream(
                request: ChatHttpRequest,
                onChunk: suspend (String) -> Unit,
            ): ChatHttpResponse = withContext(Dispatchers.IO) {
                onChunk("data: {\"choices\":[{\"delta\":{\"content\":\"Besok\"}}]}\n\n")
                onChunk("data: [DONE]\n\n")
                ChatHttpResponse(200)
            }
        }
        val events = OpenAiModel(transport).stream(request(tools = emptyList())).toList()

        assertEquals("Besok", events.filterIsInstance<AgentEvent.Token>().joinToString("") { it.text })
        assertTrue(events.none { it is AgentEvent.Failure })
    }

    @Test fun toStringNeverExposesTheKey() {
        val request = request()

        assertFalse(request.toString().contains(request.apiKey))
        assertFalse(ChatHttpRequest("https://contoh.id", request.apiKey, "{}").toString().contains(request.apiKey))
    }

    private data class Step(
        val chunks: List<String> = emptyList(),
        val code: Int = 200,
        val errorBody: String = "",
        val failure: Exception? = null,
    )

    private class FakeTransport(private val steps: List<Step>) : ChatTransport {
        val requests = mutableListOf<ChatHttpRequest>()

        override suspend fun stream(
            request: ChatHttpRequest,
            onChunk: suspend (String) -> Unit,
        ): ChatHttpResponse {
            requests += request
            val step = steps.getOrElse(requests.size - 1) { steps.last() }
            step.failure?.let { throw it }
            for (chunk in step.chunks) onChunk(chunk)
            return ChatHttpResponse(step.code, step.errorBody)
        }
    }
}
