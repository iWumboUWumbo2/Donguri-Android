//
//  UserConfig.kt
//  Donguri
//
//  The settings the ported popup dictionary, audio and Anki code reads.
//  Trimmed from Hoshi Reader's settings the same way Donguri's iOS UserConfig
//  is — same names, same defaults — so the ported popup JavaScript sees the
//  window globals it expects.
//  Copyright © 2026 Manhhao, HuangAntimony.
//  SPDX-License-Identifier: GPL-3.0-or-later
//
package world.wumbo.donguri.config

import kotlinx.serialization.Serializable
import world.wumbo.donguri.dictionary.AudioSource

enum class DictionaryUpdateInterval(val rawValue: String, val intervalMillis: Long) {
    Daily("Daily", 24L * 60 * 60 * 1000),
    Weekly("Weekly", 7L * 24 * 60 * 60 * 1000),
    Monthly("Monthly", 30L * 24 * 60 * 60 * 1000);

    companion object {
        fun fromRawValue(value: String?): DictionaryUpdateInterval =
            entries.firstOrNull { it.rawValue == value } ?: Weekly
    }
}

enum class AudioPlaybackMode(val rawValue: String) {
    Interrupt("interrupt"),
    Duck("duck"),
    Mix("mix");

    companion object {
        fun fromRawValue(value: String?): AudioPlaybackMode =
            entries.firstOrNull { it.rawValue == value } ?: Interrupt
    }
}

enum class CollapseMode(val rawValue: String) {
    ExpandAll("Expand All"),
    CollapseAll("Collapse All"),
    Custom("Custom");

    companion object {
        fun fromRawValue(value: String?): CollapseMode =
            entries.firstOrNull { it.rawValue == value } ?: ExpandAll
    }
}

@Serializable
data class UserConfig(
    val autoUpdateDictionaries: Boolean = true,
    val dictionaryUpdateInterval: DictionaryUpdateInterval = DictionaryUpdateInterval.Weekly,
    val lastDictionaryUpdateEpochMillis: Long = 0,
    val scanNonJapaneseText: Boolean = true,
    val maxResults: Int = 16,
    val scanLength: Int = 16,
    val collapseMode: CollapseMode = CollapseMode.ExpandAll,
    val expandFirstDictionary: Boolean = false,
    val collapsedDictionaries: Set<String> = emptySet(),
    val twoColumnLayout: Boolean = false,
    val compactGlossaries: Boolean = true,
    val showExpressionTags: Boolean = false,
    val harmonicFrequency: Boolean = false,
    val deduplicatePitchAccents: Boolean = false,
    val compactPitchAccents: Boolean = true,
    val popupWidth: Int = 320,
    val popupHeight: Int = 250,
    val popupScale: Double = 1.0,
    val popupActionBar: Boolean = false,
    val popupFullWidth: Boolean = false,
    val popupSwipeToDismiss: Boolean = false,
    val popupSwipeThreshold: Int = 40,
    val audioSources: List<AudioSource> = listOf(DEFAULT_AUDIO_SOURCE),
    val audioEnableAutoplay: Boolean = false,
    val audioPlaybackMode: AudioPlaybackMode = AudioPlaybackMode.Interrupt,
    val customCss: String = "",
    val lowRamDictionaryImport: Boolean = false,
) {
    val enabledAudioSources: List<String>
        get() = audioSources.filter { it.isEnabled }.map { it.url }

    fun normalized(): UserConfig = copy(
        maxResults = maxResults.coerceIn(MIN_MAX_RESULTS, MAX_MAX_RESULTS),
        scanLength = scanLength.coerceIn(MIN_SCAN_LENGTH, MAX_SCAN_LENGTH),
        popupWidth = popupWidth.coerceIn(MIN_POPUP_WIDTH, MAX_POPUP_WIDTH),
        popupHeight = popupHeight.coerceIn(MIN_POPUP_HEIGHT, MAX_POPUP_HEIGHT),
        popupScale = popupScale.coerceIn(MIN_POPUP_SCALE, MAX_POPUP_SCALE),
    )

    companion object {
        val DEFAULT_AUDIO_SOURCE = AudioSource(
            name = "Default",
            url = "https://hoshi-reader.manhhaoo-do.workers.dev/?term={term}&reading={reading}",
            isEnabled = true,
            isDefault = true,
        )

        const val MIN_MAX_RESULTS = 1
        const val MAX_MAX_RESULTS = 50
        const val MIN_SCAN_LENGTH = 1
        const val MAX_SCAN_LENGTH = 50
        const val MIN_POPUP_WIDTH = 200
        const val MAX_POPUP_WIDTH = 800
        const val MIN_POPUP_HEIGHT = 150
        const val MAX_POPUP_HEIGHT = 900
        const val MIN_POPUP_SCALE = 0.5
        const val MAX_POPUP_SCALE = 2.0
    }
}
