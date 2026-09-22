package world.wumbo.donguri.bbs.model

/// One row of a board's `subject.txt`. `id` is the thread's dat number, which
/// is also its creation time as a unix timestamp.
data class BbsThread(
    val id: Long,
    val title: String?,
    val responseCount: Int,
)
