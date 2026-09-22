package world.wumbo.donguri.lookup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextScannerTest {

    @Test
    fun `a tap scans forward to the first delimiter`() {
        val selection = TextScanner.select(
            source = "今日は食べさせられた。",
            offset = 3,
            maxLength = 16,
            scanNonJapaneseText = true,
        )

        assertEquals("食べさせられた", selection?.text)
    }

    @Test
    fun `the scan stops at max length`() {
        val selection = TextScanner.select("あいうえおかきくけこ", offset = 0, maxLength = 4, scanNonJapaneseText = true)

        assertEquals("あいうえ", selection?.text)
    }

    @Test
    fun `tapping a delimiter selects nothing`() {
        assertNull(TextScanner.select("今日は。明日は。", offset = 3, maxLength = 16, scanNonJapaneseText = true))
    }

    @Test
    fun `non-japanese text is skipped when the setting is off`() {
        assertNull(TextScanner.select("hello 世界", offset = 0, maxLength = 16, scanNonJapaneseText = false))
        assertEquals(
            "hello",
            TextScanner.select("hello 世界", offset = 0, maxLength = 16, scanNonJapaneseText = true)?.text,
        )
    }

    @Test
    fun `the sentence around a tap stops at sentence delimiters`() {
        val source = "一文目です。二文目の食べ物。三文目。"

        assertEquals("二文目の食べ物。", TextScanner.sentenceAround(source, source.indexOf("二")))
    }

    @Test
    fun `trailing closing punctuation stays with the sentence`() {
        val source = "彼は「そうだね。」と言った。"

        assertEquals("彼は「そうだね。」", TextScanner.sentenceAround(source, 0))
    }

    @Test
    fun `an unmatched opening bracket is trimmed off the front`() {
        val source = "「これは文です。"

        assertEquals("これは文です。", TextScanner.sentenceAround(source, 3))
    }

    @Test
    fun `a sentence with no delimiters is returned whole`() {
        assertEquals("区切りのない文", TextScanner.sentenceAround("区切りのない文", 2))
    }

    @Test
    fun `an offset outside the text selects nothing`() {
        assertNull(TextScanner.select("短い", offset = 9, maxLength = 16, scanNonJapaneseText = true))
    }
}
