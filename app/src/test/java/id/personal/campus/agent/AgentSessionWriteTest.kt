package id.personal.campus.agent

import id.personal.campus.data.CampusSnapshot
import id.personal.campus.data.InitialSchedule
import id.personal.campus.data.SettingsEntity
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integrasi loop sesi + tool tulis sungguhan (penyimpanan tiruan).
 *
 * Yang dijaga: tidak ada penyimpanan sebelum Setuju, agen **bertanya** saat argumen kurang,
 * dan jejak aksi memisahkan bacaan dari perubahan.
 */
class AgentSessionWriteTest {

    private class ScriptedModel(private val rounds: List<List<AgentEvent>>) : AgentModel {
        val requests = mutableListOf<AgentRequest>()

        override fun stream(request: AgentRequest): Flow<AgentEvent> = flow {
            requests += request
            rounds.getOrElse(requests.size - 1) { rounds.last() }.forEach { emit(it) }
        }
    }

    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-11T02:00:00Z"), ZoneId.of("UTC"))

    private fun data(): CampusSnapshot = CampusSnapshot(
        courses = InitialSchedule.courses.mapIndexed { index, course -> course.copy(id = index + 1L) },
        settings = SettingsEntity(
            startEpochDay = LocalDate.of(2026, 8, 1).toEpochDay(),
            endEpochDay = LocalDate.of(2027, 1, 31).toEpochDay(),
        ),
    )

    private fun agentRequest(confirm: (suspend (AgentWriteProposal) -> Boolean)?) = AgentRunRequest(
        config = AgentConfig(baseUrl = "https://contoh.id/v1", modelCepat = "cepat", modelPintar = "pintar"),
        apiKey = "sk-rahasia-jangan-bocor",
        messages = listOf(AgentMessage(AgentRole.User, "Tolong bantu.")),
        toolModel = "cepat",
        confirmWrite = confirm,
        zoneId = "Asia/Jakarta",
    )

    private fun toolCall(name: String, json: String) =
        AgentEvent.ToolCalls(listOf(AgentToolCall("call_1", name, json)))

    @Test fun agentAsksInsteadOfWritingWhenArgumentsAreIncomplete() = runTest {
        val store = FakeAgentWriteStore(data())
        val writer = AgentWriteTools(store, clock)
        val proposals = mutableListOf<AgentWriteProposal>()
        val model = ScriptedModel(
            listOf(
                listOf(toolCall("tambah_tugas", """{"mata_kuliah":"Pengantar Akuntansi"}"""), AgentEvent.Done("tool_calls")),
                listOf(AgentEvent.Token("Judul tugasnya apa?"), AgentEvent.Done("stop")),
            ),
        )

        val events = AgentSession(model, writer.registry)
            .run(agentRequest { proposal -> proposals += proposal; true })
            .toList()

        assertTrue("kartu konfirmasi tidak boleh muncul", proposals.isEmpty())
        assertEquals(0, store.taskSaves)
        assertTrue(events.none { it is AgentSessionEvent.WriteProposed })

        // Model menerima permintaan bertanya, bukan nilai default karangan.
        val toolMessage = model.requests[1].messages.last()
        assertEquals(AgentRole.Tool, toolMessage.role)
        assertTrue(toolMessage.content.contains("perlu_pertanyaan"))
        assertTrue(toolMessage.content.contains("Judul tugasnya apa?"))
        assertEquals("Judul tugasnya apa?", events.filterIsInstance<AgentSessionEvent.Completed>().single().text)
    }

    @Test fun proposalAppearsBeforeAnySaveAndIsAppliedAfterApproval() = runTest {
        val store = FakeAgentWriteStore(data())
        val writer = AgentWriteTools(store, clock)
        val proposals = mutableListOf<AgentWriteProposal>()
        val model = ScriptedModel(
            listOf(
                listOf(toolCall("tambah_tugas", """{"judul":"Ringkasan akuntansi"}"""), AgentEvent.Done("tool_calls")),
                listOf(AgentEvent.Token("Tugas sudah disimpan."), AgentEvent.Done("stop")),
            ),
        )

        val events = AgentSession(model, writer.registry)
            .run(
                agentRequest { proposal ->
                    // Saat kartu tampil, belum ada yang tersimpan.
                    assertEquals(0, store.taskSaves)
                    proposals += proposal
                    true
                },
            )
            .toList()

        val shown = events.filterIsInstance<AgentSessionEvent.WriteProposed>().single().proposal
        assertEquals("Tambah tugas \"Ringkasan akuntansi\"?", shown.question)
        assertEquals(listOf(shown), proposals)
        assertEquals(1, store.taskSaves)
        assertTrue(store.data.tasks.single().title == "Ringkasan akuntansi")
        assertEquals(1, events.filterIsInstance<AgentSessionEvent.WriteApplied>().size)

        // Undo tersedia untuk aksi ini.
        val undo = checkNotNull(writer.undoLast())
        assertTrue(undo.detail.contains("Ringkasan akuntansi"))
        assertTrue(store.data.tasks.isEmpty())
    }

    @Test fun deniedProposalLeavesDataUntouched() = runTest {
        val store = FakeAgentWriteStore(data())
        val writer = AgentWriteTools(store, clock)
        val model = ScriptedModel(
            listOf(
                listOf(toolCall("ubah_jadwal", """{"mata_kuliah":"Bahasa Indonesia","ruangan":"A.3b.9"}"""), AgentEvent.Done("tool_calls")),
                listOf(AgentEvent.Token("Baik, tidak jadi diubah."), AgentEvent.Done("stop")),
            ),
        )

        val events = AgentSession(model, writer.registry)
            .run(agentRequest { false })
            .toList()

        assertEquals(0, store.courseSaves)
        assertEquals("A.3b.2", store.data.courses.single { it.name == "Bahasa Indonesia" }.room)
        assertTrue(events.filterIsInstance<AgentSessionEvent.ToolFinished>().single().cancelled)
        assertTrue(events.none { it is AgentSessionEvent.WriteApplied })
        assertTrue(model.requests[1].messages.last().content.contains("dibatalkan"))
    }

    @Test fun writeWithoutConfirmationCallbackIsTreatedAsDenied() = runTest {
        val store = FakeAgentWriteStore(data())
        val writer = AgentWriteTools(store, clock)
        val model = ScriptedModel(
            listOf(
                listOf(toolCall("tambah_catatan", """{"judul":"Draf"}"""), AgentEvent.Done("tool_calls")),
                listOf(AgentEvent.Token("Tidak disimpan."), AgentEvent.Done("stop")),
            ),
        )

        AgentSession(model, writer.registry).run(agentRequest(confirm = null)).toList()
        assertEquals(0, store.noteSaves)
    }

    @Test fun alreadyCompletedTaskNeedsNoConfirmation() = runTest {
        val store = FakeAgentWriteStore(
            data().copy(
                tasks = listOf(
                    id.personal.campus.data.TaskEntity(id = 1, title = "Laporan Pajak", completed = true),
                ),
            ),
        )
        val writer = AgentWriteTools(store, clock)
        val proposals = mutableListOf<AgentWriteProposal>()
        val model = ScriptedModel(
            listOf(
                listOf(toolCall("tandai_tugas_selesai", """{"judul_tugas":"Laporan Pajak"}"""), AgentEvent.Done("tool_calls")),
                listOf(AgentEvent.Token("Tugas itu sudah selesai."), AgentEvent.Done("stop")),
            ),
        )

        AgentSession(model, writer.registry)
            .run(agentRequest { proposal -> proposals += proposal; true })
            .toList()

        assertTrue(proposals.isEmpty())
        assertEquals(0, store.taskSaves)
    }

    @Test fun traceSeparatesReadsFromWrites() = runTest {
        val store = FakeAgentWriteStore(data())
        val reader = AgentTools(AgentSnapshotSource { store.snapshot() }, clock)
        val writer = AgentWriteTools(store, clock)
        val model = ScriptedModel(
            listOf(
                listOf(
                    AgentEvent.ToolCalls(
                        listOf(
                            AgentToolCall("call_1", "kelas_hari", """{"hari":"Rabu"}"""),
                            AgentToolCall("call_2", "tambah_tugas", """{"judul":"Ringkasan akuntansi"}"""),
                        ),
                    ),
                    AgentEvent.Done("tool_calls"),
                ),
                listOf(AgentEvent.Token("Selesai."), AgentEvent.Done("stop")),
            ),
        )

        val events = AgentSession(model, AgentToolRegistry(reader.tools + writer.tools))
            .run(agentRequest { true })
            .toList()

        val trace = events.filterIsInstance<AgentSessionEvent.Completed>().single().trace
        assertEquals("membaca 3 mata kuliah Rabu, menambah tugas Ringkasan akuntansi", trace.describe())
        assertEquals(AgentTraceKind.READ, trace.entries.first().kind)
        assertEquals(AgentTraceKind.WRITE, trace.entries.last().kind)
        assertEquals(1, store.taskSaves)
    }

    @Test fun noApiKeyLeaksIntoProposalsOrEvents() = runTest {
        val store = FakeAgentWriteStore(data())
        val writer = AgentWriteTools(store, clock)
        val model = ScriptedModel(
            listOf(
                listOf(toolCall("tambah_tugas", """{"judul":"Ringkasan"}"""), AgentEvent.Done("tool_calls")),
                listOf(AgentEvent.Token("ok"), AgentEvent.Done("stop")),
            ),
        )
        val events = AgentSession(model, writer.registry).run(agentRequest { true }).toList()
        assertTrue(events.none { it.toString().contains("sk-rahasia-jangan-bocor") })
    }
}