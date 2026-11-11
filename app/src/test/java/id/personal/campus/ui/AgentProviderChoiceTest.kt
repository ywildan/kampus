package id.personal.campus.ui

import id.personal.campus.agent.CustomProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentProviderChoiceTest {
    @Test fun savedPresetUrlSelectsThePreset() {
        assertEquals("openrouter", providerChoiceFor("https://openrouter.ai/api/v1/", emptyList()))
    }

    @Test fun savedCustomUrlSelectsTheCustomProvider() {
        val providers = listOf(CustomProvider("c1", "Kampus", "https://ai.kampus.id/v1"))
        assertEquals("custom:c1", providerChoiceFor("https://ai.kampus.id/v1", providers))
    }

    @Test fun unknownOrBlankUrlHasNoChoice() {
        assertEquals("", providerChoiceFor("https://contoh.id/v1", emptyList()))
        assertEquals("", providerChoiceFor("", emptyList()))
    }
}
