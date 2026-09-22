package world.wumbo.donguri.bbs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import world.wumbo.donguri.bbs.model.NgKind
import world.wumbo.donguri.bbs.model.NgRule
import world.wumbo.donguri.bbs.model.Post
import world.wumbo.donguri.bbs.store.NgFilterStore

class NgFilterTest {

    private val post = Post(
        name = "名無し◆trip123",
        trip = "trip123",
        email = "",
        date = "date",
        id = "AbCdEf01",
        text = "これはNGワードを含む本文",
        threadTitle = null,
    )

    @Test
    fun `no rules hides nothing`() {
        assertFalse(NgFilterStore.hides(post, emptyList()))
    }

    @Test
    fun `a word rule matches the body case-insensitively`() {
        assertTrue(NgFilterStore.hides(post, listOf(NgRule(kind = NgKind.WORD, pattern = "ngワード"))))
        assertFalse(NgFilterStore.hides(post, listOf(NgRule(kind = NgKind.WORD, pattern = "別の語"))))
    }

    @Test
    fun `an id rule matches on a substring of the id`() {
        assertTrue(NgFilterStore.hides(post, listOf(NgRule(kind = NgKind.ID, pattern = "abcdef01"))))
    }

    @Test
    fun `a name rule matches the name or the tripcode`() {
        assertTrue(NgFilterStore.hides(post, listOf(NgRule(kind = NgKind.NAME, pattern = "名無し"))))
        assertTrue(NgFilterStore.hides(post, listOf(NgRule(kind = NgKind.NAME, pattern = "trip123"))))
    }

    @Test
    fun `an id rule does not match a post without an id`() {
        val anonymous = post.copy(id = null)

        assertFalse(NgFilterStore.hides(anonymous, listOf(NgRule(kind = NgKind.ID, pattern = "AbCdEf01"))))
    }
}
