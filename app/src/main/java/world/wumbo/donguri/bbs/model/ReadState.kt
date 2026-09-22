package world.wumbo.donguri.bbs.model

import kotlinx.serialization.Serializable

@Serializable
data class ReadState(
    val lastReadCount: Int,
    val lastReadIndex: Int,
)
