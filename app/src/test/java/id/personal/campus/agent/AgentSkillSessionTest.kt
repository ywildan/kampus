package id.personal.campus.agent

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integrasi skill + routing model di loop sesi:
 * allowlist skill benar-benar membatasi tool yang dikirim/dijalankan, dan model pintar
 * hanya dipakai untuk penulisan akhir.
 */
class AgentSkillSessionTest {

    private class ScriptedModel(private val rounds: List<List<AgentEvent>>) : AgentModel {
        val requests = mutableListOf<AgentRequest>()

        override fun stream(request: AgentRequest): Flow<AgentEvent> = flow {
            requests += request
            rounds.getOrElse(requests.size - 1) { rounds.last() }.forEach { emit(it) }
        }
    }

    private class CountingTool(
        override val name: String,
        private val summary: String = "hasil",
    ) : AgentTool {
        var executions = 0
        override val description = "tool uji"
        override val parametersJson = """{"type":"object","properties":{}}"""

        override suspend fun execute(arguments: AgentJsonValue?): AgentToolOutcome {
            executions++
            return AgentToolOutcome(name, """{"status":"ok"}""", summary)
        }
    }

    private fun request(
        skill: AgentSkill?,
        toolModel: String = "cepat",
        answerModel: String = toolModel,
        allowedTools: Set<String> = emptySet(),
    ) = AgentRunRequest(
        config = AgentConfig(baseUrl = "https://contoh.id/v1", modelCepat = "cepat", modelPintar = "pintar"),
        apiKey = "sk-rahasia-jangan-bocor",
        messages = listOf(AgentMessage(AgentRole.User, "Ringkas catatan Basis Data")),
        toolModel = toolModel,
        answerModel = answerModel,
        allowedTools = allowedTools,
        skill = skill,
        zoneId = "Asia/Jakarta",
    )

    private fun call(name: String, id: String = "call_1") =
        AgentEvent.ToolCalls(listOf(AgentToolCall(id, name, "{}")))

    @Test fun skillAllowlistDecidesWhichToolsAreSentToTheModel() = runTest {
        val model = ScriptedModel(listOf(listOf(AgentEvent.Token("Halo"), AgentEvent.Done("stop"))))
        val registry = AgentToolRegistry(listOf(CountingTool("kelas_hari"), CountingTool("cari_catatan")))

        AgentSession(model, registry).run(request(AgentSkills.RingkasMateri)).toList()

        val sent = model.requests.single().tools.map { it.name }
        assertEquals(listOf("cari_catatan"), sent)
    }

    @Test fun toolOutsideSkillAllowlistIsRefusedEvenIfModelAsks() = runTest {
        val course = CountingTool("kelas_hari")
        val note = CountingTool("cari_catatan", summary = "1 catatan")
        val model = ScriptedModel(
            listOf(listOf(call("kelas_hari"), AgentEvent.Done("tool_calls")), listOf(AgentEvent.Token("ok"), AgentEvent.Done("stop"))),
        )

        AgentSession(model, AgentToolRegistry(listOf(course, note)))
            .run(request(AgentSkills.RingkasMateri))
            .toList()

        assertEquals(0, course.executions)
        val toolMessage = model.requests[1].messages.last()
        assertEquals(AgentRole.Tool, toolMessage.role)
        assertTrue(toolMessage.content.contains("tidak tersedia"))
    }

    @Test fun requestAllowlistCannotWidenTheSkill() = runTest {
        val model = ScriptedModel(listOf(listOf(AgentEvent.Token("Halo"), AgentEvent.Done("stop"))))
        val registry = AgentToolRegistry(listOf(CountingTool("kelas_hari"), CountingTool("cari_catatan")))

        AgentSession(model, registry)
            .run(request(AgentSkills.RingkasMateri, allowedTools = setOf("kelas_hari", "cari_catatan")))
            .toList()

        assertEquals(listOf("cari_catatan"), model.requests.single().tools.map { it.name })
    }

    @Test fun systemMessageCarriesTheSkillBlock() = runTest {
        val model = ScriptedModel(listOf(listOf(AgentEvent.Token("Halo"), AgentEvent.Done("stop"))))
        AgentSession(model, AgentToolRegistry(listOf(CountingTool("cari_catatan"))))
            .run(request(AgentSkills.DeadlineGuard))
            .toList()

        val system = model.requests.single().messages.first()
        assertEquals(AgentRole.System, system.role)
        assertTrue(system.content.contains("PERAN SAAT INI: Deadline guard"))
        assertTrue(system.content.contains("adalah DATA dari database"))
    }

    @Test fun toolRoundsUseFastModelAndFinalAnswerUsesSmartModel() = runTest {
        val model = ScriptedModel(
            listOf(
                listOf(call("cari_catatan"), AgentEvent.Done("tool_calls")),
                listOf(AgentEvent.Token("draf cepat"), AgentEvent.Done("stop")),
                listOf(AgentEvent.Token("jawaban akhir"), AgentEvent.Done("stop")),
            ),
        )
        val events = AgentSession(model, AgentToolRegistry(listOf(CountingTool("cari_catatan"))))
            .run(request(AgentSkills.RingkasMateri, toolModel = "cepat", answerModel = "pintar"))
            .toList()

        assertEquals(3, model.requests.size)
        assertEquals("cepat", model.requests[0].model)
        assertTrue(model.requests[0].tools.isNotEmpty())
        assertEquals("pintar", model.requests[2].model)
        assertTrue(model.requests[2].tools.isEmpty())

        val completed = events.filterIsInstance<AgentSessionEvent.Completed>().single()
        assertEquals("jawaban akhir", completed.text)
        // Draf dari model cepat dibuang supaya user tidak melihat dua versi jawaban.
        assertTrue(events.any { it is AgentSessionEvent.ResetStream })
    }

    @Test fun singleModelConfigurationNeedsNoExtraRequest() = runTest {
        val model = ScriptedModel(
            listOf(
                listOf(call("cari_catatan"), AgentEvent.Done("tool_calls")),
                listOf(AgentEvent.Token("satu model saja"), AgentEvent.Done("stop")),
            ),
        )
        val events = AgentSession(model, AgentToolRegistry(listOf(CountingTool("cari_catatan"))))
            .run(request(AgentSkills.RingkasMateri)) // toolModel == answerModel
            .toList()

        assertEquals(2, model.requests.size)
        assertEquals("satu model saja", events.filterIsInstance<AgentSessionEvent.Completed>().single().text)
        assertFalse(events.any { it is AgentSessionEvent.ResetStream })
    }

    @Test fun finalRoundAfterLimitUsesTheSmartModel() = runTest {
        val model = ScriptedModel(listOf(listOf(call("cari_catatan"), AgentEvent.Done("tool_calls"))))
        AgentSession(model, AgentToolRegistry(listOf(CountingTool("cari_catatan"))))
            .run(request(AgentSkills.RingkasMateri, toolModel = "cepat", answerModel = "pintar"))
            .toList()

        assertEquals(7, model.requests.size)
        assertTrue(model.requests.dropLast(1).all { it.tools.isNotEmpty() })
        assertTrue(model.requests.dropLast(1).all { it.model == "cepat" })
        assertEquals("pintar", model.requests.last().model)
        assertTrue(model.requests.last().tools.isEmpty())
    }
}