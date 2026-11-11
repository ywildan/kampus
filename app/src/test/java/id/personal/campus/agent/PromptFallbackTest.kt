package id.personal.campus.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptFallbackTest {

    private val allowed = setOf("kelas_hari", "daftar_tugas", "ubah_jadwal")

    @Test fun readsFencedJsonBlockWithLanguageTag() {
        val answer = """
            Baik, saya periksa dulu.
            ```json
            {"tool":"kelas_hari","arguments":{"hari":"Rabu"}}
            ```
            Setelah itu saya jawab.
        """.trimIndent()

        val result = PromptFallback.parse(answer, allowed)

        assertTrue(result.warnings.isEmpty())
        assertEquals(1, result.calls.size)
        assertEquals("kelas_hari", result.calls[0].name)
        assertEquals("""{"hari":"Rabu"}""", result.calls[0].argumentsJson)
    }

    @Test fun skipsNonJsonFenceBeforeTheJsonOne() {
        val answer = "```\nhalo biasa\n```\n```json\n{\"tool\":\"daftar_tugas\",\"arguments\":{}}\n```"

        val result = PromptFallback.parse(answer, allowed)

        assertEquals("daftar_tugas", result.calls.single().name)
    }

    @Test fun acceptsToolCallsArrayAndJsonStringArguments() {
        val answer =
            """{"tool_calls":[{"name":"ubah_jadwal","arguments":"{\"matkul\":\"Basis Data\",\"aktif\":true}"}]}"""

        val call = PromptFallback.parse(answer, allowed).calls.single()

        assertEquals("ubah_jadwal", call.name)
        assertEquals("""{"matkul":"Basis Data","aktif":true}""", call.argumentsJson)
    }

    @Test fun handlesBracesInsideStrings() {
        val answer = """{"tool":"kelas_hari","arguments":{"catatan":"kurung } dan { di dalam"}}"""

        val call = PromptFallback.parse(answer, allowed).calls.single()

        assertEquals("""{"catatan":"kurung } dan { di dalam"}""", call.argumentsJson)
    }

    @Test fun ignoresToolsOutsideAllowlist() {
        val answer = """{"tool":"hapus_semua_tugas","arguments":{}}"""

        val result = PromptFallback.parse(answer, allowed)

        assertTrue(result.calls.isEmpty())
        assertTrue(result.warnings.any { it.contains("hapus_semua_tugas") })
    }

    @Test fun reportsWhenModelWroteNoJson() {
        val result = PromptFallback.parse("Tidak ada kelas setelah ini.", allowed)

        assertTrue(result.calls.isEmpty())
        assertTrue(result.warnings.single().contains("blok JSON"))
    }

    @Test fun reportsWhenJsonIsNotReadable() {
        val result = PromptFallback.parse("```json\n{tool: kelas_hari}\n```", allowed)

        assertTrue(result.calls.isEmpty())
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test fun reportsWhenJsonHasNoToolName() {
        val result = PromptFallback.parse("""{"arguments":{"hari":"Rabu"}}""", allowed)

        assertTrue(result.calls.isEmpty())
        assertTrue(result.warnings.single().contains("nama tool"))
    }

    @Test fun missingArgumentsBecomeEmptyObject() {
        val call = PromptFallback.parse("""{"tool":"kelas_berikutnya"}""", setOf("kelas_berikutnya")).calls.single()

        assertEquals("{}", call.argumentsJson)
    }

    @Test fun extractJsonBlockReturnsNullForPlainText() {
        assertNull(PromptFallback.extractJsonBlock("Baik, ini jawabannya."))
        assertNull(PromptFallback.extractJsonBlock(""))
    }
}
