package world.wumbo.donguri.features.popupsettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import world.wumbo.donguri.R
import world.wumbo.donguri.config.CollapseMode
import world.wumbo.donguri.config.DictionaryUpdateInterval
import world.wumbo.donguri.config.UserConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PopupSettingsScreen(
    onBack: () -> Unit,
    viewModel: PopupSettingsViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_popup)) },
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
            SettingsSectionHeader(stringResource(R.string.settings_popup))
            SettingsSliderRow(
                label = stringResource(R.string.popup_width),
                value = config.popupWidth,
                range = UserConfig.MIN_POPUP_WIDTH..UserConfig.MAX_POPUP_WIDTH,
            ) { value -> viewModel.update { it.copy(popupWidth = value) } }
            SettingsSliderRow(
                label = stringResource(R.string.popup_height),
                value = config.popupHeight,
                range = UserConfig.MIN_POPUP_HEIGHT..UserConfig.MAX_POPUP_HEIGHT,
            ) { value -> viewModel.update { it.copy(popupHeight = value) } }
            SettingsScaleRow(
                label = stringResource(R.string.popup_scale),
                value = config.popupScale,
                range = UserConfig.MIN_POPUP_SCALE.toFloat()..UserConfig.MAX_POPUP_SCALE.toFloat(),
            ) { value -> viewModel.update { it.copy(popupScale = value) } }
            SettingsSwitchRow(
                label = stringResource(R.string.popup_full_width),
                checked = config.popupFullWidth,
            ) { value -> viewModel.update { it.copy(popupFullWidth = value) } }
            SettingsSwitchRow(
                label = stringResource(R.string.popup_swipe_to_dismiss),
                checked = config.popupSwipeToDismiss,
            ) { value -> viewModel.update { it.copy(popupSwipeToDismiss = value) } }
            if (config.popupSwipeToDismiss) {
                SettingsSliderRow(
                    label = stringResource(R.string.popup_swipe_threshold),
                    value = config.popupSwipeThreshold,
                    range = 10..200,
                ) { value -> viewModel.update { it.copy(popupSwipeThreshold = value) } }
            }

            HorizontalDivider()
            SettingsSectionHeader(stringResource(R.string.dictionary))
            SettingsSliderRow(
                label = stringResource(R.string.popup_max_results),
                value = config.maxResults,
                range = UserConfig.MIN_MAX_RESULTS..UserConfig.MAX_MAX_RESULTS,
            ) { value -> viewModel.update { it.copy(maxResults = value) } }
            SettingsSliderRow(
                label = stringResource(R.string.popup_scan_length),
                value = config.scanLength,
                range = UserConfig.MIN_SCAN_LENGTH..UserConfig.MAX_SCAN_LENGTH,
            ) { value -> viewModel.update { it.copy(scanLength = value) } }
            SettingsSwitchRow(
                label = stringResource(R.string.popup_scan_non_japanese),
                checked = config.scanNonJapaneseText,
            ) { value -> viewModel.update { it.copy(scanNonJapaneseText = value) } }
            SettingsChoiceRow(
                label = stringResource(R.string.popup_collapse_mode),
                options = CollapseMode.entries,
                selected = config.collapseMode,
                optionLabel = { mode ->
                    stringResource(
                        when (mode) {
                            CollapseMode.ExpandAll -> R.string.popup_collapse_expand_all
                            CollapseMode.CollapseAll -> R.string.popup_collapse_collapse_all
                            CollapseMode.Custom -> R.string.popup_collapse_custom
                        },
                    )
                },
            ) { value -> viewModel.update { it.copy(collapseMode = value) } }
            SettingsSwitchRow(
                label = stringResource(R.string.popup_expand_first),
                checked = config.expandFirstDictionary,
            ) { value -> viewModel.update { it.copy(expandFirstDictionary = value) } }
            SettingsSwitchRow(
                label = stringResource(R.string.popup_two_column),
                checked = config.twoColumnLayout,
            ) { value -> viewModel.update { it.copy(twoColumnLayout = value) } }
            SettingsSwitchRow(
                label = stringResource(R.string.popup_compact_glossaries),
                checked = config.compactGlossaries,
            ) { value -> viewModel.update { it.copy(compactGlossaries = value) } }
            SettingsSwitchRow(
                label = stringResource(R.string.popup_show_expression_tags),
                checked = config.showExpressionTags,
            ) { value -> viewModel.update { it.copy(showExpressionTags = value) } }
            SettingsSwitchRow(
                label = stringResource(R.string.popup_harmonic_frequency),
                checked = config.harmonicFrequency,
            ) { value -> viewModel.update { it.copy(harmonicFrequency = value) } }
            SettingsSwitchRow(
                label = stringResource(R.string.popup_deduplicate_pitch),
                checked = config.deduplicatePitchAccents,
            ) { value -> viewModel.update { it.copy(deduplicatePitchAccents = value) } }
            SettingsSwitchRow(
                label = stringResource(R.string.popup_compact_pitch),
                checked = config.compactPitchAccents,
            ) { value -> viewModel.update { it.copy(compactPitchAccents = value) } }

            HorizontalDivider()
            SettingsSectionHeader(stringResource(R.string.settings_dictionaries))
            SettingsSwitchRow(
                label = stringResource(R.string.dictionary_auto_update),
                checked = config.autoUpdateDictionaries,
            ) { value -> viewModel.update { it.copy(autoUpdateDictionaries = value) } }
            if (config.autoUpdateDictionaries) {
                SettingsChoiceRow(
                    label = stringResource(R.string.dictionary_update_interval),
                    options = DictionaryUpdateInterval.entries,
                    selected = config.dictionaryUpdateInterval,
                    optionLabel = { it.rawValue },
                ) { value -> viewModel.update { it.copy(dictionaryUpdateInterval = value) } }
            }
            SettingsSwitchRow(
                label = stringResource(R.string.dictionary_low_ram_import),
                checked = config.lowRamDictionaryImport,
            ) { value -> viewModel.update { it.copy(lowRamDictionaryImport = value) } }
        }
    }
}
