package world.wumbo.donguri.bbs.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import world.wumbo.donguri.bbs.model.NgKind
import world.wumbo.donguri.bbs.model.Post
import world.wumbo.donguri.bbs.net.PostService
import world.wumbo.donguri.bbs.store.NgFilterStore
import world.wumbo.donguri.bbs.store.ReadStateStore
import world.wumbo.donguri.config.UserConfigRepository
import world.wumbo.donguri.features.anki.AnkiMineResult
import world.wumbo.donguri.features.anki.AnkiMiningContext
import world.wumbo.donguri.features.anki.AnkiRepository
import world.wumbo.donguri.features.anki.toPopupSettings
import world.wumbo.donguri.popup.AnkiPopupFormat
import world.wumbo.donguri.popup.AnkiPopupSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import world.wumbo.donguri.dictionary.DictionaryRepository
import world.wumbo.donguri.lookup.TextScanner
import world.wumbo.donguri.popup.LookupPopupState
import androidx.compose.ui.geometry.Rect
import javax.inject.Inject
import kotlin.math.max

data class ThreadUiState(
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    /// ID → post indices, used to highlight every post from the same poster.
    val idIndices: Map<String, List<Int>> = emptyMap(),
    val tripIndices: Map<String, List<Int>> = emptyMap(),
    val highlightedId: String? = null,
    val highlightedTrip: String? = null,
    /// Index to scroll to once a fresh load's rows exist, for resuming where
    /// the user left off.
    val resumeIndex: Int? = null,
    /// The open pop-up dictionary, if any, and which post's characters it
    /// matched so the word can be highlighted underneath it.
    val popup: LookupPopupState? = null,
    val popupPostIndex: Int? = null,
    val popupHighlight: IntRange? = null,
)

@HiltViewModel
class ThreadViewModel @Inject constructor(
    private val postService: PostService,
    private val readStateStore: ReadStateStore,
    private val ngFilterStore: NgFilterStore,
    private val dictionaryRepository: DictionaryRepository,
    private val userConfigRepository: UserConfigRepository,
    private val ankiRepository: AnkiRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ThreadUiState())
    val uiState: StateFlow<ThreadUiState> = _uiState.asStateFlow()

    val ngRules = ngFilterStore.rules
    val userConfig = userConfigRepository.config

    private var popupGeneration = 0

    /// The thread title travels with a mined card as `{document-title}`, the
    /// way the book title does in Hoshi Reader.
    private var threadTitle: String? = null

    val ankiPopupSettings: StateFlow<AnkiPopupSettings> = ankiRepository.settings
        .map { it.toPopupSettings(isBackendAvailable = ankiRepository.isBackendAvailable()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnkiPopupSettings())

    fun setThreadTitle(title: String?) {
        threadTitle = title
    }

    /// popup.js sends `{formatId, payload}` and wants a truthy reply on
    /// success; anything falsy puts the mining button into its error state.
    fun mineEntry(requestJson: String, respond: (String) -> Unit) {
        val payloadJson = runCatching {
            json.parseToJsonElement(requestJson).jsonObject["payload"]?.jsonObject?.toString()
        }.getOrNull()
        if (payloadJson == null) {
            respond("false")
            return
        }
        val popup = _uiState.value.popup
        viewModelScope.launch {
            val result = ankiRepository.mineEntry(
                rawPayload = payloadJson,
                miningContext = AnkiMiningContext(
                    sentence = popup?.sentence.orEmpty(),
                    documentTitle = threadTitle,
                    sentenceOffset = popup?.sentenceOffset,
                ),
            )
            respond(if (result == AnkiMineResult.Added || result == AnkiMineResult.Duplicate) "true" else "false")
        }
    }

    /// popup.js sends the handlebar values it would mine with and expects
    /// `{ formatId: isDuplicate }` back.
    fun duplicateCheck(valuesJson: String, respond: (String) -> Unit) {
        val expression = runCatching {
            json.parseToJsonElement(valuesJson).jsonObject["{expression}"]?.jsonPrimitive?.contentOrNull
        }.getOrNull().orEmpty()
        if (expression.isEmpty()) {
            respond("null")
            return
        }
        viewModelScope.launch {
            val isDuplicate = ankiRepository.isDuplicate(expression)
            respond(
                buildJsonObject { put(AnkiPopupFormat.DEFAULT_ID, isDuplicate) }.toString(),
            )
        }
    }

    private var boardUrl: String? = null
    private var threadId: Long = 0
    private var maxSeenIndex = 0

    fun bind(boardUrl: String, threadId: Long) {
        if (this.boardUrl == boardUrl && this.threadId == threadId) return
        this.boardUrl = boardUrl
        this.threadId = threadId
        load(refresh = false)
    }

    fun load(refresh: Boolean = true) {
        val url = boardUrl ?: return
        val id = threadId
        viewModelScope.launch {
            val isInitialLoad = _uiState.value.posts.isEmpty()
            _uiState.update {
                it.copy(isLoading = isInitialLoad, isRefreshing = refresh, errorMessage = null)
            }
            runCatching { postService.fetchPosts(url, id) }
                .onSuccess { posts ->
                    val (idIndices, tripIndices) = buildHighlightIndices(posts)
                    // Only resume if there is unread content below the saved
                    // position — if the last visit already reached the end,
                    // scrolling the final row to the top has nothing to anchor
                    // against.
                    val resume = if (isInitialLoad) {
                        maxSeenIndex = 0
                        readStateStore.state(ReadStateStore.key(url, id))
                            ?.takeIf { posts.size > 1 && it.lastReadIndex < posts.size - 1 }
                            ?.let { max(0, it.lastReadIndex) }
                    } else {
                        null
                    }
                    _uiState.value = ThreadUiState(
                        posts = posts,
                        isLoading = false,
                        idIndices = idIndices,
                        tripIndices = tripIndices,
                        resumeIndex = resume,
                    )
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, isRefreshing = false, errorMessage = error.toString())
                    }
                }
        }
    }

    fun onResumeHandled() {
        _uiState.update { it.copy(resumeIndex = null) }
    }

    fun onPostSeen(index: Int) {
        maxSeenIndex = max(maxSeenIndex, index)
    }

    fun toggleHighlightedId(id: String) {
        _uiState.update {
            it.copy(highlightedId = if (it.highlightedId == id) null else id, highlightedTrip = null)
        }
    }

    fun toggleHighlightedTrip(trip: String) {
        _uiState.update {
            it.copy(highlightedTrip = if (it.highlightedTrip == trip) null else trip, highlightedId = null)
        }
    }

    fun clearHighlight() {
        _uiState.update { it.copy(highlightedId = null, highlightedTrip = null) }
    }

    fun addNgRule(kind: NgKind, pattern: String) = ngFilterStore.add(kind, pattern)

    /// Runs a dictionary lookup for a tapped word and opens the pop-up.
    /// Mirrors `ThreadView.handleTextSelection` on iOS: nothing found means
    /// the pop-up closes rather than showing an empty card.
    fun onWordTap(postIndex: Int, sourceText: String, offset: Int, anchor: Rect) {
        val config = userConfigRepository.config.value
        val selection = TextScanner.select(
            source = sourceText,
            offset = offset,
            maxLength = config.scanLength,
            scanNonJapaneseText = config.scanNonJapaneseText,
        )
        if (selection == null) {
            dismissPopup()
            return
        }

        viewModelScope.launch {
            val results = runCatching {
                dictionaryRepository.lookup(
                    text = selection.text,
                    maxResults = config.maxResults,
                    scanLength = config.scanLength,
                )
            }.getOrDefault(emptyList())

            val first = results.firstOrNull()
            if (first == null) {
                dismissPopup()
                return@launch
            }

            val styles = runCatching { dictionaryRepository.dictionaryStyles() }.getOrDefault(emptyMap())
            popupGeneration += 1
            _uiState.update {
                it.copy(
                    popup = LookupPopupState(
                        anchor = anchor,
                        results = results,
                        dictionaryStyles = styles,
                        sourceText = selection.text,
                        sentence = selection.sentence,
                        sentenceOffset = selection.sentence.indexOf(first.matched).takeIf { it >= 0 } ?: 0,
                        generation = popupGeneration,
                    ),
                    popupPostIndex = postIndex,
                    popupHighlight = offset until (offset + first.matched.length)
                        .coerceAtMost(sourceText.length),
                )
            }
        }
    }

    fun lookupRedirect(query: String) = dictionaryRepository.lookup(
        text = query,
        maxResults = userConfigRepository.config.value.maxResults,
        scanLength = userConfigRepository.config.value.scanLength,
    )

    fun dictionaryMedia(dictionary: String, path: String): ByteArray? =
        dictionaryRepository.dictionaryMedia(dictionary, path)

    fun dismissPopup() {
        if (_uiState.value.popup == null) return
        _uiState.update { it.copy(popup = null, popupPostIndex = null, popupHighlight = null) }
    }

    fun persistReadState() {
        val url = boardUrl ?: return
        val posts = _uiState.value.posts
        if (posts.isEmpty()) return
        readStateStore.update(
            key = ReadStateStore.key(url, threadId),
            postCount = posts.size,
            readIndex = maxSeenIndex,
        )
    }

    override fun onCleared() {
        persistReadState()
        super.onCleared()
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun buildHighlightIndices(posts: List<Post>): Pair<Map<String, List<Int>>, Map<String, List<Int>>> {
        val byId = HashMap<String, MutableList<Int>>()
        val byTrip = HashMap<String, MutableList<Int>>()
        posts.forEachIndexed { index, post ->
            post.id?.let { byId.getOrPut(it) { mutableListOf() }.add(index) }
            post.trip?.let { byTrip.getOrPut(it) { mutableListOf() }.add(index) }
        }
        return byId to byTrip
    }
}
