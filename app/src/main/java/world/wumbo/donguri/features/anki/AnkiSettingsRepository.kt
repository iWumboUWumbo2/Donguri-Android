package world.wumbo.donguri.features.anki

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

@Singleton
class AnkiSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _settings = MutableStateFlow(AnkiSettings())
    val settings: StateFlow<AnkiSettings> = _settings.asStateFlow()

    /// Writes wait for the stored settings, so an edit made before the first
    /// read lands cannot overwrite it with the defaults.
    private val loaded = CompletableDeferred<Unit>()

    init {
        scope.launch {
            dataStore.data.first()[KEY]?.let { stored ->
                _settings.value = runCatching { json.decodeFromString<AnkiSettings>(stored) }
                    .getOrDefault(AnkiSettings())
            }
            loaded.complete(Unit)
        }
    }

    fun update(transform: (AnkiSettings) -> AnkiSettings) {
        scope.launch {
            loaded.await()
            val updated = transform(_settings.value)
            if (updated == _settings.value) return@launch
            _settings.value = updated
            dataStore.edit { it[KEY] = json.encodeToString(updated) }
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("donguri.ankiSettings")
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
