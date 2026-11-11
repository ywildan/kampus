package id.personal.campus.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentJsonTest {

    @Test fun parsesObjectAndReadsTypedFields() {
        val root = AgentJson.parse("""{"a":1,"b":"x","c":true,"d":null,"e":{"f":2},"g":[1,2]}""")

        assertEquals(1, root.intOrNull("a"))
        assertEquals("x", root.stringOrNull("b"))
        assertEquals(true, root.boolOrNull("c"))
        assertTrue(root.field("d")!!.isNull)
        assertEquals(2, root.field("e").intOrNull("f"))
        assertEquals(2, root.field("g").asItems.size)
    }

    @Test fun parsesStringEscapesAndUnicode() {
        val root = AgentJson.parse("""{"s":"baris\nbaru \"kutip\" \\ tab\t \u00e9 \ud83d\ude00"}""")
        val value = root.stringOrNull("s").orEmpty()

        assertTrue(value.contains("\n"))
        assertTrue(value.contains("\"kutip\""))
        assertTrue(value.contains("\\"))
        assertTrue(value.contains("\t"))
        assertTrue(value.contains("é"))
        assertTrue(value.contains("\uD83D\uDE00"))
    }

    @Test fun keepsNumbersAsWritten() {
        assertEquals("0.3", AgentJson.write(numOf(0.3)))
        assertEquals("1024", AgentJson.write(numOf(1024)))
        assertEquals("0.3", AgentJson.parse("""{"t":0.3}""")!!.field("t")!!.let { (it as AgentJsonValue.Num).raw })
        assertEquals(7, AgentJson.parse("""{"t":7}""").intOrNull("t"))
    }

    @Test fun rejectsMalformedInputInsteadOfCrashing() {
        assertNull(AgentJson.parse(""))
        assertNull(AgentJson.parse("{"))
        assertNull(AgentJson.parse("""{"a":}"""))
        assertNull(AgentJson.parse("""{"a":1}trailing"""))
        assertNull(AgentJson.parse("[1,"))
        assertNull(AgentJson.parse("""{"a":1,}"""))
        assertNull(AgentJson.parse("data: halo"))
    }

    @Test fun rejectsPathologicallyDeepInput() {
        assertNull(AgentJson.parse("[".repeat(200) + "]".repeat(200)))
    }

    @Test fun writeRoundTripsSpecialCharacters() {
        val original = objOf(
            "judul" to strOf("Catatan \"penting\"\nbaris 2\t📌"),
            "n" to numOf(3),
            "ok" to boolOf(true),
            "kosong" to AgentJsonValue.Null,
            "daftar" to arrOf(strOf("a,b"), strOf("c")),
        )

        val parsed = AgentJson.parse(AgentJson.write(original))

        assertEquals("Catatan \"penting\"\nbaris 2\t📌", parsed.stringOrNull("judul"))
        assertEquals(3, parsed.intOrNull("n"))
        assertEquals(true, parsed.boolOrNull("ok"))
        assertEquals(2, parsed.field("daftar").asItems.size)
    }

    @Test fun accessorsAreTypeSafe() {
        val root = AgentJson.parse("""{"n":5,"s":"{\"x\":1}","o":{"k":1}}""")

        assertNull(root.stringOrNull("n"))
        assertNull(root.intOrNull("s"))
        assertNull(root.boolOrNull("n"))
        assertEquals(1, (root.stringOrNull("s").let { AgentJson.parse(it!!) }).intOrNull("x"))
        assertEquals(1, root.field("s").asParsedJson().intOrNull("x"))
        assertEquals(1, root.field("o").asItems.size)
        assertEquals(0, root.field("tidakAda").asItems.size)
    }
}
