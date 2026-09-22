package world.wumbo.donguri.features.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.manhhao.hoshi.LookupResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import world.wumbo.donguri.config.UserConfigRepository
import world.wumbo.donguri.dictionary.DictionaryRepository
import javax.inject.Inject

data class DictionarySearchUiState(
    val query: String = "",
    val results: List<LookupResult> = emptyList(),
    val dictionaryStyles: Map<String, String> = emptyMap(),
    val hasSearched: Boolean = false,
    val isSearching: Boolean = false,
    val generation: Int = 0,
)

@HiltViewModel
class DictionarySearchViewModel @Inject constructor(
    private val repository: DictionaryRepository,
    userConfigRepository: UserConfigRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DictionarySearchUiState())
    val uiState: StateFlow<DictionarySearchUiState> = _uiState.asStateFlow()

    val userConfig = userConfigRepository.config

    private val configFlow = userConfigRepository.config

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            val config = configFlow.value
            val (results, styles) = withContext(Dispatchers.IO) {
                val found = runCatching {
                    // A standalone search scans the whole query rather than a
                    // tap window, so the scan length is the query itself.
                    repository.lookup(
                        text = query,
                        maxResults = config.maxResults,
                        scanLength = maxOf(query.length, config.scanLength),
                    )
                }.getOrDefault(emptyList())
                found to runCatching { repository.dictionaryStyles() }.getOrDefault(emptyMap())
            }
            _uiState.update {
                it.copy(
                    results = results,
                    dictionaryStyles = styles,
                    hasSearched = true,
                    isSearching = false,
                    generation = it.generation + 1,
                )
            }
        }
    }

    fun lookupRedirect(query: String): List<LookupResult> {
        val config = configFlow.value
        val results = repository.lookup(query, config.maxResults, maxOf(query.length, config.scanLength))
        _uiState.update { it.copy(results = results) }
        return results
    }

    fun dictionaryMedia(dictionary: String, path: String): ByteArray? =
        repository.dictionaryMedia(dictionary, path)
}
