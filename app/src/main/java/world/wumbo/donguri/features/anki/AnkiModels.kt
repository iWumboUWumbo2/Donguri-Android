//
//  AnkiModels.kt
//  Donguri
//
//  Anki settings, the mining payload popup.js sends, and the handlebar
//  renderer that turns it into note fields. Ported from Hoshi Reader Android,
//  trimmed the way Donguri's iOS port is: one card format rather than several,
//  no dictionary categories, no book cover and no Sasayaki audio.
//  Copyright © 2026 Manhhao, HuangAntimony.
//  SPDX-License-Identifier: GPL-3.0-or-later
//
package world.wumbo.donguri.features.anki

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class AnkiDeck(val id: Long, val name: String)

@Serializable
data class AnkiNoteType(val id: Long, val name: String, val fields: List<String>)

@Serializable
enum class AnkiBackendKind { AnkiDroid, AnkiConnect }

@Serializable
enum class AnkiDuplicateScope { Collection, Deck, DeckRoot }

@Serializable
data class AnkiSettings(
    val backendKind: AnkiBackendKind = AnkiBackendKind.AnkiDroid,
    val selectedDeckId: Long? = null,
    val selectedDeckName: String? = null,
    val selectedNoteTypeId: Long? = null,
    val selectedNoteTypeName: String? = null,
    val availableDecks: List<AnkiDeck> = emptyList(),
    val availableNoteTypes: List<AnkiNoteType> = emptyList(),
    val fieldMappings: Map<String, String> = emptyMap(),
    val tags: String = DEFAULT_TAG,
    val allowDupes: Boolean = false,
    val checkDuplicatesAcrossAllModels: Boolean = false,
    val duplicateScope: AnkiDuplicateScope = AnkiDuplicateScope.Collection,
    val compactGlossaries: Boolean = false,
    val disableShowNotes: Boolean = false,
    val selectedGlossaryFallback: String = "",
    val ankiConnectUrl: String = "",
    val ankiConnectApiKey: String = "",
    val ankiConnectForceSync: Boolean = false,
) {
    val selectedDeck: AnkiDeck?
        get() = selectedDeckId?.let { id -> availableDecks.firstOrNull { it.id == id } }
            ?: selectedDeckName?.let { name -> availableDecks.firstOrNull { it.name == name } }

    val selectedNoteType: AnkiNoteType?
        get() = selectedNoteTypeId?.let { id -> availableNoteTypes.firstOrNull { it.id == id } }
            ?: selectedNoteTypeName?.let { name -> availableNoteTypes.firstOrNull { it.name == name } }

    val isConfigured: Boolean
        get() = selectedDeck != null && selectedNoteType != null && fieldMappings.values.any { it.isNotBlank() }

    /// The card needs a word audio file only if some field actually asks for one.
    val needsAudio: Boolean
        get() = fieldMappings.values.any { "{audio}" in it }

    companion object {
        const val DEFAULT_TAG = "donguri"
    }
}

@Serializable
data class DictionaryMedia(val dictionary: String, val path: String, val filename: String)

/// What popup.js hands over when the mining button is pressed. Every field is
/// optional: which of them are filled depends on the entry and the settings.
@Serializable
data class AnkiMiningPayload(
    val expression: String = "",
    val reading: String = "",
    val matched: String = "",
    val furiganaPlain: String = "",
    val frequenciesHtml: String = "",
    val freqHarmonicRank: String = "",
    val glossary: String = "",
    val glossaryFirst: String = "",
    val singleGlossaries: Map<String, String> = emptyMap(),
    val pitchPositions: String = "",
    val pitchCategories: String = "",
    val pitchAccentGraphs: String = "",
    val phoneticTranscriptions: String = "",
    val popupSelectionText: String = "",
    val audio: String = "",
    val selectedDictionary: String = "",
    val dictionaryMedia: List<DictionaryMedia> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /// popup.js sends the nested collections as JSON *strings*, not nested
        /// objects, so they are decoded in a second pass.
        fun fromJson(rawJson: String): AnkiMiningPayload {
            val root = json.parseToJsonElement(rawJson).jsonObject
            val singleGlossaries = root.string("singleGlossaries")
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() }
                .orEmpty()
            val dictionaryMedia = root.string("dictionaryMedia")
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { json.decodeFromString<List<DictionaryMedia>>(it) }.getOrNull() }
                .orEmpty()
            return AnkiMiningPayload(
                expression = root.string("expression"),
                reading = root.string("reading"),
                matched = root.string("matched"),
                furiganaPlain = root.string("furiganaPlain"),
                frequenciesHtml = root.string("frequenciesHtml"),
                freqHarmonicRank = root.string("freqHarmonicRank"),
                glossary = root.string("glossary"),
                glossaryFirst = root.string("glossaryFirst"),
                singleGlossaries = singleGlossaries,
                pitchPositions = root.string("pitchPositions"),
                pitchCategories = root.string("pitchCategories"),
                pitchAccentGraphs = root.string("pitchAccentGraphs"),
                phoneticTranscriptions = root.string("phoneticTranscriptions"),
                popupSelectionText = root.string("popupSelectionText"),
                audio = root.string("audio"),
                selectedDictionary = root.string("selectedDictionary"),
                dictionaryMedia = dictionaryMedia,
            )
        }

        private fun JsonObject.string(key: String): String =
            this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
    }
}

/// Where the word came from: the sentence around it and the thread it was in.
data class AnkiMiningContext(
    val sentence: String,
    val documentTitle: String? = null,
    val sentenceOffset: Int? = null,
)

object AnkiHandlebarRenderer {
    private val handlebarRegex = Regex("\\{[^}]*\\}")
    private val tagValueWhitespaceRegex = Regex("[\\p{Z}\\u0009-\\u000D\\u0085]+")
    private val glossaryHeaderRegex = Regex("""(<li data-dictionary="[^"]*">)<i>[^<]*</i> """)
    private val dictionaryLabelRegex = Regex("""<li data-dictionary="([^"]+)"><i>([^<]*)</i> """)
    private const val SINGLE_GLOSSARY_PREFIX = "{single-glossary-"
    private const val BRIEF_SUFFIX = "-brief"
    private const val NO_DICTIONARY_SUFFIX = "-no-dictionary"

    fun render(
        template: String,
        payload: AnkiMiningPayload,
        context: AnkiMiningContext,
        selectedGlossaryFallback: String = "",
    ): String = handlebarRegex.replace(template) { match ->
        handlebarToValue(match.value, payload, context, selectedGlossaryFallback)
    }

    /// Anki tags cannot contain spaces, so each rendered value collapses to a
    /// single underscore-joined token.
    fun renderTags(
        template: String,
        payload: AnkiMiningPayload,
        context: AnkiMiningContext,
        selectedGlossaryFallback: String = "",
    ): String = handlebarRegex.replace(template) { match ->
        handlebarToValue(match.value, payload, context, selectedGlossaryFallback)
            .split(tagValueWhitespaceRegex)
            .filter { it.isNotEmpty() }
            .joinToString("_")
    }

    private fun handlebarToValue(
        handlebar: String,
        payload: AnkiMiningPayload,
        context: AnkiMiningContext,
        selectedGlossaryFallback: String,
    ): String {
        if (handlebar.startsWith(SINGLE_GLOSSARY_PREFIX)) {
            return payload.singleGlossaryHandlebarValue(handlebar)
        }
        return when (handlebar) {
            "{expression}" -> payload.expression
            "{reading}" -> payload.reading
            "{furigana-plain}" -> payload.furiganaPlain
            "{audio}" -> payload.audio
            "{glossary}" -> payload.glossary
            "{glossary-brief}" -> stripGlossaryHeaders(payload.glossary)
            "{glossary-no-dictionary}" -> stripDictionaryName(payload.glossary)
            "{glossary-first}" -> payload.glossaryFirst
            "{glossary-first-brief}" -> stripGlossaryHeaders(payload.glossaryFirst)
            "{glossary-first-no-dictionary}" -> stripDictionaryName(payload.glossaryFirst)
            "{selected-glossary}" ->
                payload.selectedGlossaryOrConfiguredFallback(context, selectedGlossaryFallback)
            "{selected-glossary-fallback}" -> payload.selectedGlossaryOrFallback()
            "{selected-glossary-brief}" -> stripGlossaryHeaders(
                payload.selectedGlossaryOrConfiguredFallback(context, selectedGlossaryFallback),
            )
            "{selected-glossary-brief-fallback}" -> stripGlossaryHeaders(payload.selectedGlossaryOrFallback())
            "{selected-glossary-no-dictionary}" -> stripDictionaryName(
                payload.selectedGlossaryOrConfiguredFallback(context, selectedGlossaryFallback),
            )
            "{selected-glossary-no-dictionary-fallback}" -> stripDictionaryName(payload.selectedGlossaryOrFallback())
            "{popup-selection-text}" -> payload.popupSelectionText
            "{sentence}" -> sentenceValue(payload, context)
            "{cloze-prefix}" -> clozeParts(payload, context).prefix
            "{cloze-body}" -> clozeParts(payload, context).body
            "{cloze-suffix}" -> clozeParts(payload, context).suffix
            "{frequencies}" -> payload.frequenciesHtml
            "{frequency-harmonic-rank}" -> payload.freqHarmonicRank
            "{pitch-accent-positions}" -> payload.pitchPositions
            "{pitch-accent-categories}" -> payload.pitchCategories
            "{pitch-accent-graphs}" -> payload.pitchAccentGraphs
            "{pitch-accent-graphs-first}" -> firstPitchAccentGraph(payload.pitchAccentGraphs)
            "{phonetic-transcriptions}" -> payload.phoneticTranscriptions
            /// Donguri's "document" is the thread the post was in.
            "{document-title}" -> context.documentTitle.orEmpty()
            else -> ""
        }
    }

    private fun AnkiMiningPayload.singleGlossaryHandlebarValue(handlebar: String): String {
        val dictionary = handlebar.removePrefix(SINGLE_GLOSSARY_PREFIX).removeSuffix("}")
        return when {
            dictionary.endsWith(BRIEF_SUFFIX) ->
                stripGlossaryHeaders(singleGlossaryForDictionary(dictionary.removeSuffix(BRIEF_SUFFIX)))
            dictionary.endsWith(NO_DICTIONARY_SUFFIX) ->
                stripDictionaryName(singleGlossaryForDictionary(dictionary.removeSuffix(NO_DICTIONARY_SUFFIX)))
            else -> singleGlossaryForDictionary(dictionary)
        }
    }

    private fun AnkiMiningPayload.selectedGlossaryOrFallback(): String =
        singleGlossaryForDictionary(selectedDictionary).ifBlank { glossaryFirst }

    private fun AnkiMiningPayload.selectedGlossaryOrConfiguredFallback(
        context: AnkiMiningContext,
        selectedGlossaryFallback: String,
    ): String {
        val selected = singleGlossaryForDictionary(selectedDictionary)
        if (selected.isNotBlank()) return selected
        // A fallback that is itself a selected-glossary handlebar would recurse.
        if (selectedGlossaryFallback in SELECTED_GLOSSARY_HANDLEBARS) return ""
        return handlebarToValue(selectedGlossaryFallback, this, context, "")
    }

    private fun AnkiMiningPayload.singleGlossaryForDictionary(dictionary: String): String {
        if (dictionary.isBlank()) return ""
        singleGlossaries[dictionary]?.let { return it }
        // Dictionary titles pick up a "[revision]" suffix over time; match
        // without it so a template survives a dictionary update.
        val normalized = dictionary.normalizedDictionaryName()
        return singleGlossaries.entries
            .firstOrNull { (name, _) -> name.normalizedDictionaryName() == normalized }
            ?.value
            .orEmpty()
    }

    private fun stripGlossaryHeaders(html: String): String =
        glossaryHeaderRegex.replace(html) { it.groupValues[1] }

    private fun stripDictionaryName(html: String): String =
        dictionaryLabelRegex.replace(html) { match ->
            val dict = match.groupValues[1]
            val label = match.groupValues[2]
            val stripped = label.replace(", $dict)", ")")
            if (stripped == "($dict)") {
                """<li data-dictionary="$dict">"""
            } else {
                """<li data-dictionary="$dict"><i>$stripped</i> """
            }
        }

    private fun String.normalizedDictionaryName(): String =
        trim().replace(Regex("""\s*\[[^]]+]\s*$"""), "")

    private fun sentenceValue(payload: AnkiMiningPayload, context: AnkiMiningContext): String {
        val parts = clozeParts(payload, context)
        if (parts.body.isEmpty()) return context.sentence
        return "${parts.prefix}<b>${parts.body}</b>${parts.suffix}"
    }

    private fun clozeParts(payload: AnkiMiningPayload, context: AnkiMiningContext): ClozeParts {
        val matched = payload.matched.takeIf { it.isNotBlank() }
            ?: return ClozeParts(context.sentence, "", "")
        val offset = context.sentenceOffset
        val start = if (
            offset != null &&
            offset >= 0 &&
            offset + matched.length <= context.sentence.length &&
            context.sentence.regionMatches(offset, matched, 0, matched.length)
        ) {
            offset
        } else {
            // The same word can occur more than once; without a usable offset
            // the first occurrence is the best guess available.
            context.sentence.indexOf(matched).takeIf { it >= 0 }
                ?: return ClozeParts(context.sentence, "", "")
        }
        return ClozeParts(
            prefix = context.sentence.substring(0, start),
            body = context.sentence.substring(start, start + matched.length),
            suffix = context.sentence.substring(start + matched.length),
        )
    }

    private fun firstPitchAccentGraph(html: String): String =
        Regex("""<svg\b[\s\S]*?</svg>""").find(html)?.value.orEmpty()

    private data class ClozeParts(val prefix: String, val body: String, val suffix: String)

    private val SELECTED_GLOSSARY_HANDLEBARS = setOf(
        "{selected-glossary}",
        "{selected-glossary-brief}",
        "{selected-glossary-no-dictionary}",
        "{selected-glossary-fallback}",
        "{selected-glossary-brief-fallback}",
        "{selected-glossary-no-dictionary-fallback}",
    )
}

fun Map<String, String>.activeAnkiFieldMappings(noteType: AnkiNoteType): Map<String, String> =
    noteType.fields.mapNotNull { field -> this[field]?.let { field to it } }.toMap()
