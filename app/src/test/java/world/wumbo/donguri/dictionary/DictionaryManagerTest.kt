package world.wumbo.donguri.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DictionaryManagerTest {

    private fun stored(name: String) = DictionaryInfo(
        index = DictionaryIndex(title = name, revision = "1"),
        path = File("/dictionaries/$name"),
    )

    @Test
    fun `configured dictionaries keep their order and enabled flag`() {
        val result = DictionaryManager.collectDictionaries(
            storedDicts = listOf(stored("a"), stored("b")),
            configDicts = listOf(
                DictionaryConfig.DictionaryEntry(fileName = "b", isEnabled = false, order = 0),
                DictionaryConfig.DictionaryEntry(fileName = "a", isEnabled = true, order = 1),
            ),
        )

        assertEquals(listOf("b", "a"), result.map { it.path.name })
        assertEquals(listOf(false, true), result.map { it.isEnabled })
    }

    @Test
    fun `a newly imported dictionary lands at the end, enabled`() {
        val result = DictionaryManager.collectDictionaries(
            storedDicts = listOf(stored("a"), stored("new")),
            configDicts = listOf(DictionaryConfig.DictionaryEntry(fileName = "a", isEnabled = true, order = 0)),
        )

        assertEquals(listOf("a", "new"), result.map { it.path.name })
        assertTrue(result.last().isEnabled)
    }

    @Test
    fun `a config entry for a deleted dictionary is ignored`() {
        val result = DictionaryManager.collectDictionaries(
            storedDicts = listOf(stored("a")),
            configDicts = listOf(
                DictionaryConfig.DictionaryEntry(fileName = "gone", isEnabled = true, order = 0),
                DictionaryConfig.DictionaryEntry(fileName = "a", isEnabled = true, order = 1),
            ),
        )

        assertEquals(listOf("a"), result.map { it.path.name })
    }

    @Test
    fun `moving a dictionary renumbers every entry`() {
        val dictionaries = listOf(stored("a"), stored("b"), stored("c"))

        val moved = DictionaryManager.moveDictionaries(dictionaries, fromIndex = 2, toIndex = 0)

        assertEquals(listOf("c", "a", "b"), moved.map { it.fileName })
        assertEquals(listOf(0, 1, 2), moved.map { it.order })
    }

    @Test
    fun `moving from an index that does not exist leaves the order alone`() {
        val dictionaries = listOf(stored("a"), stored("b"))

        val moved = DictionaryManager.moveDictionaries(dictionaries, fromIndex = 5, toIndex = 0)

        assertEquals(listOf("a", "b"), moved.map { it.fileName })
    }
}
