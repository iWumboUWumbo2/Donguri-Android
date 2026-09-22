package world.wumbo.donguri.bbs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import world.wumbo.donguri.R
import world.wumbo.donguri.bbs.ReportService
import world.wumbo.donguri.bbs.store.NgFilterStore
import world.wumbo.donguri.bbs.text.resTargetOf
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import world.wumbo.donguri.popup.LookupPopup
import world.wumbo.donguri.ui.ErrorState
import world.wumbo.donguri.ui.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    boardUrl: String,
    threadId: Long,
    threadTitle: String?,
    onOpenThread: (boardUrl: String, threadId: Long) -> Unit,
    onBack: () -> Unit,
    viewModel: ThreadViewModel = hiltViewModel(),
) {
    LaunchedEffect(boardUrl, threadId) { viewModel.bind(boardUrl, threadId) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ngRules by viewModel.ngRules.collectAsStateWithLifecycle()
    val userConfig by viewModel.userConfig.collectAsStateWithLifecycle()
    val ankiPopupSettings by viewModel.ankiPopupSettings.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Reply preview: the >>N target of a tapped anchor, or every reply to a
    // post whose reply badge was tapped.
    var replyPreviewIndices by remember { mutableStateOf<List<Int>>(emptyList()) }

    DisposableEffect(Unit) {
        onDispose { viewModel.persistReadState() }
    }

    LaunchedEffect(uiState.resumeIndex) {
        val resume = uiState.resumeIndex ?: return@LaunchedEffect
        listState.scrollToItem(resume)
        viewModel.onResumeHandled()
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { index -> index?.let(viewModel::onPostSeen) }
    }

    val highlightedIndices = remember(uiState.highlightedId, uiState.highlightedTrip, uiState.idIndices, uiState.tripIndices) {
        when {
            uiState.highlightedId != null -> uiState.idIndices[uiState.highlightedId].orEmpty().toSet()
            uiState.highlightedTrip != null -> uiState.tripIndices[uiState.highlightedTrip].orEmpty().toSet()
            else -> emptySet()
        }
    }

    val title = threadTitle ?: uiState.posts.firstOrNull()?.threadTitle
    LaunchedEffect(title) { viewModel.setThreadTitle(title) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title ?: stringResource(R.string.untitled_thread),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.load() },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    uiState.posts.isNotEmpty() -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(uiState.posts) { index, post ->
                            if (NgFilterStore.hides(post, ngRules)) {
                                // Kept in place so >>N numbering stays correct.
                                AbornPlaceholder(index)
                            } else {
                                PostCard(
                                    index = index,
                                    post = post,
                                    idCount = post.id?.let { uiState.idIndices[it]?.size } ?: 0,
                                    tripCount = post.trip?.let { uiState.tripIndices[it]?.size } ?: 0,
                                    isIdHighlighted = post.id != null && post.id == uiState.highlightedId,
                                    isTripHighlighted = post.trip != null && post.trip == uiState.highlightedTrip,
                                    isRowHighlighted = index in highlightedIndices,
                                    onShowReplies = { replyPreviewIndices = it },
                                    onIdTap = viewModel::toggleHighlightedId,
                                    onTripTap = viewModel::toggleHighlightedTrip,
                                    onLinkClick = { url ->
                                        val target = resTargetOf(url)?.minus(1)
                                        when {
                                            target != null && target in uiState.posts.indices ->
                                                replyPreviewIndices = listOf(target)
                                            // A >>N pointing past the end of the
                                            // thread has nothing to preview.
                                            target != null -> Unit
                                            else -> runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                            }
                                        }
                                    },
                                    onNgId = { viewModel.addNgRule(world.wumbo.donguri.bbs.model.NgKind.ID, it) },
                                    onNgName = { viewModel.addNgRule(world.wumbo.donguri.bbs.model.NgKind.NAME, it) },
                                    onReport = {
                                        ReportService.report(
                                            context = context,
                                            post = post,
                                            index = index,
                                            boardUrl = boardUrl,
                                            threadId = threadId,
                                            threadTitle = title,
                                        )
                                    },
                                    onWordTap = { tap ->
                                        viewModel.onWordTap(index, tap.text, tap.offset, tap.charBounds)
                                    },
                                    highlightRange = uiState.popupHighlight
                                        ?.takeIf { uiState.popupPostIndex == index },
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                    uiState.errorMessage != null ->
                        ErrorState(message = uiState.errorMessage!!, onRetry = { viewModel.load() })
                    uiState.isLoading -> LoadingState()
                    else -> ErrorState(message = stringResource(R.string.no_thread_information))
                }
            }

            if (uiState.highlightedId != null || uiState.highlightedTrip != null) {
                HighlightBanner(
                    text = uiState.highlightedId?.let {
                        stringResource(R.string.posts_by_id, uiState.idIndices[it]?.size ?: 0, it)
                    } ?: stringResource(
                        R.string.posts_by_trip,
                        uiState.tripIndices[uiState.highlightedTrip]?.size ?: 0,
                        uiState.highlightedTrip.orEmpty(),
                    ),
                    onClear = viewModel::clearHighlight,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
                )
            }

            uiState.popup?.let { popup ->
                LookupPopup(
                    state = popup,
                    config = userConfig,
                    loadMedia = viewModel::dictionaryMedia,
                    onDismiss = viewModel::dismissPopup,
                    onLookupRedirect = viewModel::lookupRedirect,
                    ankiSettings = ankiPopupSettings,
                    onMineEntry = viewModel::mineEntry,
                    onDuplicateCheck = viewModel::duplicateCheck,
                )
            }

            if (replyPreviewIndices.isNotEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f))
                        .clickable { replyPreviewIndices = emptyList() },
                )
                ReplyPreviewCard(
                    indices = replyPreviewIndices,
                    posts = uiState.posts,
                    onClose = { replyPreviewIndices = emptyList() },
                    onShowReplies = { replyPreviewIndices = it },
                    onJump = { target ->
                        replyPreviewIndices = emptyList()
                        coroutineScope.launch { listState.animateScrollToItem(target) }
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 16.dp)
                        .heightIn(max = 420.dp),
                )
            }
        }
    }
}
