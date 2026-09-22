package world.wumbo.donguri.bbs.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import world.wumbo.donguri.bbs.model.Post
import world.wumbo.donguri.bbs.text.htmlDecoded
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostService @Inject constructor() {

    suspend fun fetchPosts(boardUrl: String, threadId: Long): List<Post> = withContext(Dispatchers.IO) {
        try {
            fetchFromDat(boardUrl, threadId)
        } catch (e: HttpStatusException) {
            if (e.statusCode != HttpURLConnection.HTTP_NOT_FOUND) throw e
            // The thread has "dat落ち" (aged out of the live dat store). Fall back
            // to parsing the server's own read.cgi HTML page, which keeps serving
            // old threads from its archive long after the raw .dat is gone.
            fetchFromArchive(boardUrl, threadId)
        }
    }

    private fun fetchFromDat(boardUrl: String, threadId: Long): List<Post> {
        val url = resolveAgainstBoard(boardUrl, "dat/$threadId.dat")
        return parseDat(httpGet(url).toString(SHIFT_JIS))
    }

    /// Parses a thread's read.cgi HTML page — used once a thread's raw .dat has
    /// aged out of the live server (dat落ち), since the server still renders old
    /// threads as HTML long after the .dat itself is gone.
    private fun fetchFromArchive(boardUrl: String, threadId: Long): List<Post> {
        val url = archiveUrl(boardUrl, threadId)
        return parseArchive(httpGet(url).toString(SHIFT_JIS))
    }

    internal companion object {
        private const val ABORN_MARKER = "あぼーん"

        private val DAT_LINE = Regex(
            """(.*?)<>(.*?)<>(.*?)(?:\s+ID:([^\s<>]+))?(?:\s+BE:([^\s<>]+))?<>(.*?)<>(.*)"""
        )
        private val RES_ANCHOR = Regex(""">>(\d+)""")
        private val IMAGE_URL = Regex(
            """https?://[^\s<>"']+\.(?:jpe?g|png|gif|webp|bmp)(?:\?[^\s<>"']*)?""",
            RegexOption.IGNORE_CASE,
        )
        private val ARCHIVE_TITLE = Regex("""<title>(.*?)</title>""", setOf(RegexOption.DOT_MATCHES_ALL))
        private val ARCHIVE_POST_START = Regex(
            """<div id="\d+" data-date="[^"]*" data-userid="([^"]*)" data-id="\d+" class="clear post">"""
        )
        private val ARCHIVE_NAME = Regex("""<span class="postusername">(.*?)</span>""", setOf(RegexOption.DOT_MATCHES_ALL))
        private val ARCHIVE_DATE = Regex("""<span class="date">(.*?)</span>""", setOf(RegexOption.DOT_MATCHES_ALL))
        private val ARCHIVE_CONTENT = Regex("""<div class="post-content">(.*?)</div>""", setOf(RegexOption.DOT_MATCHES_ALL))

        fun archiveUrl(boardUrl: String, threadId: Long): String {
            val parsed = URL(boardUrl)
            val directory = parsed.path.split('/').firstOrNull { it.isNotEmpty() } ?: ""
            return "https://${parsed.host}/test/read.cgi/$directory/$threadId/"
        }

        fun parseDat(body: String): List<Post> {
            // First pass: parse each line into a post, without reply back-references
            // (those require knowing about every post first).
            val parsed = body.lineSequence().mapNotNull { line ->
                if (line.isEmpty()) return@mapNotNull null
                // Skip aborn (deleted/moderated) posts
                if (line.startsWith(ABORN_MARKER)) return@mapNotNull null

                val match = DAT_LINE.matchEntire(line) ?: return@mapNotNull null
                val name = match.groupValues[1].htmlDecoded()
                val text = withResAnchors(match.groupValues[6].htmlDecoded())
                val rawTitle = match.groupValues[7]

                Post(
                    name = name,
                    trip = extractTrip(name),
                    email = match.groupValues[2].htmlDecoded(),
                    date = match.groupValues[3],
                    id = match.groupValues[4].takeIf { it.isNotEmpty() },
                    text = text,
                    threadTitle = rawTitle.takeIf { it.isNotEmpty() }
                        ?.substringBefore('\t')?.trim(),
                    imageUrls = extractImageUrls(text),
                )
            }.toList()

            return attachReplies(parsed)
        }

        fun parseArchive(html: String): List<Post> {
            val title = ARCHIVE_TITLE.find(html)?.groupValues?.get(1)?.trim()

            // Each post is a <div id="N" data-date="…" data-userid="ID:xxxx" data-id="N"
            // class="clear post"> …</div>; scanning for just the start markers and
            // slicing between consecutive ones avoids needing to balance nested tags.
            val starts = ARCHIVE_POST_START.findAll(html).toList()

            val parsed = starts.mapIndexedNotNull { index, match ->
                val chunkStart = match.range.last + 1
                val chunkEnd = if (index + 1 < starts.size) starts[index + 1].range.first else html.length
                val chunk = html.substring(chunkStart, chunkEnd)

                val name = ARCHIVE_NAME.find(chunk)?.groupValues?.get(1) ?: return@mapIndexedNotNull null
                val date = ARCHIVE_DATE.find(chunk)?.groupValues?.get(1) ?: return@mapIndexedNotNull null
                val content = ARCHIVE_CONTENT.find(chunk)?.groupValues?.get(1) ?: return@mapIndexedNotNull null

                val decodedName = name.htmlDecoded()
                val rawUserId = match.groupValues[1]
                val id = when {
                    rawUserId.startsWith("ID:") -> rawUserId.removePrefix("ID:")
                    rawUserId.isEmpty() -> null
                    else -> rawUserId
                }
                val text = withResAnchors(content.htmlDecoded())

                Post(
                    name = decodedName,
                    trip = extractTrip(decodedName),
                    email = "",
                    date = date.htmlDecoded(),
                    id = id,
                    text = text,
                    threadTitle = if (index == 0) title else null,
                    imageUrls = extractImageUrls(text),
                )
            }

            return attachReplies(parsed)
        }

        private fun withResAnchors(text: String): String =
            RES_ANCHOR.replace(text) { "[>>${it.groupValues[1]}](donguri://res/${it.groupValues[1]})" }

        /// A tripcode only exists if "◆" actually appears in the name — splitting
        /// alone returns the whole string as a single element when the separator is
        /// absent, so without this check every plain anonymous name would be
        /// misread as its own "tripcode".
        private fun extractTrip(name: String): String? {
            val components = name.split("◆")
            if (components.size <= 1) return null
            return components.last().split(" ").firstOrNull()?.takeIf { it.isNotEmpty() }
        }

        private fun extractImageUrls(text: String): List<String> =
            IMAGE_URL.findAll(text).map { it.value }.toList()

        /// Second pass: figure out which posts reply to which via >>N, then rebuild
        /// the posts with `replies` populated.
        private fun attachReplies(posts: List<Post>): List<Post> {
            if (posts.isEmpty()) return posts
            val repliesByIndex = HashMap<Int, MutableList<Int>>()
            posts.forEachIndexed { i, post ->
                for (match in RES_ANCHOR.findAll(post.text)) {
                    val n = match.groupValues[1].toIntOrNull() ?: continue
                    val targetIndex = n - 1
                    if (targetIndex !in posts.indices) continue
                    repliesByIndex.getOrPut(targetIndex) { mutableListOf() }.add(i)
                }
            }
            return posts.mapIndexed { index, post ->
                post.copy(replies = repliesByIndex[index].orEmpty())
            }
        }
    }
}
