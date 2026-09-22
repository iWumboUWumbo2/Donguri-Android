package world.wumbo.donguri.bbs.model

import kotlinx.serialization.Serializable
import java.util.UUID

/// One NG (あぼーん) rule. 5ch clients traditionally filter on the post body
/// (NGワード), the poster's ID (NGID) or their name/tripcode (NG名前).
@Serializable
data class NgRule(
    val id: String = UUID.randomUUID().toString(),
    val kind: NgKind,
    val pattern: String,
)

@Serializable
enum class NgKind { WORD, ID, NAME }
