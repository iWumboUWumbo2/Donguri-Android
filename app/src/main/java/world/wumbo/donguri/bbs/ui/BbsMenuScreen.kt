package world.wumbo.donguri.bbs.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import world.wumbo.donguri.R
import world.wumbo.donguri.bbs.model.Board
import world.wumbo.donguri.bbs.model.MenuCategory
import world.wumbo.donguri.ui.Chip
import world.wumbo.donguri.ui.ErrorState
import world.wumbo.donguri.ui.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BbsMenuScreen(
    onOpenBoard: (Board) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDictionarySearch: () -> Unit,
    viewModel: BbsMenuViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.five_channel)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDictionarySearch) {
                        Icon(Icons.Default.MenuBook, contentDescription = stringResource(R.string.dictionary))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (filter.isNotEmpty()) {
                        IconButton(onClick = { filter = "" }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear))
                        }
                    }
                },
                placeholder = { Text(stringResource(R.string.filter)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )

            val menu = uiState.menu
            when {
                menu != null -> MenuList(
                    categories = menu.menuList,
                    filter = filter,
                    onOpenBoard = onOpenBoard,
                )
                uiState.errorMessage != null -> ErrorState(
                    message = uiState.errorMessage!!,
                    onRetry = viewModel::load,
                )
                else -> LoadingState()
            }
        }
    }
}

@Composable
private fun MenuList(
    categories: List<MenuCategory>,
    filter: String,
    onOpenBoard: (Board) -> Unit,
) {
    // A filter matches boards, not categories: a category survives only if one
    // of its boards does, and then shows just the boards that matched.
    val visible = remember(categories, filter) {
        if (filter.isBlank()) {
            categories.map { it to it.categoryContent }
        } else {
            categories.mapNotNull { category ->
                val boards = category.categoryContent.filter {
                    it.boardName.contains(filter, ignoreCase = true)
                }
                if (boards.isEmpty()) null else category to boards
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(visible, key = { it.first.categoryNumber }) { (category, boards) ->
            CategorySection(
                category = category,
                boards = boards,
                // A filter narrow enough to be worth typing is worth expanding for.
                initiallyExpanded = filter.isNotBlank(),
                onOpenBoard = onOpenBoard,
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun CategorySection(
    category: MenuCategory,
    boards: List<Board>,
    initiallyExpanded: Boolean,
    onOpenBoard: (Board) -> Unit,
) {
    var expanded by rememberSaveable(category.categoryNumber, initiallyExpanded) {
        mutableStateOf(initiallyExpanded)
    }

    Column(Modifier.animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = category.categoryName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Chip(text = boards.size.toString(), filled = false)
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
            )
        }

        if (expanded) {
            for (board in boards) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenBoard(board) }
                        .padding(start = 28.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Default.Forum,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(board.boardName, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}
