package world.wumbo.donguri

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import world.wumbo.donguri.config.UserConfigRepository
import world.wumbo.donguri.di.ApplicationScope
import world.wumbo.donguri.dictionary.DictionaryRepository
import javax.inject.Inject

@HiltAndroidApp
class DonguriApplication : Application() {

    @Inject
    lateinit var dictionaryRepository: DictionaryRepository

    @Inject
    lateinit var userConfigRepository: UserConfigRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            // Built at launch rather than lazily on the first tap, so a tapped
            // word never waits for every dictionary to be opened.
            runCatching { dictionaryRepository.ensureLookupQueryReady() }
            autoUpdateDictionariesIfDue()
        }
    }

    /// Mirrors the iOS app's launch task: check for new dictionary revisions
    /// no more often than the configured interval.
    private suspend fun autoUpdateDictionariesIfDue() {
        // The stored config arrives asynchronously; wait for the first
        // non-default emission rather than acting on the defaults.
        val config = userConfigRepository.config.first()
        if (!config.autoUpdateDictionaries) return

        val now = System.currentTimeMillis()
        val due = config.lastDictionaryUpdateEpochMillis + config.dictionaryUpdateInterval.intervalMillis
        if (config.lastDictionaryUpdateEpochMillis != 0L && now < due) return

        runCatching { dictionaryRepository.updateDictionaries(config.lowRamDictionaryImport) }
        userConfigRepository.update { it.copy(lastDictionaryUpdateEpochMillis = now) }
    }
}
