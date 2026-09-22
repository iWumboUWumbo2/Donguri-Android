package world.wumbo.donguri.bbs.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import world.wumbo.donguri.bbs.model.BbsThread
import world.wumbo.donguri.bbs.net.ThreadService
import world.wumbo.donguri.bbs.store.ReadStateStore
import javax.inject.Inject

data class BoardUiState(
    val threads: List<BbsThread> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class BoardViewModel @Inject constructor(
    private val service: ThreadService,
    private val readStateStore: ReadStateStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BoardUiState())
    val uiState: StateFlow<BoardUiState> = _uiState.asStateFlow()

    val readStates = readStateStore.states

    private var boardUrl: String? = null

    /// Called from the screen once it knows its board; Navigation3 routes do not
    /// feed a `SavedStateHandle` the way Navigation2's argument bundles did.
    fun bind(boardUrl: String) {
        if (this.boardUrl == boardUrl) return
        this.boardUrl = boardUrl
        load(refresh = false)
    }

    fun load(refresh: Boolean = true) {
        val url = boardUrl ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = !refresh && it.threads.isEmpty(), isRefreshing = refresh, errorMessage = null)
            }
            runCatching { service.fetchThreads(url) }
                .onSuccess { threads ->
                    _uiState.value = BoardUiState(threads = threads, isLoading = false)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, isRefreshing = false, errorMessage = error.toString())
                    }
                }
        }
    }

    fun unreadCount(boardUrl: String, thread: BbsThread, states: Map<String, world.wumbo.donguri.bbs.model.ReadState>): Int? {
        val state = states[ReadStateStore.key(boardUrl, thread.id)] ?: return null
        val unread = thread.responseCount - state.lastReadCount
        return unread.takeIf { it > 0 }
    }
}
