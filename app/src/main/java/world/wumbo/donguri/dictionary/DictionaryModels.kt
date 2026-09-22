//
//  DictionaryModels.kt
//  Donguri
//
//  Ported from Hoshi Reader Android's dictionary models, trimmed the way
//  Donguri's iOS port trims Hoshi Reader's: Japanese only (5ch is a Japanese
//  board), and term/frequency/pitch dictionaries only.
//  Copyright © 2026 Manhhao, HuangAntimony.
//  SPDX-License-Identifier: GPL-3.0-or-later
//
package world.wumbo.donguri.dictionary

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import java.io.File
import java.util.UUID

/// Donguri drives the engine in Japanese mode only.
const val DICTIONARY_LANGUAGE_ID = "ja"

enum class DictionaryType(val directoryName: String) {
    Term("Term"),
    Frequency("Frequency"),
    Pitch("Pitch"),
}

data class DictionaryInfo(
    val id: String = UUID.randomUUID().toString(),
    val index: DictionaryIndex,
    val path: File,
    val isEnabled: Boolean = true,
    val order: Int = 0,
)

@Serializable
data class DictionaryConfig(
    val termDictionaries: List<DictionaryEntry> = emptyList(),
    val frequencyDictionaries: List<DictionaryEntry> = emptyList(),
    val pitchDictionaries: List<DictionaryEntry> = emptyList(),
) {
    @Serializable
    data class DictionaryEntry(
        val fileName: String,
        val isEnabled: Boolean,
        val order: Int,
    )
}

/// Decodes two shapes with the same fields: a Yomitan dictionary's own
/// `index.json` (which uses `format` and carries the self-update URLs), and the
/// summary hoshidicts writes back out after importing (which uses `version`).
///
/// Everything but `title` is lenient — a strict decode silently loses
/// dictionaries, because the storage layer drops any dictionary directory whose
/// index it cannot read.
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class DictionaryIndex(
    val title: String,
    @JsonNames("version")
    val format: Int = 3,
    val revision: String = "",
    val isUpdatable: Boolean = false,
    val indexUrl: String = "",
    val downloadUrl: String = "",
)

data class DictionaryUpdateCandidate(
    val dictionary: DictionaryInfo,
    val type: DictionaryType,
)

enum class DictionaryUpdateStage { Fetching, Checking, Downloading, Importing }

data class DictionaryUpdateProgress(
    val stage: DictionaryUpdateStage,
    val title: String,
)

data class DictionaryUpdateFailure(val title: String, val message: String)

data class DictionaryUpdateSummary(
    val checkedCount: Int,
    val updatedCount: Int,
    val failures: List<DictionaryUpdateFailure> = emptyList(),
)

data class ImportedDictionary(
    val fileName: String,
    val index: DictionaryIndex,
)

data class RecommendedDictionary(
    val id: String,
    val name: String,
    val type: DictionaryType,
    val indexUrl: String = "",
    val downloadUrl: String = "",
    val description: String = "",
)

/// The set Donguri's iOS build offers under "Download Recommended
/// Dictionaries": JMdict, JMnedict and the Jiten frequency list.
val RecommendedDictionaries: List<RecommendedDictionary> = listOf(
    RecommendedDictionary(
        id = "jmdict",
        name = "JMdict",
        type = DictionaryType.Term,
        indexUrl = "https://github.com/yomidevs/jmdict-yomitan/releases/latest/download/JMdict_english_without_proper_names.json",
        description = "Term",
    ),
    RecommendedDictionary(
        id = "jmnedict",
        name = "JMnedict",
        type = DictionaryType.Term,
        indexUrl = "https://github.com/yomidevs/jmdict-yomitan/releases/latest/download/JMnedict.json",
        description = "Names",
    ),
    RecommendedDictionary(
        id = "jiten",
        name = "Jiten",
        type = DictionaryType.Frequency,
        indexUrl = "https://api.jiten.moe/api/frequency-list/index",
        description = "Frequency",
    ),
    RecommendedDictionary(
        id = "jitendex",
        name = "Jitendex",
        type = DictionaryType.Term,
        indexUrl = "https://jitendex.org/static/yomitan.json",
        description = "Term",
    ),
)

@Serializable
data class AudioSource(
    val name: String = "",
    val url: String,
    val isEnabled: Boolean = true,
    val isDefault: Boolean = false,
)
