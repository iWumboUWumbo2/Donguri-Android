package world.wumbo.donguri.bbs.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import world.wumbo.donguri.bbs.model.BbsMenu
import world.wumbo.donguri.bbs.net.BbsMenuService
import javax.inject.Inject

data class BbsMenuUiState(
    val menu: BbsMenu? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class BbsMenuViewModel @Inject constructor(
    private val service: BbsMenuService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BbsMenuUiState())
    val uiState: StateFlow<BbsMenuUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { service.fetchBbsMenu() }
                .onSuccess { menu ->
                    _uiState.value = BbsMenuUiState(menu = menu, isLoading = false)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.toString())
                    }
                }
        }
    }
}
