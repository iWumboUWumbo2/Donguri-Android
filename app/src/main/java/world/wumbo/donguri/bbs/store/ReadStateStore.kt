package world.wumbo.donguri.bbs.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import world.wumbo.donguri.bbs.model.ReadState
import world.wumbo.donguri.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/// Tracks how far the user has read into each thread, so the board list can
/// show an unread count and reopening a thread can resume near where the user
/// left off.
///
/// The whole map is held in memory and mirrored to DataStore as one JSON blob:
/// board lists read it once per row while composing, which a per-key flow would
/// make needlessly expensive.
@Singleton
class ReadStateStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _states = MutableStateFlow<Map<String, ReadState>>(emptyMap())
    val states: StateFlow<Map<String, ReadState>> = _states.asStateFlow()

    /// Writes wait for the stored map, so a thread closed moments after launch
    /// cannot persist a map containing only itself.
    private val loaded = CompletableDeferred<Unit>()

    init {
        scope.launch {
            val stored = dataStore.data.first()[KEY]
            if (stored != null) {
                _states.value = runCatching { json.decodeFromString<Map<String, ReadState>>(stored) }
                    .getOrDefault(emptyMap())
            }
            loaded.complete(Unit)
        }
    }

    fun state(key: String): ReadState? = _states.value[key]

    fun update(key: String, postCount: Int, readIndex: Int) {
        scope.launch {
            loaded.await()
            val existing = _states.value[key]
            val updated = ReadState(
                lastReadCount = max(existing?.lastReadCount ?: 0, postCount),
                lastReadIndex = max(existing?.lastReadIndex ?: 0, readIndex),
            )
            if (updated == existing) return@launch
            _states.value = _states.value + (key to updated)
            dataStore.edit { it[KEY] = json.encodeToString(_states.value) }
        }
    }

    companion object {
        private val KEY = stringPreferencesKey("donguri.readState")
        private val json = Json { ignoreUnknownKeys = true }

        fun key(boardUrl: String, threadId: Long): String = "$boardUrl|$threadId"
    }
}
