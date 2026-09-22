package world.wumbo.donguri.bbs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import world.wumbo.donguri.bbs.net.PostService

class PostServiceTest {

    private val datLine = "名無しさん<>sage<>2026/06/15(日) 12:34:56.78 ID:AbCdEf01<>本文です<>スレタイ"

    @Test
    fun `parses a dat line into a post`() {
        val posts = PostService.parseDat(datLine)

        assertEquals(1, posts.size)
        val post = posts.single()
        assertEquals("名無しさん", post.name)
        assertEquals("sage", post.email)
        assertEquals("2026/06/15(日) 12:34:56.78", post.date)
        assertEquals("AbCdEf01", post.id)
        assertEquals("本文です", post.text)
        assertEquals("スレタイ", post.threadTitle)
    }

    @Test
    fun `only the first line carries the thread title`() {
        val body = "$datLine\n名無しさん<><>2026/06/15(日) 12:35:00.00 ID:Zz9<>二番目<>"
        val posts = PostService.parseDat(body)

        assertEquals(2, posts.size)
        assertEquals("スレタイ", posts[0].threadTitle)
        assertNull(posts[1].threadTitle)
    }

    @Test
    fun `res anchors become donguri links and back-reference the target`() {
        val body = buildString {
            appendLine("名無し<><>date<>いちばん<>タイトル")
            append("名無し<><>date<>&gt;&gt;1 そうだね<>")
        }
        val posts = PostService.parseDat(body)

        assertEquals("[>>1](donguri://res/1) そうだね", posts[1].text)
        assertEquals(listOf(1), posts[0].replies)
        assertEquals(emptyList<Int>(), posts[1].replies)
    }

    @Test
    fun `a res anchor past the end of the thread is not a reply`() {
        val posts = PostService.parseDat("名無し<><>date<>&gt;&gt;99<>タイトル")

        assertEquals(1, posts.size)
        assertEquals(emptyList<Int>(), posts.single().replies)
    }

    @Test
    fun `aborn lines are dropped`() {
        val body = "あぼーん<>あぼーん<>あぼーん<>あぼーん<>\n名無し<><>date<>本文<>タイトル"
        val posts = PostService.parseDat(body)

        assertEquals(1, posts.size)
        assertEquals("本文", posts.single().text)
    }

    @Test
    fun `a tripcode is only read when the name actually contains one`() {
        val withTrip = PostService.parseDat("名無し◆abc123 <><>date<>本文<>タイトル").single()
        val withoutTrip = PostService.parseDat("名無しさん<><>date<>本文<>タイトル").single()

        assertEquals("abc123", withTrip.trip)
        assertNull(withoutTrip.trip)
    }

    @Test
    fun `image urls in the body become thumbnails`() {
        val post = PostService
            .parseDat("名無し<><>date<>見て https://i.imgur.com/a.jpg と https://x.test/b.png?size=1<>タイトル")
            .single()

        assertEquals(
            listOf("https://i.imgur.com/a.jpg", "https://x.test/b.png?size=1"),
            post.imageUrls,
        )
    }

    @Test
    fun `live dat shapes parse - millisecond dates and slashes in ids`() {
        val post = PostService
            .parseDat("以下、5ちゃんねるからVIPがお送りします<><>2026/09/20(日) 22:56:17.686 ID:G/Nys4k80<>本文<>スレタイ")
            .single()

        assertEquals("2026/09/20(日) 22:56:17.686", post.date)
        assertEquals("G/Nys4k80", post.id)
        assertEquals("本文", post.text)
    }

    @Test
    fun `a BE token after the id is not mistaken for part of it`() {
        val post = PostService
            .parseDat("名無し<><>date ID:AbCd0 BE:123456789-2BP(1000)<>本文<>スレタイ")
            .single()

        assertEquals("AbCd0", post.id)
        assertEquals("date", post.date)
    }

    @Test
    fun `an id is optional`() {
        val post = PostService.parseDat("名無し<><>2026/06/15(日) 12:34:56.78<>本文<>タイトル").single()

        assertNull(post.id)
        assertEquals("2026/06/15(日) 12:34:56.78", post.date)
    }

    @Test
    fun `read cgi archive pages parse into posts`() {
        val html = """
            <html><head><title>アーカイブスレ</title></head><body>
            <div id="1" data-date="2026/06/15" data-userid="ID:Arch1" data-id="1" class="clear post">
            <span class="postusername">名無し</span><span class="date">2026/06/15(日) 00:00:00</span>
            <div class="post-content">最初のレス</div>
            </div>
            <div id="2" data-date="2026/06/15" data-userid="ID:Arch2" data-id="2" class="clear post">
            <span class="postusername">名無し</span><span class="date">2026/06/15(日) 00:01:00</span>
            <div class="post-content">&gt;&gt;1 ですね</div>
            </div>
            </body></html>
        """.trimIndent()

        val posts = PostService.parseArchive(html)

        assertEquals(2, posts.size)
        assertEquals("アーカイブスレ", posts[0].threadTitle)
        assertEquals("Arch1", posts[0].id)
        assertEquals("最初のレス", posts[0].text)
        assertEquals("[>>1](donguri://res/1) ですね", posts[1].text)
        assertEquals(listOf(1), posts[0].replies)
    }

    @Test
    fun `archive url is built from the board host and directory`() {
        assertEquals(
            "https://mi.5ch.net/test/read.cgi/news4vip/1234567890/",
            PostService.archiveUrl("https://mi.5ch.net/news4vip/", 1234567890L),
        )
    }
}
