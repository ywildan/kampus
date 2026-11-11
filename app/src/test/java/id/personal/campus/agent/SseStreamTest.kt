package id.personal.campus.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SseStreamTest {

    @Test fun lineBufferReassemblesLineSplitAcrossReads() {
        val buffer = SseLineBuffer()

        assertEquals(emptyList<String>(), buffer.feed("data: {\"cho"))
        assertEquals(emptyList<String>(), buffer.feed("ices\":[]}"))
        assertEquals(listOf("data: {\"choices\":[]}", "x"), buffer.feed("\nx\n"))
    }

    @Test fun lineBufferStripsCarriageReturnsAndFlushesTail() {
        val buffer = SseLineBuffer()

        assertEquals(listOf("data: a", "data: b"), buffer.feed("data: a\r\ndata: b\r\n"))
        assertEquals(emptyList<String>(), buffer.feed("data: c"))
        assertEquals("data: c", buffer.flush())
        assertNull(buffer.flush())
    }

    @Test fun lineBufferDropsRunawayLine() {
        val buffer = SseLineBuffer(maxLineChars = 32)

        assertEquals(emptyList<String>(), buffer.feed("x".repeat(100)))
        assertNull(buffer.flush())
        assertEquals(listOf("data: ok"), buffer.feed("data: ok\n"))
    }

    @Test fun dataPayloadIgnoresHeartbeatsAndNonDataLines() {
        assertNull(SseData.payload(""))
        assertNull(SseData.payload("   "))
        assertNull(SseData.payload(": ping"))
        assertNull(SseData.payload("event: message"))
        assertEquals("[DONE]", SseData.payload("data: [DONE]"))
        assertEquals("[DONE]", SseData.payload("data:[DONE]"))
        assertEquals("""{"a":1}""", SseData.payload("""data: {"a":1}"""))
        assertEquals("""{"a":1}""", SseData.payload("""{"a":1}"""))
        assertEquals("[]", SseData.payload("[]"))
    }

    @Test fun parserEmitsTokensFromDeltas() {
        val parser = ChatStreamParser()

        val first = parser.feedPayload("""{"choices":[{"delta":{"content":"Kelas "}}]}""")
        val second = parser.feedPayload("""{"choices":[{"delta":{"content":"berikutnya"}}]}""")
        val done = parser.feedPayload("[DONE]")
        val tail = parser.finish()

        assertEquals(listOf(AgentEvent.Token("Kelas ")), first)
        assertEquals(listOf(AgentEvent.Token("berikutnya")), second)
        assertEquals(emptyList<AgentEvent>(), done)
        assertEquals(1, tail.size)
        assertTrue((tail[0] as AgentEvent.Done).finishReason == null)
        assertTrue(parser.sawAnyPayload)
    }

    @Test fun parserAccumulatesToolArgumentsAcrossChunks() {
        val parser = ChatStreamParser()

        parser.feedPayload(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1",
               "function":{"name":"ubah_jadwal","arguments":"{\"matkul\":\"Bahasa"}}]}}]}""".trimIndent(),
        )
        parser.feedPayload(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":" Indonesia\",\"mulai\":780}"}}]}}]}""",
        )
        parser.feedPayload("""{"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""")
        val tail = parser.finish()

        val calls = (tail[0] as AgentEvent.ToolCalls).calls
        assertEquals(1, calls.size)
        assertEquals("call_1", calls[0].id)
        assertEquals("ubah_jadwal", calls[0].name)
        assertEquals("""{"matkul":"Bahasa Indonesia","mulai":780}""", calls[0].argumentsJson)
        assertEquals("tool_calls", (tail[1] as AgentEvent.Done).finishReason)
    }

    @Test fun parserSeparatesParallelToolCallsByIndex() {
        val parser = ChatStreamParser()

        // Potongan index 1 datang lebih dulu, lalu index 0 — urutan tidak boleh tertukar.
        parser.feedPayload("""{"choices":[{"delta":{"tool_calls":[{"index":1,"id":"b","function":{"name":"daftar_tugas","arguments":"{}"}}]}}]}""")
        parser.feedPayload("""{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"a","function":{"name":"kelas_hari","arguments":"{\"hari\":3}"}}]}}]}""")
        val calls = (parser.finish()[0] as AgentEvent.ToolCalls).calls

        assertEquals(listOf("kelas_hari", "daftar_tugas"), calls.map { it.name })
        assertEquals("""{"hari":3}""", calls[0].argumentsJson)
    }

    @Test fun parserDropsToolCallsWithoutName() {
        val parser = ChatStreamParser()

        parser.feedPayload("""{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"x","function":{"arguments":"{}"}}]}}]}""")
        val events = parser.finish()

        assertTrue(events.none { it is AgentEvent.ToolCalls })
        assertTrue(events[0] is AgentEvent.Done)
    }

    @Test fun parserMapsServerErrorPayload() {
        val parser = ChatStreamParser()

        val events = parser.feedPayload("""{"error":{"message":"model tidak ditemukan","code":404}}""")

        val failure = (events.single() as AgentEvent.Failure).failure
        assertEquals(AgentFailureKind.NotFound, failure.kind)
        assertTrue(failure.message.contains("404"))
    }

    @Test fun parserToleratesAlternativeShapes() {
        val parser = ChatStreamParser()

        // `message` alih-alih `delta`, `content` berupa daftar bagian.
        val events = parser.feedPayload(
            """{"choices":[{"message":{"content":[{"type":"text","text":"Halo"},{"text":" dunia"}]}}]}""",
        )
        // Muatan tanpa `choices` (mis. hanya usage) diabaikan, bukan gagal.
        val ignored = parser.feedPayload("""{"usage":{"total_tokens":10}}""")
        parser.feedPayload("ini bukan json")
        val tail = parser.finish()

        assertEquals("Halo dunia", events.joinToString("") { (it as AgentEvent.Token).text })
        assertEquals(emptyList<AgentEvent>(), ignored)
        assertEquals(1, tail.size)
        assertTrue(tail[0] is AgentEvent.Done)
        assertEquals(1, parser.malformedPayloads)
    }
}
