package world.wumbo.donguri.bbs.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import world.wumbo.donguri.R
import world.wumbo.donguri.bbs.model.BbsThread
import world.wumbo.donguri.bbs.store.ReadStateStore
import world.wumbo.donguri.ui.Chip
import world.wumbo.donguri.ui.ErrorState
import world.wumbo.donguri.ui.LoadingState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    boardName: String,
    boardUrl: String,
    onOpenThread: (BbsThread) -> Unit,
    onBack: () -> Unit,
    viewModel: BoardViewModel = hiltViewModel(),
) {
    LaunchedEffect(boardUrl) { viewModel.bind(boardUrl) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val readStates by viewModel.readStates.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(boardName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.load() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                uiState.threads.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                    items(uiState.threads, key = { it.id }) { thread ->
                        val unread = readStates[ReadStateStore.key(boardUrl, thread.id)]
                            ?.let { (thread.responseCount - it.lastReadCount).takeIf { n -> n > 0 } }
                        ThreadRow(
                            thread = thread,
                            unreadCount = unread,
                            onClick = { onOpenThread(thread) },
                        )
                        HorizontalDivider()
                    }
                }
                uiState.errorMessage != null ->
                    ErrorState(message = uiState.errorMessage!!, onRetry = { viewModel.load() })
                uiState.isLoading -> LoadingState()
                else -> ErrorState(message = stringResource(R.string.no_board_information))
            }
        }
    }
}

private val threadDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT)

@Composable
private fun ThreadRow(thread: BbsThread, unreadCount: Int?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = thread.title ?: stringResource(R.string.untitled_thread),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // A thread's dat number is its creation time as a unix timestamp.
                Text(
                    text = threadDateFormatter.format(
                        Instant.ofEpochSecond(thread.id).atZone(ZoneId.systemDefault())
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Chip(text = thread.responseCount.toString(), icon = Icons.AutoMirrored.Filled.Message)
            if (unreadCount != null) {
                Chip(
                    text = stringResource(R.string.unread_count, unreadCount),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
