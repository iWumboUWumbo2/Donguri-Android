package world.wumbo.donguri.features.anki

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import world.wumbo.donguri.R

/// AnkiDroid guards its database behind a custom permission; without it the
/// deck and note-type lists come back empty.
private const val ANKI_READ_WRITE_PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnkiScreen(
    onOpenAnkiConnect: () -> Unit,
    onBack: () -> Unit,
    viewModel: AnkiViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.fetchConfiguration() }

    LaunchedEffect(uiState.message, uiState.errorMessage) {
        val text = uiState.errorMessage ?: uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
    }

    LaunchedEffect(uiState.needsPermission) {
        if (uiState.needsPermission) permissionLauncher.launch(ANKI_READ_WRITE_PERMISSION)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_anki)) },
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
                        enabled = !uiState.isFetching,
                        onClick = {
                            if (settings.backendKind == AnkiBackendKind.AnkiDroid) {
                                permissionLauncher.launch(ANKI_READ_WRITE_PERMISSION)
                            } else {
                                viewModel.fetchConfiguration()
                            }
                        },
                    ) {
                        Icon(Icons.Default.Download, contentDescription = stringResource(R.string.anki_fetch))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                SectionHeader(stringResource(R.string.anki_backend))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AnkiBackendKind.entries.forEach { kind ->
                        FilterChip(
                            selected = settings.backendKind == kind,
                            onClick = { viewModel.setBackend(kind) },
                            label = {
                                Text(
                                    when (kind) {
                                        AnkiBackendKind.AnkiDroid -> stringResource(R.string.anki_backend_ankidroid)
                                        AnkiBackendKind.AnkiConnect -> stringResource(R.string.anki_backend_ankiconnect)
                                    },
                                )
                            },
                        )
                    }
                }
                if (settings.backendKind == AnkiBackendKind.AnkiConnect) {
                    TextButton(
                        onClick = onOpenAnkiConnect,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    ) { Text(stringResource(R.string.anki_connect)) }
                }
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }

            item {
                SectionHeader(stringResource(R.string.anki_deck))
                PickerRow(
                    label = settings.selectedDeckName ?: stringResource(R.string.anki_not_configured),
                    options = settings.availableDecks.map { it.name },
                    onSelect = { name ->
                        settings.availableDecks.firstOrNull { it.name == name }?.let(viewModel::selectDeck)
                    },
                )

                SectionHeader(stringResource(R.string.anki_note_type))
                PickerRow(
                    label = settings.selectedNoteTypeName ?: stringResource(R.string.anki_not_configured),
                    options = settings.availableNoteTypes.map { it.name },
                    onSelect = { name ->
                        settings.availableNoteTypes.firstOrNull { it.name == name }?.let(viewModel::selectNoteType)
                    },
                )
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }

            item {
                SectionHeader(stringResource(R.string.anki_tags))
                OutlinedTextField(
                    value = settings.tags,
                    onValueChange = viewModel::setTags,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )

                SwitchRow(
                    label = stringResource(R.string.anki_allow_duplicates),
                    checked = settings.allowDupes,
                    onCheckedChange = viewModel::setAllowDupes,
                )
                SwitchRow(
                    label = stringResource(R.string.anki_compact_glossaries),
                    checked = settings.compactGlossaries,
                    onCheckedChange = viewModel::setCompactGlossaries,
                )

                SectionHeader(stringResource(R.string.anki_duplicate_scope))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AnkiDuplicateScope.entries.forEach { scope ->
                        FilterChip(
                            selected = settings.duplicateScope == scope,
                            onClick = { viewModel.setDuplicateScope(scope) },
                            label = {
                                Text(
                                    when (scope) {
                                        AnkiDuplicateScope.Collection ->
                                            stringResource(R.string.anki_duplicate_scope_collection)
                                        AnkiDuplicateScope.Deck ->
                                            stringResource(R.string.anki_duplicate_scope_deck)
                                        AnkiDuplicateScope.DeckRoot ->
                                            stringResource(R.string.anki_duplicate_scope_deck_root)
                                    },
                                )
                            },
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }

            val noteType = settings.selectedNoteType
            if (noteType != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionHeader(stringResource(R.string.anki_fields), Modifier.weight(1f))
                        if (AnkiFieldTemplates.matches(noteType)) {
                            TextButton(onClick = viewModel::applyTemplate) {
                                Text(stringResource(R.string.anki_apply_template, noteType.name))
                            }
                        }
                    }
                }
                items(noteType.fields, key = { "field-$it" }) { field ->
                    OutlinedTextField(
                        value = settings.fieldMappings[field].orEmpty(),
                        onValueChange = { viewModel.setFieldMapping(field, it) },
                        label = { Text(field) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PickerRow(label: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = options.isNotEmpty()) { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}
