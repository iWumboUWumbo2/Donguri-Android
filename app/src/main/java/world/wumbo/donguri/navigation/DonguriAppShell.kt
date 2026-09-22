package world.wumbo.donguri.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import world.wumbo.donguri.bbs.ui.BbsMenuScreen
import world.wumbo.donguri.bbs.ui.BoardScreen
import world.wumbo.donguri.bbs.ui.ThreadScreen
import world.wumbo.donguri.features.anki.AnkiConnectScreen
import world.wumbo.donguri.features.anki.AnkiScreen
import world.wumbo.donguri.features.dictionary.DictionaryScreen
import world.wumbo.donguri.features.ngfilter.NgFilterScreen
import world.wumbo.donguri.features.popupsettings.AudioSettingsScreen
import world.wumbo.donguri.features.popupsettings.CssEditorScreen
import world.wumbo.donguri.features.popupsettings.PopupSettingsScreen
import world.wumbo.donguri.features.dictionary.DictionarySearchScreen
import world.wumbo.donguri.features.settings.SettingsScreen

/// Donguri has a single navigation stack — board menu → board → thread, with
/// settings and dictionary search pushed on top — matching the iOS app's one
/// `NavigationStack`. There are no top-level tabs to keep separate stacks for.
@Composable
fun DonguriAppShell(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(AppRoute.BbsMenu)

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { pop() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = { key: NavKey ->
            NavEntry(key) {
                when (val route = key as AppRoute) {
                    AppRoute.BbsMenu -> BbsMenuScreen(
                        onOpenBoard = { board ->
                            backStack.add(AppRoute.Board(board.boardName, board.url))
                        },
                        onOpenSettings = { backStack.add(AppRoute.Settings) },
                        onOpenDictionarySearch = { backStack.add(AppRoute.DictionarySearch) },
                    )

                    is AppRoute.Board -> BoardScreen(
                        boardName = route.boardName,
                        boardUrl = route.boardUrl,
                        onOpenThread = { thread ->
                            backStack.add(AppRoute.Thread(route.boardUrl, thread.id, thread.title))
                        },
                        onBack = { pop() },
                    )

                    is AppRoute.Thread -> ThreadScreen(
                        boardUrl = route.boardUrl,
                        threadId = route.threadId,
                        threadTitle = route.title,
                        onOpenThread = { boardUrl, threadId ->
                            backStack.add(AppRoute.Thread(boardUrl, threadId, null))
                        },
                        onBack = { pop() },
                    )

                    AppRoute.DictionarySearch -> DictionarySearchScreen(onBack = { pop() })

                    AppRoute.Settings -> SettingsScreen(
                        onOpenSection = { backStack.add(AppRoute.SettingsDetail(it)) },
                        onBack = { pop() },
                    )

                    is AppRoute.SettingsDetail -> when (route.section) {
                        SettingsSection.Dictionaries -> DictionaryScreen(onBack = { pop() })
                        SettingsSection.Anki -> AnkiScreen(
                            onOpenAnkiConnect = {
                                backStack.add(AppRoute.SettingsDetail(SettingsSection.AnkiConnect))
                            },
                            onBack = { pop() },
                        )
                        SettingsSection.AnkiConnect -> AnkiConnectScreen(onBack = { pop() })
                        SettingsSection.NgFilter -> NgFilterScreen(onBack = { pop() })
                        SettingsSection.CustomCss -> CssEditorScreen(onBack = { pop() })
                        SettingsSection.Popup -> PopupSettingsScreen(onBack = { pop() })
                        SettingsSection.Audio -> AudioSettingsScreen(onBack = { pop() })
                    }
                }
            }
        },
    )
}
