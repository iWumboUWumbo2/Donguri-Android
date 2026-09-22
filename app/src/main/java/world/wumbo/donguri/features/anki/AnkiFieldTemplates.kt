//
//  AnkiFieldTemplates.kt
//  Donguri
//
//  Default field mappings for the note types Donguri knows about, using the
//  core handlebars from Lapis. Ported from Hoshi Reader.
//  Copyright © 2026 Manhhao.
//  SPDX-License-Identifier: GPL-3.0-or-later
//
package world.wumbo.donguri.features.anki

object AnkiFieldTemplates {
    private val templates = mapOf(
        "Lapis" to mapOf(
            "Expression" to "{expression}",
            "ExpressionFurigana" to "{furigana-plain}",
            "ExpressionReading" to "{reading}",
            "ExpressionAudio" to "{audio}",
            "SelectionText" to "{popup-selection-text}",
            "MainDefinition" to "{glossary-first}",
            "Sentence" to "{sentence}",
            "Glossary" to "{glossary}",
            "PitchPosition" to "{pitch-accent-positions}",
            "PitchCategories" to "{pitch-accent-categories}",
            "Frequency" to "{frequencies}",
            "FreqSort" to "{frequency-harmonic-rank}",
            "MiscInfo" to "{document-title}",
        ),
        "Kiku" to mapOf(
            "Expression" to "{expression}",
            "ExpressionFurigana" to "{furigana-plain}",
            "ExpressionReading" to "{reading}",
            "ExpressionAudio" to "{audio}",
            "SelectionText" to "{popup-selection-text}",
            "MainDefinition" to "{glossary-first}",
            "Sentence" to "{sentence}",
            "Glossary" to "{glossary}",
            "PitchPosition" to "{pitch-accent-positions}",
            "PitchCategories" to "{pitch-accent-categories}",
            "Frequency" to "{frequencies}",
            "FreqSort" to "{frequency-harmonic-rank}",
            "MiscInfo" to "{document-title}",
        ),
        "Senren" to mapOf(
            "word" to "{expression}",
            "reading" to "{reading}",
            "sentence" to "<span class=\"group\">{cloze-prefix}<span class=\"highlight\">{cloze-body}</span>{cloze-suffix}</span>",
            "selectionText" to "{popup-selection-text}",
            "definition" to "{glossary-first}",
            "wordAudio" to "{audio}",
            "glossary" to "{glossary}",
            "pitchPositions" to "{pitch-accent-positions}",
            "pitchCategories" to "{pitch-accent-categories}",
            "frequencies" to "{frequencies}",
            "freqSort" to "{frequency-harmonic-rank}",
            "miscInfo" to "{document-title}",
        ),
    )

    fun matches(noteType: AnkiNoteType): Boolean = templates.containsKey(noteType.name)

    /// Only maps fields the note type actually has, so a customised Lapis
    /// variant does not end up with mappings for fields that do not exist.
    fun defaultMappings(noteType: AnkiNoteType): Map<String, String> {
        val template = templates[noteType.name] ?: return emptyMap()
        return noteType.fields.mapNotNull { field -> template[field]?.let { field to it } }.toMap()
    }

    /// Every handlebar a user can put in a field, for the settings screen's
    /// insert menu.
    val allHandlebars: List<String> = listOf(
        "{expression}",
        "{reading}",
        "{furigana-plain}",
        "{audio}",
        "{glossary}",
        "{glossary-brief}",
        "{glossary-no-dictionary}",
        "{glossary-first}",
        "{glossary-first-brief}",
        "{glossary-first-no-dictionary}",
        "{selected-glossary}",
        "{selected-glossary-fallback}",
        "{selected-glossary-brief}",
        "{selected-glossary-brief-fallback}",
        "{selected-glossary-no-dictionary}",
        "{selected-glossary-no-dictionary-fallback}",
        "{popup-selection-text}",
        "{sentence}",
        "{cloze-prefix}",
        "{cloze-body}",
        "{cloze-suffix}",
        "{frequencies}",
        "{frequency-harmonic-rank}",
        "{pitch-accent-positions}",
        "{pitch-accent-categories}",
        "{pitch-accent-graphs}",
        "{pitch-accent-graphs-first}",
        "{phonetic-transcriptions}",
        "{document-title}",
    )
}
