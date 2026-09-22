package world.wumbo.donguri.bbs.text

/// A link inside a post's visible text. `start`/`end` index the visible text,
/// not the markdown-ish source, so they can be used directly for hit testing
/// and for styling an `AnnotatedString`.
data class PostLink(val start: Int, val end: Int, val url: String)

/// A post's text as the reader actually sees it, plus where its links are.
///
/// `PostService` hands us markdown-ish text where `>>N` anchors are
/// `[>>N](donguri://res/N)`; only the label is ever displayed. Anything
/// reasoning about the visible text — laying it out, hit-testing a tap for a
/// dictionary lookup, or handing it to a translator — wants this rather than
/// the raw form, which would drag `donguri://` URLs along.
data class RenderedPostText(val text: String, val links: List<PostLink>)

private val MARKDOWN_LINK = Regex("""\[([^\]]*)\]\(([^)\s]+)\)""")
private val BARE_URL = Regex("""https?://[^\s<>"']+""")

/// Schemes this app actually creates or follows. Anything else keeps its label
/// but loses its link — post text is untrusted content from 5ch.
private fun isFollowableScheme(url: String): Boolean =
    url.startsWith("donguri://") || url.startsWith("http://") || url.startsWith("https://")

fun String.renderPostText(): RenderedPostText {
    val out = StringBuilder()
    val links = mutableListOf<PostLink>()
    var cursor = 0

    fun appendLinkifyingBareUrls(chunk: String) {
        var last = 0
        for (match in BARE_URL.findAll(chunk)) {
            out.append(chunk, last, match.range.first)
            val start = out.length
            out.append(match.value)
            links += PostLink(start, out.length, match.value)
            last = match.range.last + 1
        }
        out.append(chunk, last, chunk.length)
    }

    for (match in MARKDOWN_LINK.findAll(this)) {
        // Text between the previous link and this one can still hold bare URLs.
        appendLinkifyingBareUrls(substring(cursor, match.range.first))

        val label = match.groupValues[1]
        val href = match.groupValues[2]
        if (isFollowableScheme(href)) {
            val start = out.length
            out.append(label)
            links += PostLink(start, out.length, href)
        } else {
            out.append(label)
        }
        cursor = match.range.last + 1
    }
    appendLinkifyingBareUrls(substring(cursor))

    return RenderedPostText(out.toString(), links)
}

/// Just the visible text, for translation and for NG word matching.
fun String.asPlainPostText(): String = MARKDOWN_LINK.replace(this) { it.groupValues[1] }

/// The post number a `donguri://res/N` link points at, or null.
fun resTargetOf(url: String): Int? =
    url.removePrefix("donguri://res/").takeIf { it != url }?.toIntOrNull()
