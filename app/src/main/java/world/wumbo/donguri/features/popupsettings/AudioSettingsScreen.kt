package world.wumbo.donguri.features.popupsettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import world.wumbo.donguri.R
import world.wumbo.donguri.config.AudioPlaybackMode
import world.wumbo.donguri.dictionary.AudioSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSettingsScreen(
    onBack: () -> Unit,
    viewModel: PopupSettingsViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }
    var newUrl by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_audio)) },
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
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            SettingsSwitchRow(
                label = stringResource(R.string.audio_autoplay),
                checked = config.audioEnableAutoplay,
            ) { value -> viewModel.update { it.copy(audioEnableAutoplay = value) } }

            SettingsChoiceRow(
                label = stringResource(R.string.settings_audio),
                options = AudioPlaybackMode.entries,
                selected = config.audioPlaybackMode,
                optionLabel = { it.rawValue },
            ) { value -> viewModel.update { it.copy(audioPlaybackMode = value) } }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SettingsSectionHeader(stringResource(R.string.audio_sources))

            config.audioSources.forEach { source ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = source.name.ifBlank { source.url },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = source.url,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Switch(
                        checked = source.isEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.update { current ->
                                current.copy(
                                    audioSources = current.audioSources.map {
                                        if (it.url == source.url) it.copy(isEnabled = enabled) else it
                                    },
                                )
                            }
                        },
                    )
                    // The bundled default source cannot be removed, only disabled.
                    if (!source.isDefault) {
                        IconButton(
                            onClick = {
                                viewModel.update { current ->
                                    current.copy(audioSources = current.audioSources.filterNot { it.url == source.url })
                                }
                            },
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.dictionary_delete))
                        }
                    }
                }
            }

            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text(stringResource(R.string.audio_source_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newUrl,
                    onValueChange = { newUrl = it },
                    label = { Text(stringResource(R.string.audio_source_url)) },
                    placeholder = { Text("https://…?term={term}&reading={reading}") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    enabled = newUrl.startsWith("https://"),
                    onClick = {
                        viewModel.update { current ->
                            if (current.audioSources.any { it.url == newUrl }) {
                                current
                            } else {
                                current.copy(
                                    audioSources = current.audioSources +
                                        AudioSource(name = newName.trim(), url = newUrl.trim()),
                                )
                            }
                        }
                        newName = ""
                        newUrl = ""
                    },
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.audio_source_add))
                }
            }
        }
    }
}
