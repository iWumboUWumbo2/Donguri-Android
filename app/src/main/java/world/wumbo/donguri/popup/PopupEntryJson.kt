//
//  PopupEntryJson.kt
//  Donguri
//
//  Serialises a hoshidicts LookupResult into the entry shape popup.js renders.
//  Ported from Hoshi Reader Android's LookupPopupHtml.
//  Copyright © 2026 HuangAntimony.
//  SPDX-License-Identifier: GPL-3.0-or-later
//
package world.wumbo.donguri.popup

import de.manhhao.hoshi.LookupResult
import de.manhhao.hoshi.TraceCandidate
import de.manhhao.hoshi.TraceSource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

fun LookupResult.toEntryJsonString(): String = toEntryJson().toString()

internal fun LookupResult.toEntryJson(): JsonObject = buildJsonObject {
    put("expression", term.expression)
    put("reading", term.reading)
    put("matched", matched)
    putJsonArray("deinflectionTraceRows") {
        traceCandidates.sortedForDisplay().forEach { candidate ->
            add(
                buildJsonArray {
                    candidate.trace.reversedArray().forEach { transformGroup ->
                        add(
                            buildJsonObject {
                                put("name", transformGroup.name)
                                put("description", transformGroup.description)
                            },
                        )
                    }
                },
            )
        }
    }
    putJsonArray("glossaries") {
        term.glossaries.forEach { glossary ->
            add(
                buildJsonObject {
                    put("dictionary", glossary.dictName)
                    put("content", glossary.glossary)
                    put("definitionTags", glossary.definitionTags)
                    put("termTags", glossary.termTags)
                },
            )
        }
    }
    putJsonArray("frequencies") {
        term.frequencies.forEach { frequency ->
            add(
                buildJsonObject {
                    put("dictionary", frequency.dictName)
                    putJsonArray("frequencies") {
                        frequency.frequencies.forEach { tag ->
                            add(
                                buildJsonObject {
                                    put("value", tag.value)
                                    put("displayValue", tag.displayValue)
                                },
                            )
                        }
                    }
                },
            )
        }
    }
    putJsonArray("pitches") {
        term.pitches.forEach { pitch ->
            add(
                buildJsonObject {
                    put("dictionary", pitch.dictName)
                    putJsonArray("pitches") {
                        pitch.pitches
                            .distinctBy { accent ->
                                accent.pattern.takeIf(String::isNotBlank) ?: accent.position.toString()
                            }
                            .forEach { accent ->
                                add(
                                    buildJsonObject {
                                        if (accent.pattern.isNotBlank()) {
                                            put("position", accent.pattern)
                                        } else {
                                            put("position", accent.position)
                                        }
                                        putJsonArray("nasal") { accent.nasal.forEach { add(JsonPrimitive(it)) } }
                                        putJsonArray("devoice") { accent.devoice.forEach { add(JsonPrimitive(it)) } }
                                    },
                                )
                            }
                    }
                    putJsonArray("transcriptions") {
                        pitch.transcriptions.distinct().forEach { add(JsonPrimitive(it)) }
                    }
                },
            )
        }
    }
    putJsonArray("rules") {
        term.rules.splitToSequence(' ')
            .filter { it.isNotBlank() }
            .forEach { add(JsonPrimitive(it)) }
    }
}

/// Dictionary-only deinflection traces are an implementation detail of the
/// engine's own lookup tables and are not shown.
private fun Array<TraceCandidate>.sortedForDisplay(): List<TraceCandidate> =
    withIndex()
        .filter { it.value.source != TraceSource.DICTIONARY }
        .sortedWith(
            compareBy<IndexedValue<TraceCandidate>> { it.value.source.displayRank() }
                .thenBy { it.index },
        )
        .map { it.value }

private fun TraceSource.displayRank(): Int = when (this) {
    TraceSource.ALGORITHM -> 0
    TraceSource.BOTH -> 1
    TraceSource.DICTIONARY -> 2
}
