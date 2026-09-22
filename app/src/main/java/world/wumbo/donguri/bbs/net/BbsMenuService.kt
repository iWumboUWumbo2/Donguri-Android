package world.wumbo.donguri.bbs.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import world.wumbo.donguri.bbs.model.BbsMenu
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BbsMenuService @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchBbsMenu(): BbsMenu = withContext(Dispatchers.IO) {
        val body = httpGet(MENU_URL).toString(Charsets.UTF_8)
        json.decodeFromString(BbsMenu.serializer(), body)
    }

    private companion object {
        const val MENU_URL = "https://menu.5ch.io/bbsmenu.json"
    }
}
