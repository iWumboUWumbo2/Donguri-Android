package world.wumbo.donguri.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute : NavKey {
    /// The board menu — the app's root, as `BBSMenuView` is on iOS.
    @Serializable
    data object BbsMenu : AppRoute

    @Serializable
    data class Board(val boardName: String, val boardUrl: String) : AppRoute

    @Serializable
    data class Thread(
        val boardUrl: String,
        val threadId: Long,
        /// Known when navigating from a board list, absent when following a
        /// cross-thread link, where the title only arrives with the posts.
        val title: String? = null,
    ) : AppRoute

    @Serializable
    data object DictionarySearch : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data class SettingsDetail(val section: SettingsSection) : AppRoute
}

@Serializable
enum class SettingsSection {
    Dictionaries,
    Anki,
    AnkiConnect,
    NgFilter,
    CustomCss,
    Popup,
    Audio,
}
