package world.wumbo.donguri.config

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
import world.wumbo.donguri.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

/// One JSON blob rather than a preference key per field: the popup reads the
/// whole config at once to build its document, and a settings screen edits
/// several fields in a row.
@Singleton
class UserConfigRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _config = MutableStateFlow(UserConfig())
    val config: StateFlow<UserConfig> = _config.asStateFlow()

    /// Writes wait for the stored config, so an edit made before the first
    /// read lands cannot overwrite it with the defaults.
    private val loaded = CompletableDeferred<Unit>()

    init {
        scope.launch {
            dataStore.data.first()[KEY]?.let { stored ->
                _config.value = runCatching { json.decodeFromString<UserConfig>(stored) }
                    .getOrDefault(UserConfig())
                    .normalized()
            }
            loaded.complete(Unit)
        }
    }

    fun update(transform: (UserConfig) -> UserConfig) {
        scope.launch {
            loaded.await()
            val updated = transform(_config.value).normalized()
            if (updated == _config.value) return@launch
            _config.value = updated
            dataStore.edit { it[KEY] = json.encodeToString(updated) }
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("donguri.userConfig")
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
