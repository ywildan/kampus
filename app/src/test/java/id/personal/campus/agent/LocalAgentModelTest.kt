package id.personal.campus.agent

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tes parser + format jawaban [LocalAgentModel].
 *
 * Jam tetap: 2026-11-11 (Rabu) di Asia/Jakarta — "besok" = Kamis 12 Nov,
 * "lusa" = Jumat 13 Nov.
 */
class LocalAgentModelTest {

    private val zone = ZoneId.of("Asia/Jakarta")
    private val clock = Clock.fixed(Instant.parse("2026-11-11T00:00:00Z"), zone)
    private val model = LocalAgentModel(zoneId = zone, clock = clock)
    private val today = LocalDate.of(2026, 11, 11)

    private fun request(vararg messages: AgentMessage, tools: List<AgentToolSpec> = emptyList()) =
        AgentRequest(
            config = AgentConfig(),
            apiKey = "",
            model = LocalAgentModel.MODEL_NAME,
            messages = messages.toList(),
            tools = tools,
        )

    private fun spec(name: String) = AgentToolSpec(name, name, "{}")

    private suspend fun callsFor(text: String, tools: List<AgentToolSpec> = emptyList()): List<AgentToolCall> {
        val events = model.stream(request(AgentMessage(AgentRole.User, text), tools = tools)).toList()
        return events.filterIsInstance<AgentEvent.ToolCalls>().flatMap { it.calls }
    }

    private suspend fun tokensFor(text: String, tools: List<AgentToolSpec> = emptyList()): String {
        val events = model.stream(request(AgentMessage(AgentRole.User, text), tools = tools)).toList()
        return events.filterIsInstance<AgentEvent.Token>().joinToString("") { it.text }
    }

    private fun callArgs(call: AgentToolCall): AgentJsonValue? = AgentJson.parse(call.argumentsJson)

    // -----------------------------------------------------------------------
    // Uraian niat baca
    // -----------------------------------------------------------------------

    @Test fun jadwalBesokMapsToKelasHari() = runTest {
        val calls = callsFor("jadwal besok")
        assertEquals(1, calls.size)
        assertEquals("kelas_hari", calls.single().name)
        assertEquals("Kamis", callArgs(calls.single()).stringOrNull("hari"))
    }

    @Test fun jadwalNamaHariMapsToKelasHari() = runTest {
        val calls = callsFor("jadwal rabu")
        assertEquals("kelas_hari", calls.single().name)
        assertEquals("Rabu", callArgs(calls.single()).stringOrNull("hari"))
    }

    @Test fun aliasesAreNormalized() = runTest {
        val calls = callsFor("jdwl bsk")
        assertEquals("kelas_hari", calls.single().name)
        assertEquals("Kamis", callArgs(calls.single()).stringOrNull("hari"))
    }

    @Test fun besokAdaKuliahApaMapsToKelasHari() = runTest {
        val calls = callsFor("besok ada kuliah apa")
        assertEquals("kelas_hari", calls.single().name)
        assertEquals("Kamis", callArgs(calls.single()).stringOrNull("hari"))
    }

    @Test fun jadwalSemingguMapsToJadwalSeminggu() = runTest {
        val calls = callsFor("jadwal minggu ini")
        assertEquals("jadwal_seminggu", calls.single().name)
    }

    @Test fun kelasBerikutnyaMapsToKelasBerikutnya() = runTest {
        val calls = callsFor("kelas berikutnya")
        assertEquals("kelas_berikutnya", calls.single().name)
    }

    @Test fun adaTugasApaSajaListsUnfinishedTasks() = runTest {
        val calls = callsFor("ada tugas apa saja")
        assertEquals("daftar_tugas", calls.single().name)
        assertEquals(false, callArgs(calls.single()).boolOrNull("sudah_selesai"))
    }

    @Test fun tugasYangSudahSelesaiListsFinishedTasks() = runTest {
        val calls = callsFor("tugas yang sudah selesai")
        assertEquals("daftar_tugas", calls.single().name)
        assertEquals(true, callArgs(calls.single()).boolOrNull("sudah_selesai"))
    }

    @Test fun ruanganMapsToCariRuangan() = runTest {
        val calls = callsFor("ruangan Pengantar Bisnis di mana")
        assertEquals("cari_ruangan", calls.single().name)
        assertEquals("Pengantar Bisnis", callArgs(calls.single()).stringOrNull("matkul"))
    }

    @Test fun jadwalNamaMatkulMapsToCariRuangan() = runTest {
        val calls = callsFor("jadwal Bahasa Indonesia")
        assertEquals("cari_ruangan", calls.single().name)
        assertEquals("Bahasa Indonesia", callArgs(calls.single()).stringOrNull("matkul"))
    }

    @Test fun cariCatatanMapsToCariCatatan() = runTest {
        val calls = callsFor("cari catatan pajak")
        assertEquals("cari_catatan", calls.single().name)
        assertEquals("pajak", callArgs(calls.single()).stringOrNull("kata_kunci"))
    }

    // -----------------------------------------------------------------------
    // Uraian niat tulis
    // -----------------------------------------------------------------------

    @Test fun tolongPrefixIsIgnored() = runTest {
        val calls = callsFor("tolong tambah tugas Latihan 3")
        assertEquals("tambah_tugas", calls.single().name)
        assertEquals("Latihan 3", callArgs(calls.single()).stringOrNull("judul"))
    }

    @Test fun tambahTugasExtractsTitleCourseAndDue() = runTest {
        val calls = callsFor("tambah tugas Latihan 3 matkul PTE tenggat besok")
        assertEquals("tambah_tugas", calls.single().name)
        val args = callArgs(calls.single())
        assertEquals("Latihan 3", args.stringOrNull("judul"))
        assertEquals("PTE", args.stringOrNull("mata_kuliah"))
        assertEquals("2026-11-12", args.stringOrNull("tenggat"))
    }

    @Test fun tambahJadwalExtractsAllRequiredFields() = runTest {
        val calls = callsFor("tambah jadwal Pengantar Bisnis rabu 15.10-17.40 A.3b.4")
        assertEquals("ubah_jadwal", calls.single().name)
        val args = callArgs(calls.single())
        assertEquals("Pengantar Bisnis", args.stringOrNull("mata_kuliah"))
        assertEquals("Rabu", args.stringOrNull("hari"))
        assertEquals("15:10", args.stringOrNull("mulai"))
        assertEquals("17:40", args.stringOrNull("selesai"))
        assertEquals("A.3b.4", args.stringOrNull("ruangan"))
    }

    @Test fun ubahJadwalPartialFieldsAreKept() = runTest {
        val calls = callsFor("ubah jadwal Pengantar Bisnis ruangan A.3b.6")
        assertEquals("ubah_jadwal", calls.single().name)
        val args = callArgs(calls.single())
        assertEquals("Pengantar Bisnis", args.stringOrNull("mata_kuliah"))
        assertEquals("A.3b.6", args.stringOrNull("ruangan"))
        assertEquals("", args.stringOrNull("hari"))
    }

    @Test fun tandaiSelesaiExtractsTitle() = runTest {
        val calls = callsFor("tandai tugas Latihan 3 selesai")
        assertEquals("tandai_tugas_selesai", calls.single().name)
        assertEquals("Latihan 3", callArgs(calls.single()).stringOrNull("judul_tugas"))
    }

    @Test fun tugasSudahSelesaiWithoutYangMarksDone() = runTest {
        val calls = callsFor("tugas Latihan 3 sudah selesai")
        assertEquals("tandai_tugas_selesai", calls.single().name)
        assertEquals("Latihan 3", callArgs(calls.single()).stringOrNull("judul_tugas"))
    }

    @Test fun tambahCatatanExtractsTitleAndBody() = runTest {
        val calls = callsFor("tambah catatan Pajak isi tarif PPN 12 persen")
        assertEquals("tambah_catatan", calls.single().name)
        val args = callArgs(calls.single())
        assertEquals("Pajak", args.stringOrNull("judul"))
        assertEquals("tarif PPN 12 persen", args.stringOrNull("isi"))
    }

    // -----------------------------------------------------------------------
    // Di luar cakupan & allowlist
    // -----------------------------------------------------------------------

    @Test fun outOfScopeQuestionGetsHonestScopeAnswer() = runTest {
        val text = tokensFor("jelaskan pajak penghasilan")
        assertTrue(text.contains("Mode Offline"))
        assertTrue(callsFor("jelaskan pajak penghasilan").isEmpty())
    }

    @Test fun greetingGetsShortReply() = runTest {
        val text = tokensFor("halo")
        assertTrue(text.contains("Halo"))
        assertTrue(callsFor("halo").isEmpty())
    }

    @Test fun disallowedToolIsNotCalled() = runTest {
        val allowed = listOf(spec("kelas_hari"))
        val calls = callsFor("ada tugas apa saja", tools = allowed)
        assertTrue(calls.isEmpty())
        assertTrue(tokensFor("ada tugas apa saja", tools = allowed).contains("Mode Offline"))
    }

    // -----------------------------------------------------------------------
    // Format jawaban (putaran kedua)
    // -----------------------------------------------------------------------

    private fun roundTwo(userText: String, toolName: String, toolJson: String): String {
        val messages = listOf(
            AgentMessage(AgentRole.User, userText),
            AgentMessage(
                AgentRole.Assistant,
                toolCalls = listOf(AgentToolCall("local-1", toolName, "{}")),
            ),
            AgentMessage(AgentRole.Tool, toolJson, toolCallId = "local-1"),
        )
        return kotlinx.coroutines.runBlocking {
            model.stream(request(*messages.toTypedArray())).toList()
                .filterIsInstance<AgentEvent.Token>().joinToString("") { it.text }
        }
    }

    @Test fun formatsKelasHariResult() {
        val json = """{"status":"ok","hari":"Kamis","tanggal":"2026-11-12","jumlah":2,"kelas":[
            {"matkul":"Ketentuan Umum Perpajakan","hari":"Kamis","tanggal":"2026-11-12","mulai":"07:00","selesai":"08:40","ruangan":"A.3b.5","catatan_ruangan":"","kelas":"01"},
            {"matkul":"Bahasa Indonesia","hari":"Kamis","tanggal":"2026-11-12","mulai":"10:40","selesai":"12:20","ruangan":"A.3b.2","catatan_ruangan":"","kelas":"01"}]}"""
        val text = roundTwo("jadwal besok", "kelas_hari", json)
        assertTrue(text.contains("Kamis, 12 November 2026"))
        assertTrue(text.contains("07:00–08:40 Ketentuan Umum Perpajakan · A.3b.5"))
        assertTrue(text.contains("10:40–12:20 Bahasa Indonesia · A.3b.2"))
    }

    @Test fun formatsEmptyKelasHariWithToolMessage() {
        val json = """{"status":"ok","hari":"Kamis","tanggal":"2026-11-12","jumlah":0,"kelas":[],"pesan":""}"""
        val text = roundTwo("jadwal besok", "kelas_hari", json)
        assertTrue(text.contains("Tidak ada kelas pada Kamis"))
    }

    @Test fun tugasBesokFiltersByDueDate() {
        val json = """{"status":"ok","filter":"belum_selesai","jumlah":2,"tugas":[
            {"judul":"Latihan 3","matkul":"PTE","tenggat":"2026-11-12","terlambat":false,"catatan":""},
            {"judul":"Makalah","matkul":"PTE","tenggat":"2026-11-20","terlambat":false,"catatan":""}]}"""
        val text = roundTwo("tugas besok", "daftar_tugas", json)
        assertTrue(text.contains("Latihan 3"))
        assertTrue(text.contains("12 November 2026"))
        assertTrue(!text.contains("Makalah"))
    }

    @Test fun writeAskQuestionIsReturnedAsIs() {
        val json = """{"status":"perlu_pertanyaan","pesan":"Judul tugasnya apa?","petunjuk":"x"}"""
        val text = roundTwo("tambah tugas", "tambah_tugas", json)
        assertEquals("Judul tugasnya apa?", text)
    }

    @Test fun writeSavedResultShowsMessageAndWarnings() {
        val json = """{"status":"tersimpan","pesan":"Tugas \"Latihan 3\" tersimpan.","peringatan":["Tenggat sudah lewat."]}"""
        val text = roundTwo("tambah tugas Latihan 3", "tambah_tugas", json)
        assertTrue(text.contains("Tugas \"Latihan 3\" tersimpan."))
        assertTrue(text.contains("Tenggat sudah lewat."))
    }

    @Test fun parseIntentIsPure() {
        // Parser tidak butuh model: tanggal relatif memakai `today` yang diberikan.
        val intent = parseLocalIntent("jadwal lusa", today)
        assertTrue(intent is LocalIntent.Call)
        assertEquals("kelas_hari", (intent as LocalIntent.Call).tool)
        assertEquals("Jumat", intent.args.stringOrNull("hari"))
    }
}
