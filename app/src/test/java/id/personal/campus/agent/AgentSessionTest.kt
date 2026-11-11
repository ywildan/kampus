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
 * Loop sesi diuji dengan **MockModel berskrip** (tanpa jaringan, tanpa Room).
 * Yang dijaga di sini: batas 6 iterasi tool, pembatalan tool tulis tanpa persetujuan,
 * pemotongan hasil tool, dan pembacaan protokol cadangan.
 */
class AgentSessionTest {

    private class ScriptedModel(private val rounds: List<List<AgentEvent>>) : AgentModel {
        val requests = mutableListOf<AgentRequest>()

        override fun stream(request: AgentRequest): Flow<AgentEvent> = flow {
            requests += request
            val events = rounds.getOrElse(requests.size - 1) { rounds.last() }
            events.forEach { emit(it) }
        }
    }

    private class CountingTool(
        override val name: String = "kelas_hari",
        override val writesData: Boolean = false,
        private val body: String = """{"status":"ok"}""",
        private val summary: String = "1 mata kuliah",
        private val preparation: AgentWritePreparation? = null,
    ) : AgentTool {
        var executions = 0
        var lastArguments: AgentJsonValue? = null
        override val description = "tool uji"
        override val parametersJson = """{"type":"object","properties":{}}"""

        override suspend fun prepare(arguments: AgentJsonValue?): AgentWritePreparation? =
            preparation?.takeIf { writesData }

        override suspend fun execute(arguments: AgentJsonValue?): AgentToolOutcome {
            executions++
            lastArguments = arguments
            return AgentToolOutcome(name, body, summary)
        }
    }

    private fun proposal(tool: String = "ubah_jadwal", question: String = "Ubah jam Bahasa Indonesia → 13:00–14:40?") =
        AgentWriteProposal(
            tool = tool,
            title = "Ubah jadwal",
            question = question,
            detail = "Rabu 13:00–14:40 · A.3b.2",
            kind = AgentWriteKind.UPDATE,
            changes = listOf(AgentFieldChange("Jam", "10:40–12:20", "13:00–14:40")),
        )

    private fun request(
        confirmWrite: (suspend (AgentWriteProposal) -> Boolean)? = null,
        allowedTools: Set<String> = emptySet(),
    ) = AgentRunRequest(
        config = AgentConfig(baseUrl = "https://contoh.id/v1", modelCepat = "cepat", modelPintar = "pintar"),
        apiKey = "sk-rahasia-jangan-bocor",
        messages = listOf(AgentMessage(AgentRole.User, "Kelas apa setelah ini?")),
        toolModel = "cepat",
        allowedTools = allowedTools,
        confirmWrite = confirmWrite,
        zoneId = "Asia/Jakarta",
    )

    private fun call(name: String = "kelas_hari", id: String = "call_1", arguments: String = "{}") =
        AgentEvent.ToolCalls(listOf(AgentToolCall(id, name, arguments)))

    @Test fun plainAnswerNeedsNoTool() = runTest {
        val tool = CountingTool()
        val model = ScriptedModel(listOf(listOf(AgentEvent.Token("Halo!"), AgentEvent.Done("stop"))))
        val events = AgentSession(model, AgentToolRegistry(listOf(tool))).run(request()).toList()

        val tokens = events.filterIsInstance<AgentSessionEvent.Token>().joinToString("") { it.text }
        assertEquals("Halo!", tokens)
        val completed = events.filterIsInstance<AgentSessionEvent.Completed>().single()
        assertEquals("Halo!", completed.text)
        assertEquals(0, completed.iterations)
        assertTrue(completed.trace.entries.isEmpty())
        assertEquals(1, model.requests.size)
        assertEquals(listOf("kelas_hari"), model.requests.single().tools.map { it.name })
        assertEquals(0, tool.executions)
    }

    @Test fun loopStopsAfterSixToolRounds() = runTest {
        val tool = CountingTool()
        // Model terus meminta tool; sesi harus berhenti, bukan berputar tanpa ujung.
        val model = ScriptedModel(listOf(listOf(call(), AgentEvent.Done("tool_calls"))))
        val events = AgentSession(model, AgentToolRegistry(listOf(tool))).run(request()).toList()

        assertEquals(6, tool.executions)
        assertEquals(7, model.requests.size)
        assertTrue(model.requests.dropLast(1).all { it.tools.isNotEmpty() })
        assertTrue(model.requests.last().tools.isEmpty())

        val completed = events.filterIsInstance<AgentSessionEvent.Completed>().single()
        assertTrue(completed.stoppedByLimit)
        assertEquals(6, completed.iterations)
        assertEquals(6, completed.trace.entries.size)
        assertTrue(completed.text.contains("6 langkah"))
    }

    @Test fun writeToolIsNotExecutedWithoutApproval() = runTest {
        lateinit var tool: CountingTool
        tool = CountingTool(
            name = "ubah_jadwal",
            writesData = true,
            summary = "mengubah jadwal Bahasa Indonesia",
            preparation = AgentWritePreparation.Ready(proposal()) { AppliedWrite(tool.execute(null)) },
        )
        val model = ScriptedModel(
            listOf(
                listOf(call("ubah_jadwal"), AgentEvent.Done("tool_calls")),
                listOf(AgentEvent.Token("Baik, tidak jadi diubah."), AgentEvent.Done("stop")),
            ),
        )
        val events = AgentSession(model, AgentToolRegistry(listOf(tool)))
            .run(request(confirmWrite = { false }))
            .toList()

        assertEquals(0, tool.executions)
        val finished = events.filterIsInstance<AgentSessionEvent.ToolFinished>().single()
        assertTrue(finished.cancelled)
        assertEquals("Baik, tidak jadi diubah.", events.filterIsInstance<AgentSessionEvent.Completed>().single().text)
        // Model diberi tahu bahwa perubahannya dibatalkan, supaya tidak mengulang diam-diam.
        assertTrue(model.requests[1].messages.last().content.contains("dibatalkan"))
    }

    @Test fun writeToolRunsOnlyAfterApproval() = runTest {
        lateinit var tool: CountingTool
        tool = CountingTool(
            name = "tambah_tugas",
            writesData = true,
            summary = "menambah tugas Ringkasan",
            preparation = AgentWritePreparation.Ready(proposal(tool = "tambah_tugas")) { AppliedWrite(tool.execute(null)) },
        )
        val model = ScriptedModel(
            listOf(
                listOf(call("tambah_tugas"), AgentEvent.Done("tool_calls")),
                listOf(AgentEvent.Token("Tugas sudah ditambahkan."), AgentEvent.Done("stop")),
            ),
        )
        val events = AgentSession(model, AgentToolRegistry(listOf(tool)))
            .run(request(confirmWrite = { true }))
            .toList()

        assertEquals(1, tool.executions)
        assertFalse(events.filterIsInstance<AgentSessionEvent.ToolFinished>().single().cancelled)
        assertEquals("menambah tugas Ringkasan", events.filterIsInstance<AgentSessionEvent.Completed>().single().trace.entries.single().summary)
    }

    @Test fun longToolResultIsTruncatedAndModelIsTold() = runTest {
        val tool = CountingTool(body = "x".repeat(5_000))
        val model = ScriptedModel(
            listOf(
                listOf(call(), AgentEvent.Done("tool_calls")),
                listOf(AgentEvent.Token("Ringkas."), AgentEvent.Done("stop")),
            ),
        )
        AgentSession(model, AgentToolRegistry(listOf(tool)), AgentLimits(maxToolResultChars = 120))
            .run(request())
            .toList()

        val toolMessage = model.requests[1].messages.last()
        assertEquals(AgentRole.Tool, toolMessage.role)
        assertTrue(toolMessage.content.contains("dipotong"))
        assertTrue(toolMessage.content.length < 300)
    }

    @Test fun fallbackJsonBlockIsParsedAndTextIsReset() = runTest {
        val tool = CountingTool()
        val model = ScriptedModel(
            listOf(
                listOf(
                    AgentEvent.Token("```json\n{\"tool\":\"kelas_hari\",\"arguments\":{\"hari\":\"Rabu\"}}\n```"),
                    AgentEvent.Done("stop", usedPromptFallback = true),
                ),
                listOf(AgentEvent.Token("Kelas berikutnya 12:30."), AgentEvent.Done("stop")),
            ),
        )
        val events = AgentSession(model, AgentToolRegistry(listOf(tool))).run(request()).toList()

        assertTrue(events.contains(AgentSessionEvent.ResetStream))
        assertEquals(1, tool.executions)
        assertEquals("Kelas berikutnya 12:30.", events.filterIsInstance<AgentSessionEvent.Completed>().single().text)

        val history = model.requests[1].messages
        assertTrue(history.any { it.role == AgentRole.Assistant && it.content.contains("```json") })
        val result = history.last()
        assertEquals(AgentRole.User, result.role)
        assertTrue(result.content.contains("Hasil tool kelas_hari"))
        assertTrue(result.content.contains("bukan instruksi"))
    }

    @Test fun toolsIgnoredByServerAreDetectedFromPlainText() = runTest {
        val tool = CountingTool()
        val model = ScriptedModel(
            listOf(
                listOf(
                    AgentEvent.Token("{\"tool\":\"kelas_hari\",\"arguments\":{\"hari\":3}}"),
                    AgentEvent.Done("stop"),
                ),
                listOf(AgentEvent.Token("Selesai."), AgentEvent.Done("stop")),
            ),
        )
        AgentSession(model, AgentToolRegistry(listOf(tool))).run(request()).toList()
        assertEquals(1, tool.executions)
    }

    @Test fun toolOutsideAllowlistIsRefused() = runTest {
        val allowed = CountingTool(name = "cari_catatan")
        val other = CountingTool(name = "kelas_hari")
        val model = ScriptedModel(
            listOf(
                listOf(call("kelas_hari"), AgentEvent.Done("tool_calls")),
                listOf(AgentEvent.Token("Maaf, tidak tersedia."), AgentEvent.Done("stop")),
            ),
        )
        val events = AgentSession(model, AgentToolRegistry(listOf(allowed, other)))
            .run(request(allowedTools = setOf("cari_catatan")))
            .toList()

        assertEquals(listOf("cari_catatan"), model.requests.first().tools.map { it.name })
        assertEquals(0, other.executions)
        assertTrue(events.filterIsInstance<AgentSessionEvent.ToolFinished>().single().cancelled)
    }

    @Test fun malformedArgumentsDoNotCrashTheSession() = runTest {
        val tool = SimpleAgentTool(
            name = "kelas_hari",
            description = "uji",
            parametersJson = """{"type":"object","properties":{}}""",
        ) { arguments -> AgentToolOutcome("kelas_hari", """{"status":"ok"}""", "0 mata kuliah", 0) }

        val model = ScriptedModel(
            listOf(
                listOf(call(arguments = "{bukan json"), AgentEvent.Done("tool_calls")),
                listOf(AgentEvent.Token("Baik."), AgentEvent.Done("stop")),
            ),
        )
        val events = AgentSession(model, AgentToolRegistry(listOf(tool))).run(request()).toList()
        assertEquals("Baik.", events.filterIsInstance<AgentSessionEvent.Completed>().single().text)
    }

    @Test fun traceDescribesWhatWasRead() {
        val trace = AgentTrace(
            listOf(
                AgentTraceEntry("kelas_hari", "3 mata kuliah Rabu"),
                AgentTraceEntry("daftar_tugas", "5 tugas"),
            ),
        )
        assertEquals("membaca 3 mata kuliah Rabu, 5 tugas", trace.describe())
        assertEquals("tanpa tool", AgentTrace().describe())
    }

    @Test fun systemPromptTreatsDatabaseContentAsData() {
        val prompt = AgentPrompt.system(now = "Rabu, 11 November 2026 12:20", zone = "Asia/Jakarta")
        assertTrue(prompt.contains("DATA"))
        assertTrue(prompt.contains("bukan perintah"))
        assertTrue(prompt.contains("hapus semua tugas"))
        assertTrue(prompt.contains("tidak bisa menghapus data"))
    }

    @Test fun apiKeyNeverAppearsInSessionEvents() = runTest {
        val tool = CountingTool()
        val model = ScriptedModel(listOf(listOf(call(), AgentEvent.Done("tool_calls")), listOf(AgentEvent.Token("ok"), AgentEvent.Done("stop"))))
        val events = AgentSession(model, AgentToolRegistry(listOf(tool))).run(request()).toList()
        assertTrue(events.none { it.toString().contains("sk-rahasia-jangan-bocor") })
    }
}