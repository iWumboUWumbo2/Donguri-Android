package world.wumbo.donguri.bbs.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/// The board list served by menu.5ch.io, shaped exactly as that JSON is.
@Serializable
data class BbsMenu(
    @SerialName("description") val description: String = "",
    @SerialName("menu_list") val menuList: List<MenuCategory> = emptyList(),
    @SerialName("last_modify") val lastModify: Long = 0,
    @SerialName("last_modify_string") val lastModifyString: String = "",
)

@Serializable
data class MenuCategory(
    @SerialName("category_name") val categoryName: String,
    @SerialName("category_number") val categoryNumber: String,
    @SerialName("category_total") val categoryTotal: Int = 0,
    @SerialName("category_content") val categoryContent: List<Board> = emptyList(),
)

@Serializable
data class Board(
    @SerialName("category_order") val categoryOrder: Int,
    @SerialName("board_name") val boardName: String,
    @SerialName("url") val url: String,
    @SerialName("directory_name") val directoryName: String = "",
    @SerialName("category") val category: Int = 0,
    @SerialName("category_name") val categoryName: String = "",
)
