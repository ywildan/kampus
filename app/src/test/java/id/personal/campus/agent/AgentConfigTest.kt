package id.personal.campus.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentConfigTest {
    @Test fun trimsInputAndTrailingSlash() {
        assertEquals("https://openrouter.ai/api/v1", url("https://openrouter.ai/api/v1/"))
        assertEquals("https://openrouter.ai/api/v1", url("  https://openrouter.ai/api/v1  "))
    }

    @Test fun doesNotInventV1Path() {
        assertEquals("https://api.contoh.id", url("https://api.contoh.id"))
        assertEquals("http://10.0.2.2:11434/v1", url("http://10.0.2.2:11434/v1"))
    }

    @Test fun dropsOnlyTheApiSuffixesTheClientAppends() {
        assertEquals("https://api.contoh.id/v1", url("https://api.contoh.id/v1/chat/completions"))
        assertEquals("https://api.contoh.id/v1", url("https://api.contoh.id/v1/models/"))
        assertEquals("https://api.contoh.id/v1/chat", url("https://api.contoh.id/v1/chat"))
    }

    @Test fun rejectsInputThatCannotBeUsed() {
        assertTrue(invalid(""))
        assertTrue(invalid("   "))
        assertTrue(invalid("api.contoh.id/v1"))
        assertTrue(invalid("ftp://contoh.id/v1"))
        assertTrue(invalid("https://con toh.id"))
        assertTrue(invalid("https://api.contoh.id/v1?kunci=1"))
        assertTrue(invalid("https://api.contoh.id/v1/chat/completions?kunci=1"))
        assertTrue(invalid("https://rahasia@api.contoh.id/v1"))
    }

    @Test fun cleartextIsAllowedOnlyForLocalServers() {
        assertEquals("http://192.168.1.10:1234/v1", url("http://192.168.1.10:1234/v1"))
        assertEquals("http://localhost:8000/v1", url("http://localhost:8000/v1"))
        assertEquals("http://pc-rumah.local/v1", url("http://pc-rumah.local/v1"))
        assertTrue(invalid("http://api.contoh.id/v1"))
        assertTrue(invalid("http://8.8.8.8/v1"))
    }

    @Test fun localHostDetectionCoversPrivateRangesOnly() {
        assertTrue(isLocalHost("10.0.2.2"))
        assertTrue(isLocalHost("10.5.5.5"))
        assertTrue(isLocalHost("127.0.0.1"))
        assertTrue(isLocalHost("172.16.5.4"))
        assertTrue(isLocalHost("172.31.5.4"))
        assertTrue(isLocalHost("192.168.0.10"))
        assertTrue(isLocalHost("server-kampus"))
        assertTrue(isLocalHost("pc-rumah.local"))
        assertTrue(!isLocalHost("172.32.5.4"))
        assertTrue(!isLocalHost("192.169.1.1"))
        assertTrue(!isLocalHost("contoh.id"))
    }

    @Test fun urlBuildersAndModelFallbackFollowSavedConfig() {
        val config = AgentConfig(baseUrl = "https://api.contoh.id/v1/", modelCepat = "model-kecil")
        assertEquals("https://api.contoh.id/v1/models", config.modelsUrl)
        assertEquals("https://api.contoh.id/v1/chat/completions", config.chatCompletionsUrl)
        assertEquals("model-kecil", config.effectiveFast)
        assertEquals("model-kecil", config.effectiveSmart)
        assertEquals(null, AgentConfig(baseUrl = "bukan-url").chatCompletionsUrl)
    }

    @Test fun sanitizedTrimAndClampNumericFields() {
        val wild = AgentConfig(
            baseUrl = " https://api.contoh.id/v1 ", modelCepat = " a ", modelPintar = "   ",
            temperature = 9.5, maxTokens = 100_000,
        ).sanitized()
        assertEquals("https://api.contoh.id/v1", wild.baseUrl)
        assertEquals("a", wild.modelCepat)
        assertEquals("", wild.modelPintar)
        assertEquals(2.0, wild.temperature, 0.0001)
        assertEquals(AGENT_MAX_MAX_TOKENS, wild.maxTokens)
        assertEquals(AGENT_MIN_MAX_TOKENS, AgentConfig(maxTokens = 1).sanitized().maxTokens)
        assertEquals(AGENT_DEFAULT_TEMPERATURE, AgentConfig(temperature = Double.NaN).sanitized().temperature, 0.0001)
        assertTrue(wild.hasModel)
    }

    @Test fun everyPresetIsUsableAndUnique() {
        assertEquals(6, AgentPresets.map { it.id }.distinct().size)
        assertEquals(
            AgentPresets.size,
            AgentPresets.count { normalizeBaseUrl(it.baseUrl) is AgentUrlResult.Valid },
        )
        assertTrue(AgentPresets.count { !it.butuhKunci } == 2)
        assertEquals("http://10.0.2.2:11434/v1", agentPreset("ollama")?.baseUrl)
        assertEquals(null, agentPreset("tidak-ada"))
    }

    @Test fun customProvidersRoundTripThroughJson() {
        val providers = listOf(
            CustomProvider("c1", "Server kampus", "https://ai.kampus.id/v1"),
            CustomProvider("c2", "Lokal", "http://10.0.2.2:11434/v1"),
        )
        val decoded = decodeCustomProviders(encodeCustomProviders(providers))
        assertEquals(providers, decoded)
        assertEquals(emptyList<CustomProvider>(), decodeCustomProviders(""))
        assertEquals(emptyList<CustomProvider>(), decodeCustomProviders("bukan-json"))
        assertEquals(providers[0], AgentConfig(customProviders = providers).customProvider("custom:c1"))
        assertEquals(null, AgentConfig(customProviders = providers).customProvider("openrouter"))
    }

    @Test fun sanitizedKeepsCustomProvidersAndDropsBlankOnes() {
        val kept = AgentConfig(
            customProviders = listOf(
                CustomProvider("c1", " Server kampus ", "https://ai.kampus.id/v1 "),
                CustomProvider("c2", "", "https://x.id/v1"),
                CustomProvider("c3", "Tanpa URL", ""),
            ),
        ).sanitized().customProviders
        assertEquals(listOf(CustomProvider("c1", "Server kampus", "https://ai.kampus.id/v1")), kept)
    }

    private fun url(raw: String) = (normalizeBaseUrl(raw) as AgentUrlResult.Valid).baseUrl

    private fun invalid(raw: String) = normalizeBaseUrl(raw) is AgentUrlResult.Invalid
}
