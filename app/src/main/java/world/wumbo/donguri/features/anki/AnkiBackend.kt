//
//  AnkiBackend.kt
//  Donguri
//
//  Ported from Hoshi Reader Android.
//
//  Donguri on iOS mines through AnkiMobile's x-callback-url or through
//  AnkiConnect over HTTP. Android has no x-callback-url; AnkiDroid's own
//  ContentProvider API is the local equivalent, and is what this backend uses.
//
//  Copyright © 2026 HuangAntimony.
//  SPDX-License-Identifier: GPL-3.0-or-later
//
package world.wumbo.donguri.features.anki

import androidx.annotation.StringRes
import world.wumbo.donguri.R
import javax.inject.Inject

interface AnkiBackend {
    fun isAvailable(): Boolean
    fun fetchDecks(): List<AnkiDeck>
    fun fetchNoteTypes(): List<AnkiNoteType>
    fun isDuplicate(
        deck: AnkiDeck,
        noteType: AnkiNoteType,
        key: String,
        duplicateScope: AnkiDuplicateScope,
        checkDuplicatesAcrossAllModels: Boolean,
    ): Boolean
    fun addNote(
        deck: AnkiDeck,
        noteType: AnkiNoteType,
        fieldsByName: Map<String, String>,
        tags: Set<String>,
        allowDupes: Boolean,
        duplicateScope: AnkiDuplicateScope,
        checkDuplicatesAcrossAllModels: Boolean,
    ): Boolean
    fun addMediaFromUri(uriString: String, preferredName: String, mimeType: String): String?
    fun addMediaFromBytes(bytes: ByteArray, preferredName: String, mimeType: String): String? = null
    fun openNotes(
        deck: AnkiDeck,
        noteType: AnkiNoteType,
        key: String,
        duplicateScope: AnkiDuplicateScope,
        checkDuplicatesAcrossAllModels: Boolean,
    ): Boolean = false
    fun sync(): Boolean = false
}

enum class AnkiFetchFailure(@param:StringRes val userMessageRes: Int) {
    ApiUnavailable(R.string.anki_fetch_api_unavailable),
    PermissionDenied(R.string.anki_fetch_permission_denied),
    DeckListUnavailable(R.string.anki_fetch_deck_list_unavailable),
    ModelListUnavailable(R.string.anki_fetch_model_list_unavailable),
    ModelFieldsUnavailable(R.string.anki_fetch_model_fields_unavailable),
    ProviderFailure(R.string.anki_fetch_provider_failure),
}

class AnkiFetchException(
    val failure: AnkiFetchFailure,
    override val message: String? = failure.name,
    override val cause: Throwable? = null,
) : RuntimeException(message, cause)

/// The slice of AnkiDroid's ContentProvider the backend needs, kept behind an
/// interface so the adapter can be tested without AnkiDroid installed.
interface AnkiContentApi {
    fun deckList(): Map<Long, String>
    fun modelList(): Map<Long, String>
    fun fieldList(modelId: Long): List<String>
    fun findDuplicateNotes(
        deck: AnkiDeck,
        modelId: Long,
        key: String,
        duplicateScope: AnkiDuplicateScope,
        checkAllModels: Boolean,
    ): Boolean
    fun addNote(modelId: Long, deckId: Long, fields: Array<String>, tags: Set<String>): Long?
    fun addMediaFromUri(uriString: String, preferredName: String, mimeType: String): String? = null
    fun openNotes(query: String): Boolean = false
    fun sync(): Boolean = false
    fun isAvailable(): Boolean = true
}

class AnkiDroidBackendAdapter @Inject constructor(
    private val api: AnkiContentApi,
) : AnkiBackend {
    override fun isAvailable(): Boolean = api.isAvailable()

    override fun fetchDecks(): List<AnkiDeck> =
        api.deckList().map { (id, name) -> AnkiDeck(id, name) }.sortedAnkiDecks()

    override fun fetchNoteTypes(): List<AnkiNoteType> {
        val unreadableModels = mutableListOf<String>()
        val noteTypes = api.modelList().mapNotNull { (id, name) ->
            val fields = runCatching { api.fieldList(id) }.getOrElse { error ->
                if (error is AnkiFetchException && error.failure == AnkiFetchFailure.ModelFieldsUnavailable) {
                    unreadableModels += name
                    return@mapNotNull null
                }
                throw error
            }
            if (fields.isEmpty()) {
                unreadableModels += name
                null
            } else {
                AnkiNoteType(id = id, name = name, fields = fields)
            }
        }
        if (unreadableModels.isNotEmpty()) {
            throw AnkiFetchException(
                AnkiFetchFailure.ModelFieldsUnavailable,
                "Unable to read fields for: ${unreadableModels.joinToString()}",
            )
        }
        return noteTypes
    }

    override fun isDuplicate(
        deck: AnkiDeck,
        noteType: AnkiNoteType,
        key: String,
        duplicateScope: AnkiDuplicateScope,
        checkDuplicatesAcrossAllModels: Boolean,
    ): Boolean = key.isNotBlank() && api.findDuplicateNotes(
        deck = deck,
        modelId = noteType.id,
        key = key,
        duplicateScope = duplicateScope,
        checkAllModels = checkDuplicatesAcrossAllModels,
    )

    override fun addNote(
        deck: AnkiDeck,
        noteType: AnkiNoteType,
        fieldsByName: Map<String, String>,
        tags: Set<String>,
        allowDupes: Boolean,
        duplicateScope: AnkiDuplicateScope,
        checkDuplicatesAcrossAllModels: Boolean,
    ): Boolean {
        // Anki decides duplicates on the note type's first field, whatever it
        // happens to be named.
        val duplicateKey = noteType.fields.firstOrNull()?.let(fieldsByName::get).orEmpty()
        if (
            !allowDupes &&
            isDuplicate(deck, noteType, duplicateKey, duplicateScope, checkDuplicatesAcrossAllModels)
        ) {
            return false
        }
        val fields = noteType.fields.map { fieldsByName[it].orEmpty() }.toTypedArray()
        return api.addNote(noteType.id, deck.id, fields, tags) != null
    }

    override fun addMediaFromUri(uriString: String, preferredName: String, mimeType: String): String? =
        api.addMediaFromUri(uriString, preferredName, mimeType)

    override fun openNotes(
        deck: AnkiDeck,
        noteType: AnkiNoteType,
        key: String,
        duplicateScope: AnkiDuplicateScope,
        checkDuplicatesAcrossAllModels: Boolean,
    ): Boolean = api.openNotes(
        ankiNoteBrowserQuery(deck, noteType, key, duplicateScope, checkDuplicatesAcrossAllModels),
    )

    override fun sync(): Boolean = api.sync()
}

internal fun List<AnkiDeck>.sortedAnkiDecks(): List<AnkiDeck> =
    sortedWith(
        compareBy<AnkiDeck, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
            .thenBy { it.name }
            .thenBy { it.id },
    )

internal fun ankiNoteBrowserQuery(
    deck: AnkiDeck,
    noteType: AnkiNoteType,
    key: String,
    duplicateScope: AnkiDuplicateScope,
    checkAllModels: Boolean,
): String {
    val firstField = noteType.fields.firstOrNull() ?: return ""
    val escapedKey = key.replace("\"", "")
    if (escapedKey.isBlank()) return ""
    return buildList {
        add("\"$firstField:$escapedKey\"")
        if (!checkAllModels) add("\"note:${noteType.name}\"")
        when (duplicateScope) {
            AnkiDuplicateScope.Collection -> Unit
            AnkiDuplicateScope.Deck -> add("\"deck:${deck.name}\"")
            AnkiDuplicateScope.DeckRoot -> add("\"deck:${deck.name.substringBefore("::")}\"")
        }
    }.joinToString(" ")
}
