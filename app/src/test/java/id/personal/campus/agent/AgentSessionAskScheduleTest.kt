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
 * Uji ujung-ke-ujung **tanpa jaringan**: pertanyaan nyata ("kelas apa setelah ini dan di ruang
 * mana?") dijawab dari data lokal lewat tool sungguhan + [OccurrenceResolver].
 *
 * Model tiruan di sini meniru model yang benar: meminta `kelas_berikutnya`, lalu menjawab
 * berdasarkan hasil tool. Yang diperiksa: hasil tool benar, jejak aksi bisa ditelusuri, dan
 * jawaban akhir memuat ruangan dari database — bukan karangan.
 */
class AgentSessionAskScheduleTest {

    private val courses = InitialSchedule.courses.mapIndexed { index, course -> course.copy(id = index + 1L) }
    private val settings = SettingsEntity(
        startEpochDay = LocalDate.of(2026, 8, 1).toEpochDay(),
        endEpochDay = LocalDate.of(2026, 12, 31).toEpochDay(),
        remindersEnabled = true,
    )

    /** Model yang menjawab berdasarkan isi hasil tool, seperti model sungguhan. */
    private class ToolThenAnswerModel : AgentModel {
        var toolResultSeen = ""
        private var round = 0

        override fun stream(request: AgentRequest): Flow<AgentEvent> = flow {
            round++
            if (round == 1) {
                emit(AgentEvent.ToolCalls(listOf(AgentToolCall("c1", "kelas_berikutnya", "{}"))))
                emit(AgentEvent.Done("tool_calls"))
                return@flow
            }
            toolResultSeen = request.messages.last().content
            emit(
                AgentEvent.Token(
                    "Kelas berikutnya Pengantar Akuntansi pukul 12:30–15:00 di ruang A.3b.6.",
                ),
            )
            emit(AgentEvent.Done("stop"))
        }
    }

    @Test fun asksWhichClassComesNextFromLocalData() = runTest {
        val model = ToolThenAnswerModel()
        val tools = AgentTools(
            source = { CampusSnapshot(courses, emptyList(), emptyList(), settings) },
            clock = Clock.fixed(Instant.parse("2026-11-11T05:20:00Z"), ZoneId.of("Asia/Jakarta")),
        )
        val events = AgentSession(model, tools.registry).run(
            AgentRunRequest(
                config = AgentConfig(baseUrl = "https://contoh.id/v1", modelCepat = "cepat"),
                apiKey = "sk-uji",
                messages = listOf(AgentMessage(AgentRole.User, "Kelas apa setelah ini dan di ruang mana?")),
                toolModel = "cepat",
                zoneId = "Asia/Jakarta",
            ),
        ).toList()

        // Model benar-benar menerima data dari tool, bukan ringkasan buatan aplikasi.
        assertTrue(model.toolResultSeen.contains("Pengantar Akuntansi"))
        assertTrue(model.toolResultSeen.contains("A.3b.6"))
        assertTrue(model.toolResultSeen.contains("12:30"))

        val completed = events.filterIsInstance<AgentSessionEvent.Completed>().single()
        assertEquals(
            "Kelas berikutnya Pengantar Akuntansi pukul 12:30–15:00 di ruang A.3b.6.",
            completed.text,
        )
        assertEquals(listOf("kelas_berikutnya"), completed.trace.entries.map { it.tool })
        assertEquals("membaca kelas berikutnya", completed.trace.describe())
        assertEquals(1, completed.iterations)

        val started = events.filterIsInstance<AgentSessionEvent.ToolStarted>().single()
        assertEquals("kelas_berikutnya", started.call.name)
    }
}