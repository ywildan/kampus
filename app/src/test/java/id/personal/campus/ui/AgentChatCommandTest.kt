package id.personal.campus.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentChatCommandTest {
    @Test fun autoApprovalWithoutArgumentToggles() {
        assertEquals(AgentChatCommand.AutoApproval(null), parseAgentCommand("/autoapproval"))
    }

    @Test fun onAndOffAreExplicit() {
        assertEquals(AgentChatCommand.AutoApproval(true), parseAgentCommand("/autoapproval on"))
        assertEquals(AgentChatCommand.AutoApproval(false), parseAgentCommand("/autoapproval off"))
    }

    @Test fun caseAndSpacingAreTolerated() {
        assertEquals(AgentChatCommand.AutoApproval(null), parseAgentCommand("  /AUTOAPPROVAL  "))
        assertEquals(AgentChatCommand.AutoApproval(false), parseAgentCommand("/autoapproval OFF"))
    }

    @Test fun normalTextAndUnknownSlashStayMessages() {
        assertNull(parseAgentCommand("buat tugas"))
        assertNull(parseAgentCommand("/autoapprovalx"))
        assertNull(parseAgentCommand("/autoapproval nanti"))
    }

    @Test fun reducerStoresTheToggle() {
        assertFalse(AgentChatState().autoApproveWrites)
        val state = AgentChatReducer.reduce(AgentChatState(), AgentChatEvent.AutoApproveChanged(true))
        assertTrue(state.autoApproveWrites)
    }
}
