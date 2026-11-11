package id.personal.campus.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentChatReducerTest {
    private fun sent() = AgentChatReducer.reduce(AgentChatState(), AgentChatEvent.Sent("hi"))

    @Test fun firstTokenCreatesStreamingBubble() {
        val state = AgentChatReducer.reduce(sent(), AgentChatEvent.Token("Halo"))
        val message = state.messages.last()
        assertEquals(AgentChatRole.ASSISTANT, message.role)
        assertEquals("Halo", message.text)
        assertTrue(message.streaming)
    }

    @Test fun completedTextAppearsWithoutTools() {
        val streamed = AgentChatReducer.reduce(sent(), AgentChatEvent.Token("Halo"))
        val state = AgentChatReducer.reduce(
            streamed,
            AgentChatEvent.Completed("Halo! Ada yang bisa dibantu?", "tanpa tool", false),
        )
        val message = state.messages.last()
        assertEquals(AgentChatRole.ASSISTANT, message.role)
        assertEquals("Halo! Ada yang bisa dibantu?", message.text)
        assertFalse(message.streaming)
        assertFalse(state.busy)
    }

    @Test fun completedWithoutTokensStillAddsBubble() {
        val state = AgentChatReducer.reduce(sent(), AgentChatEvent.Completed("Hanya teks", "tanpa tool", false))
        val message = state.messages.last()
        assertEquals(AgentChatRole.ASSISTANT, message.role)
        assertEquals("Hanya teks", message.text)
        assertFalse(state.busy)
    }

    @Test fun emptyCompletedShowsFallback() {
        val state = AgentChatReducer.reduce(sent(), AgentChatEvent.Completed("", "tanpa tool", false))
        assertEquals("Tidak ada jawaban yang bisa saya susun.", state.messages.last().text)
        assertFalse(state.busy)
    }

    @Test fun toolFlowKeepsOneAssistantBubble() {
        var state = AgentChatReducer.reduce(sent(), AgentChatEvent.ToolStarted("kelas_hari"))
        state = AgentChatReducer.reduce(state, AgentChatEvent.Token("Jawaban"))
        state = AgentChatReducer.reduce(state, AgentChatEvent.ToolFinished("kelas_hari", false))
        state = AgentChatReducer.reduce(
            state,
            AgentChatEvent.Completed("Jawaban final", "membaca jadwal", false),
        )
        assertEquals(2, state.messages.size)
        assertEquals("Jawaban final", state.messages.last().text)
        assertFalse(state.messages.last().streaming)
    }
}
