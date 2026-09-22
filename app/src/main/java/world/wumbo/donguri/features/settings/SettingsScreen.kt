package world.wumbo.donguri.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import world.wumbo.donguri.R
import world.wumbo.donguri.navigation.SettingsSection

private data class SettingsEntry(
    val section: SettingsSection,
    val icon: ImageVector,
    val labelRes: Int,
)

private val settingsEntries = listOf(
    SettingsEntry(SettingsSection.Dictionaries, Icons.Default.MenuBook, R.string.settings_dictionaries),
    SettingsEntry(SettingsSection.Anki, Icons.Default.Style, R.string.settings_anki),
    SettingsEntry(SettingsSection.Popup, Icons.Default.PictureInPicture, R.string.settings_popup),
    SettingsEntry(SettingsSection.Audio, Icons.Default.VolumeUp, R.string.settings_audio),
    SettingsEntry(SettingsSection.NgFilter, Icons.Default.Block, R.string.settings_ng_filter),
    SettingsEntry(SettingsSection.CustomCss, Icons.Default.Code, R.string.settings_custom_css),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenSection: (SettingsSection) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
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
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(settingsEntries, key = { it.section.name }) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSection(entry.section) }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(
                        entry.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = stringResource(entry.labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}
