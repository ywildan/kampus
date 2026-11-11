package id.personal.campus.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {

    @Test fun readsOpenAiStyleDataArray() {
        val body = """{"object":"list","data":[{"id":"gpt-x"},{"id":"llama-3"}]}"""

        assertEquals(listOf("gpt-x", "llama-3"), ModelCatalog.parseModels(body))
    }

    @Test fun readsOllamaStyleModelsArray() {
        val body = """{"models":[{"name":"llama3:8b"},{"name":"phi3"}]}"""

        assertEquals(listOf("llama3:8b", "phi3"), ModelCatalog.parseModels(body))
    }

    @Test fun readsRootArrayAndRemovesDuplicates() {
        val body = """[{"id":"b"},{"id":"a"},{"name":"b"}]"""

        assertEquals(listOf("a", "b"), ModelCatalog.parseModels(body))
    }

    @Test fun skipsEntriesWithoutUsableId() {
        val body = """{"data":[{"id":"  "},{"id":"a"},{"name":null},{"tanpa":"id"},{}]}"""

        assertEquals(listOf("a"), ModelCatalog.parseModels(body))
    }

    @Test fun returnsEmptyListInsteadOfCrashingOnGarbage() {
        assertTrue(ModelCatalog.parseModels("").isEmpty())
        assertTrue(ModelCatalog.parseModels("bukan json").isEmpty())
        assertTrue(ModelCatalog.parseModels("""{"data":"bukan array tool"}""").isEmpty())
        assertTrue(ModelCatalog.parseModels("[]").isEmpty())
    }
}
