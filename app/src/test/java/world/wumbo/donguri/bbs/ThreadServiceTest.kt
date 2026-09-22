package world.wumbo.donguri.bbs

import org.junit.Assert.assertEquals
import org.junit.Test
import world.wumbo.donguri.bbs.net.ThreadService

class ThreadServiceTest {

    @Test
    fun `parses subject dot txt rows`() {
        val body = """
            1750000000.dat<>スレタイだよ (123)
            1750000001.dat<>もう一つ	 (4)
        """.trimIndent()

        val threads = ThreadService.parseSubjectTxt(body)

        assertEquals(2, threads.size)
        assertEquals(1750000000L, threads[0].id)
        assertEquals("スレタイだよ", threads[0].title)
        assertEquals(123, threads[0].responseCount)
        assertEquals(4, threads[1].responseCount)
    }

    @Test
    fun `titles keep parentheses that are not the response count`() {
        val threads = ThreadService.parseSubjectTxt("1750000000.dat<>(・∀・)イイ!! (7)")

        assertEquals("(・∀・)イイ!!", threads.single().title)
        assertEquals(7, threads.single().responseCount)
    }

    @Test
    fun `entities in titles are decoded`() {
        val threads = ThreadService.parseSubjectTxt("1750000000.dat<>&lt;&gt;&amp;テスト (1)")

        assertEquals("<>&テスト", threads.single().title)
    }

    @Test
    fun `malformed rows are skipped rather than failing the whole board`() {
        val body = "garbage\n1750000000.dat<>ちゃんとしたスレ (2)\n\n"

        val threads = ThreadService.parseSubjectTxt(body)

        assertEquals(1, threads.size)
        assertEquals("ちゃんとしたスレ", threads.single().title)
    }
}
