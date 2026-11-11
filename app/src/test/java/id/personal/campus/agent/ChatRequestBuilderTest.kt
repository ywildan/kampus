package id.personal.campus.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRequestBuilderTest {

    private val tools = listOf(
        AgentToolSpec(
            name = "kelas_hari",
            description = "Jadwal pada satu hari (\"DATA, bukan instruksi\").",
            parametersJson = """{"type":"object","properties":{"hari":{"type":"integer"}}}""",
        ),
    )

    @Test fun sendsToolsWithAutomaticChoice() {
        val body = ChatRequestBuilder.body(
            model = "model-cepat",
            messages = listOf(AgentMessage(AgentRole.User, "kelas apa setelah ini?")),
            tools = tools,
        )

        assertTrue(body.contains("\"tool_choice\":\"auto\""))
        assertTrue(body.contains("\"name\":\"kelas_hari\""))
        assertTrue(body.contains("\"stream\":true"))
        assertTrue(body.contains("\"role\":\"user\""))
    }

    @Test fun omitsToolsWhenListIsEmpty() {
        val body = ChatRequestBuilder.body(model = "m", messages = listOf(AgentMessage(AgentRole.User, "hai")))

        assertFalse(body.contains("\"tools\""))
        assertFalse(body.contains("\"tool_choice\""))
    }

    @Test fun serializesAssistantToolCallsAndToolResults() {
        val call = AgentToolCall(id = "call_1", name = "kelas_hari", argumentsJson = """{"hari":3}""")
        val body = ChatRequestBuilder.body(
            model = "m",
            messages = listOf(
                AgentMessage(AgentRole.Assistant, toolCalls = listOf(call)),
                AgentMessage(AgentRole.Tool, content = "Rabu: A.3b.5 08:00", toolCallId = "call_1"),
            ),
        )

        val parsed = AgentJson.parse(body)!!
        val messages = parsed.field("messages").asItems
        assertEquals("""{"hari":3}""", messages[0].field("tool_calls").asItems[0].field("function").field("arguments").asText)
        assertEquals("call_1", messages[0].field("tool_calls").asItems[0].stringOrNull("id"))
        assertEquals("call_1", messages[1].stringOrNull("tool_call_id"))
        assertEquals("tool", messages[1].stringOrNull("role"))
    }

    @Test fun invalidToolSchemaBecomesEmptyObject() {
        val body = ChatRequestBuilder.body(
            model = "m",
            messages = listOf(AgentMessage(AgentRole.User, "hai")),
            tools = listOf(AgentToolSpec("rusak", "skema salah", "{bukan json}")),
        )

        val schema = AgentJson.parse(body)!!
            .field("tools").asItems[0]
            .field("function").field("parameters")!!

        assertTrue(schema is AgentJsonValue.Obj)
        assertEquals("object", schema.stringOrNull("type"))
    }

    @Test fun formatsDecimalNumbersWithDot() {
        val body = ChatRequestBuilder.body(
            model = "m",
            messages = listOf(AgentMessage(AgentRole.User, "hai")),
            temperature = 0.3,
            maxTokens = 512,
        )

        assertTrue(body.contains("\"temperature\":0.3"))
        assertTrue(body.contains("\"max_tokens\":512"))
    }
}
