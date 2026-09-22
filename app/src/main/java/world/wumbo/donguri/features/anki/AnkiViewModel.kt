package world.wumbo.donguri.features.anki

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnkiUiState(
    val isFetching: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
    val needsPermission: Boolean = false,
)

@HiltViewModel
class AnkiViewModel @Inject constructor(
    private val repository: AnkiRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AnkiUiState())
    val uiState: StateFlow<AnkiUiState> = _uiState.asStateFlow()

    val settings = repository.settings

    fun fetchConfiguration() {
        if (_uiState.value.isFetching) return
        viewModelScope.launch {
            _uiState.update { it.copy(isFetching = true, message = null, errorMessage = null) }
            when (val result = repository.fetchConfiguration()) {
                is AnkiFetchResult.Success -> _uiState.update {
                    it.copy(
                        isFetching = false,
                        message = "${result.decks.size} decks, ${result.noteTypes.size} note types",
                    )
                }
                is AnkiFetchResult.Error -> _uiState.update {
                    it.copy(
                        isFetching = false,
                        errorMessage = result.message,
                        needsPermission = result.failure == AnkiFetchFailure.PermissionDenied,
                    )
                }
            }
        }
    }

    fun setBackend(kind: AnkiBackendKind) = repository.updateSettings { it.copy(backendKind = kind) }

    fun selectDeck(deck: AnkiDeck) = repository.updateSettings {
        it.copy(selectedDeckId = deck.id, selectedDeckName = deck.name)
    }

    /// Selecting a note type also seeds its field mappings when Donguri ships
    /// a template for it, so a Lapis user has a working card immediately.
    fun selectNoteType(noteType: AnkiNoteType) = repository.updateSettings { current ->
        val mappings = if (AnkiFieldTemplates.matches(noteType)) {
            AnkiFieldTemplates.defaultMappings(noteType)
        } else {
            current.fieldMappings.filterKeys { it in noteType.fields }
        }
        current.copy(
            selectedNoteTypeId = noteType.id,
            selectedNoteTypeName = noteType.name,
            fieldMappings = mappings,
        )
    }

    fun setFieldMapping(field: String, template: String) = repository.updateSettings { current ->
        current.copy(fieldMappings = current.fieldMappings + (field to template))
    }

    fun applyTemplate() = repository.updateSettings { current ->
        val noteType = current.selectedNoteType ?: return@updateSettings current
        current.copy(fieldMappings = AnkiFieldTemplates.defaultMappings(noteType))
    }

    fun setTags(tags: String) = repository.updateSettings { it.copy(tags = tags) }
    fun setAllowDupes(allow: Boolean) = repository.updateSettings { it.copy(allowDupes = allow) }
    fun setDuplicateScope(scope: AnkiDuplicateScope) = repository.updateSettings { it.copy(duplicateScope = scope) }
    fun setCompactGlossaries(compact: Boolean) = repository.updateSettings { it.copy(compactGlossaries = compact) }
    fun setAnkiConnectUrl(url: String) = repository.updateSettings { it.copy(ankiConnectUrl = url) }
    fun setAnkiConnectApiKey(key: String) = repository.updateSettings { it.copy(ankiConnectApiKey = key) }
    fun setAnkiConnectForceSync(sync: Boolean) = repository.updateSettings { it.copy(ankiConnectForceSync = sync) }

    fun consumeMessage() = _uiState.update { it.copy(message = null, errorMessage = null) }
}
