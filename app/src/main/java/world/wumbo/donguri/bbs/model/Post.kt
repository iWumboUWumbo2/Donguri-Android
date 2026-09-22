package world.wumbo.donguri.bbs.model

data class Post(
    val name: String,
    val trip: String?,
    val email: String,
    val date: String,
    val id: String?,
    /// Markdown-ish text: `>>N` reply anchors are `[>>N](donguri://res/N)`.
    val text: String,
    val threadTitle: String?,
    /// Indices (into the thread's post list) of posts that reply to this one.
    val replies: List<Int> = emptyList(),
    /// Image URLs found in `text`, shown as thumbnails below the post.
    val imageUrls: List<String> = emptyList(),
)
