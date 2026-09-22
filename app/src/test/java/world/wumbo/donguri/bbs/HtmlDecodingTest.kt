package world.wumbo.donguri.bbs

import org.junit.Assert.assertEquals
import org.junit.Test
import world.wumbo.donguri.bbs.text.asPlainPostText
import world.wumbo.donguri.bbs.text.htmlDecoded
import world.wumbo.donguri.bbs.text.renderPostText
import world.wumbo.donguri.bbs.text.resTargetOf

class HtmlDecodingTest {

    @Test
    fun `br becomes a newline`() {
        assertEquals("一\n二", "一<br>二".htmlDecoded())
        assertEquals("一\n二", "一<BR />二".htmlDecoded())
    }

    @Test
    fun `absolute anchors become markdown links and relative ones do not`() {
        assertEquals(
            "[サイト](https://example.test/)",
            """<a href="https://example.test/">サイト</a>""".htmlDecoded(),
        )
        assertEquals(">>1", """<a href="../test/read.cgi/1">&gt;&gt;1</a>""".htmlDecoded())
    }

    @Test
    fun `named and numeric entities are decoded`() {
        assertEquals("<>&\"'", "&lt;&gt;&amp;&quot;&apos;".htmlDecoded())
        assertEquals("あ", "&#12354;".htmlDecoded())
        assertEquals("あ", "&#x3042;".htmlDecoded())
    }

    @Test
    fun `an unknown entity is left alone`() {
        assertEquals("&notarealentity;", "&notarealentity;".htmlDecoded())
    }

    @Test
    fun `remaining tags are stripped but their text is kept`() {
        assertEquals("太字と斜体", "<b>太字</b>と<i>斜体</i>".htmlDecoded())
        assertEquals("色つき", """<font color="red">色つき</font>""".htmlDecoded())
    }

    @Test
    fun `plain text drops the link targets`() {
        assertEquals(
            ">>1 見て サイト",
            "[>>1](donguri://res/1) 見て [サイト](https://example.test/)".asPlainPostText(),
        )
    }

    @Test
    fun `rendering keeps link ranges over the visible text`() {
        val rendered = "[>>12](donguri://res/12) そうだね".renderPostText()

        assertEquals(">>12 そうだね", rendered.text)
        assertEquals(1, rendered.links.size)
        assertEquals(0, rendered.links.single().start)
        assertEquals(4, rendered.links.single().end)
        assertEquals("donguri://res/12", rendered.links.single().url)
    }

    @Test
    fun `bare urls are linkified without swallowing markdown link targets`() {
        val rendered = "[>>1](donguri://res/1) https://example.test/a".renderPostText()

        assertEquals(">>1 https://example.test/a", rendered.text)
        assertEquals(2, rendered.links.size)
        assertEquals("donguri://res/1", rendered.links[0].url)
        assertEquals("https://example.test/a", rendered.links[1].url)
        assertEquals("https://example.test/a", rendered.text.substring(rendered.links[1].start, rendered.links[1].end))
    }

    @Test
    fun `a link with an unsupported scheme keeps its label but loses the link`() {
        val rendered = "[やばい](javascript:danger)".renderPostText()

        assertEquals("やばい", rendered.text)
        assertEquals(emptyList<Any>(), rendered.links)
    }

    @Test
    fun `res targets are read back out of donguri links`() {
        assertEquals(12, resTargetOf("donguri://res/12"))
        assertEquals(null, resTargetOf("https://example.test/"))
    }
}
