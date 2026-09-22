package world.wumbo.donguri.features.dictionary

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import world.wumbo.donguri.R
import world.wumbo.donguri.dictionary.DictionaryInfo
import world.wumbo.donguri.dictionary.DictionaryType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(
    onBack: () -> Unit,
    viewModel: DictionaryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importDictionary) }

    LaunchedEffect(uiState.message, uiState.errorMessage) {
        val text = uiState.errorMessage ?: uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_dictionaries)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        enabled = !uiState.isBusy,
                        onClick = { viewModel.updateDictionaries() },
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.dictionary_update))
                    }
                    IconButton(
                        enabled = !uiState.isBusy,
                        // Yomitan archives are .zip; some pickers report them
                        // as octet-stream, so both are accepted.
                        onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.dictionary_import))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    TextButton(
                        enabled = !uiState.isBusy,
                        onClick = { viewModel.downloadRecommended() },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Text(
                            text = stringResource(R.string.dictionary_download_recommended),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    HorizontalDivider()
                }

                DictionaryType.entries.forEach { type ->
                    val dictionaries = uiState.dictionariesByType[type].orEmpty()
                    item(key = "header-${type.name}") {
                        Text(
                            text = stringResource(type.titleRes()),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                    if (dictionaries.isEmpty()) {
                        item(key = "empty-${type.name}") {
                            Text(
                                text = stringResource(R.string.dictionary_none),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    } else {
                        items(dictionaries, key = { "${type.name}-${it.path.name}" }) { dictionary ->
                            val index = dictionaries.indexOf(dictionary)
                            DictionaryRow(
                                dictionary = dictionary,
                                canMoveUp = index > 0,
                                canMoveDown = index < dictionaries.lastIndex,
                                onToggle = { enabled ->
                                    viewModel.setEnabled(type, dictionary.path.name, enabled)
                                },
                                onMoveUp = { viewModel.move(type, index, index - 1) },
                                onMoveDown = { viewModel.move(type, index, index + 1) },
                                onDelete = { viewModel.delete(type, dictionary.path.name) },
                            )
                        }
                    }
                    item(key = "divider-${type.name}") { HorizontalDivider() }
                }
            }

            if (uiState.isBusy) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    uiState.busyMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun DictionaryRow(
    dictionary: DictionaryInfo,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(dictionary.index.title, style = MaterialTheme.typography.bodyMedium)
            if (dictionary.index.revision.isNotBlank()) {
                Text(
                    text = dictionary.index.revision,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.dictionary_move_up))
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.dictionary_move_down))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.dictionary_delete))
        }
        Switch(checked = dictionary.isEnabled, onCheckedChange = onToggle)
    }
}

private fun DictionaryType.titleRes(): Int = when (this) {
    DictionaryType.Term -> R.string.dictionary_type_term
    DictionaryType.Frequency -> R.string.dictionary_type_frequency
    DictionaryType.Pitch -> R.string.dictionary_type_pitch
}
