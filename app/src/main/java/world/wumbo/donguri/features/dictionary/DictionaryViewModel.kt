package world.wumbo.donguri.features.dictionary

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import world.wumbo.donguri.config.UserConfigRepository
import world.wumbo.donguri.dictionary.DictionaryInfo
import world.wumbo.donguri.dictionary.DictionaryRepository
import world.wumbo.donguri.dictionary.DictionaryType
import world.wumbo.donguri.dictionary.DictionaryUpdateProgress
import world.wumbo.donguri.dictionary.RecommendedDictionaries
import javax.inject.Inject

data class DictionaryUiState(
    val dictionariesByType: Map<DictionaryType, List<DictionaryInfo>> = emptyMap(),
    val isBusy: Boolean = false,
    val busyMessage: String? = null,
    val message: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class DictionaryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DictionaryRepository,
    private val userConfigRepository: UserConfigRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DictionaryUiState())
    val uiState: StateFlow<DictionaryUiState> = _uiState.asStateFlow()

    val userConfig = userConfigRepository.config

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val byType = withContext(Dispatchers.IO) {
                DictionaryType.entries.associateWith { repository.loadDictionaries(it) }
            }
            _uiState.update { it.copy(dictionariesByType = byType) }
        }
    }

    fun importDictionary(uri: Uri) {
        runBusy { progress ->
            progress("…")
            val lowRam = userConfigRepository.config.value.lowRamDictionaryImport
            val count = repository.importDictionary(context.contentResolver, uri, lowRam)
            if (count == 0) "No new dictionaries imported" else "Imported $count"
        }
    }

    fun downloadRecommended() {
        runBusy { progress ->
            var imported = 0
            imported += repository.importRecommendedDictionaries(
                dictionaries = RecommendedDictionaries.filter { it.id in RECOMMENDED_DEFAULT_IDS },
                lowRamImport = userConfigRepository.config.value.lowRamDictionaryImport,
                onProgress = { p: DictionaryUpdateProgress -> progress("${p.stage.name}: ${p.title}") },
            )
            if (imported == 0) "Already up to date" else "Imported $imported"
        }
    }

    fun updateDictionaries() {
        runBusy { progress ->
            val summary = repository.updateDictionaries(
                lowRamImport = userConfigRepository.config.value.lowRamDictionaryImport,
                onProgress = { p -> progress("${p.stage.name}: ${p.title}") },
            )
            when {
                summary.failures.isNotEmpty() ->
                    "Updated ${summary.updatedCount}, ${summary.failures.size} failed"
                summary.updatedCount == 0 -> "Already up to date"
                else -> "Updated ${summary.updatedCount}"
            }
        }
    }

    fun setEnabled(type: DictionaryType, fileName: String, enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.setDictionaryEnabled(type, fileName, enabled) }
            refresh()
        }
    }

    fun delete(type: DictionaryType, fileName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.deleteDictionary(type, fileName) }
            refresh()
        }
    }

    fun move(type: DictionaryType, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.moveDictionary(type, fromIndex, toIndex) }
            refresh()
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null, errorMessage = null) }

    /// Imports and updates are long, blocking and file-system bound; they run
    /// on IO with a progress line, and the screen blocks while they do.
    private fun runBusy(work: suspend ((String) -> Unit) -> String) {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, busyMessage = null, message = null, errorMessage = null) }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    work { line -> _uiState.update { it.copy(busyMessage = line) } }
                }
            }
            _uiState.update {
                it.copy(
                    isBusy = false,
                    busyMessage = null,
                    message = result.getOrNull(),
                    errorMessage = result.exceptionOrNull()?.let { error ->
                        error.localizedMessage ?: error::class.java.simpleName
                    },
                )
            }
            refresh()
        }
    }

    private companion object {
        /// What iOS's "Download Recommended Dictionaries" fetches.
        val RECOMMENDED_DEFAULT_IDS = setOf("jmdict", "jmnedict", "jiten")
    }
}
