package id.personal.campus.ui

import id.personal.campus.agent.AgentConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentChatModelModeTest {
    private val config = AgentConfig(modelCepat = "fast-model", modelPintar = "smart-model")

    @Test fun hematUsesFastForToolsAndSmartForAnswers() {
        val selection = AgentChatModelMode.HEMAT.resolveModels(config)
        assertEquals("fast-model", selection.toolModel)
        assertEquals("smart-model", selection.answerModel)
    }

    @Test fun pintarUsesSmartForEveryStep() {
        val selection = AgentChatModelMode.PINTAR.resolveModels(config)
        assertEquals("smart-model", selection.toolModel)
        assertEquals("smart-model", selection.answerModel)
    }

    @Test fun cepatUsesFastForEveryStep() {
        val selection = AgentChatModelMode.CEPAT.resolveModels(config)
        assertEquals("fast-model", selection.toolModel)
        assertEquals("fast-model", selection.answerModel)
    }

    @Test fun blankSmartModelFallsBackToFast() {
        val selection = AgentChatModelMode.HEMAT.resolveModels(AgentConfig(modelCepat = "fast-model"))
        assertEquals("fast-model", selection.toolModel)
        assertEquals("fast-model", selection.answerModel)
    }

    @Test fun blankFastModelFallsBackToSmart() {
        val selection = AgentChatModelMode.CEPAT.resolveModels(AgentConfig(modelPintar = "smart-model"))
        assertEquals("smart-model", selection.toolModel)
        assertEquals("smart-model", selection.answerModel)
    }

    @Test fun defaultModeIsHematAndReducerStoresTheChoice() {
        assertEquals(AgentChatModelMode.HEMAT, AgentChatState().modelMode)

        val chosen = AgentChatReducer.reduce(
            AgentChatState(),
            AgentChatEvent.ModelModeChosen(AgentChatModelMode.PINTAR),
        )
        assertEquals(AgentChatModelMode.PINTAR, chosen.modelMode)
    }
}
