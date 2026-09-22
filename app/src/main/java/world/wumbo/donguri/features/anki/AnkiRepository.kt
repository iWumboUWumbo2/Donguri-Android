//
//  AnkiRepository.kt
//  Donguri
//
//  Turns a mining payload from the pop-up into an Anki note. Adapted from Hoshi
//  Reader Android's AnkiRepository, trimmed to Donguri's scope: a single card
//  format, word audio as the only media the app fetches itself, and the thread
//  title standing in for the book title.
//  Copyright © 2026 Manhhao, HuangAntimony.
//  SPDX-License-Identifier: GPL-3.0-or-later
//
package world.wumbo.donguri.features.anki

import android.content.Context
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import world.wumbo.donguri.dictionary.DictionaryRepository
import java.io.File
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AnkiFetchResult {
    data class Success(val decks: List<AnkiDeck>, val noteTypes: List<AnkiNoteType>) : AnkiFetchResult
    data class Error(val message: String, val failure: AnkiFetchFailure? = null) : AnkiFetchResult
}

enum class AnkiMineResult { Added, Duplicate, NotConfigured, Failed }

@Singleton
class AnkiRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ankiDroidBackend: AnkiDroidBackendAdapter,
    private val settingsRepository: AnkiSettingsRepository,
    private val dictionaryRepository: DictionaryRepository,
) {
    val settings: StateFlow<AnkiSettings> = settingsRepository.settings

    fun updateSettings(transform: (AnkiSettings) -> AnkiSettings) = settingsRepository.update(transform)

    fun isBackendAvailable(): Boolean = runCatching { activeBackend().isAvailable() }.getOrDefault(false)

    /// Reads the decks and note types out of whichever backend is selected, so
    /// the settings screen can offer them.
    suspend fun fetchConfiguration(): AnkiFetchResult = withContext(Dispatchers.IO) {
        val backend = runCatching { activeBackend() }.getOrElse { error ->
            return@withContext AnkiFetchResult.Error(error.message ?: "Unable to reach Anki")
        }
        if (!backend.isAvailable()) {
            return@withContext AnkiFetchResult.Error(
                message = "Anki is unavailable",
                failure = AnkiFetchFailure.ApiUnavailable.takeIf {
                    settings.value.backendKind == AnkiBackendKind.AnkiDroid
                },
            )
        }
        val result = runCatching { backend.fetchDecks() to backend.fetchNoteTypes() }
        val (decks, noteTypes) = result.getOrElse { error ->
            return@withContext AnkiFetchResult.Error(
                message = error.message ?: "Unable to read the Anki collection",
                failure = (error as? AnkiFetchException)?.failure,
            )
        }
        if (decks.isEmpty()) return@withContext AnkiFetchResult.Error("No decks found")
        if (noteTypes.isEmpty()) return@withContext AnkiFetchResult.Error("No note types found")

        settingsRepository.update { current ->
            // Keep the current selection if it still exists, otherwise fall
            // back to whatever the collection now offers.
            val deck = decks.firstOrNull { it.name == current.selectedDeckName } ?: decks.first()
            val noteType = noteTypes.firstOrNull { it.name == current.selectedNoteTypeName }
            current.copy(
                availableDecks = decks,
                availableNoteTypes = noteTypes,
                selectedDeckId = deck.id,
                selectedDeckName = deck.name,
                selectedNoteTypeId = noteType?.id ?: current.selectedNoteTypeId,
                selectedNoteTypeName = noteType?.name ?: current.selectedNoteTypeName,
            )
        }
        AnkiFetchResult.Success(decks, noteTypes)
    }

    suspend fun mineEntry(rawPayload: String, miningContext: AnkiMiningContext): AnkiMineResult =
        withContext(Dispatchers.IO) {
            val current = settings.value
            val deck = current.selectedDeck ?: return@withContext AnkiMineResult.NotConfigured
            val noteType = current.selectedNoteType ?: return@withContext AnkiMineResult.NotConfigured
            val payload = runCatching { AnkiMiningPayload.fromJson(rawPayload) }.getOrNull()
                ?: return@withContext AnkiMineResult.Failed

            val backend = runCatching { activeBackend() }.getOrNull()
                ?: return@withContext AnkiMineResult.Failed

            // Media has to reach the collection before the fields that
            // reference it, or the note points at files Anki does not have.
            val audioTag = payload.audio
                .takeIf { current.needsAudio && it.isNotBlank() }
                ?.let { url -> storeWordAudio(backend, url) }
                .orEmpty()
            val mediaTags = payload.dictionaryMedia.associate { media ->
                media.filename to storeDictionaryMedia(backend, media).orEmpty()
            }

            val resolvedPayload = payload.copy(audio = audioTag)
            val fields = current.fieldMappings
                .activeAnkiFieldMappings(noteType)
                .mapValues { (_, template) ->
                    AnkiHandlebarRenderer
                        .render(template, resolvedPayload, miningContext, current.selectedGlossaryFallback)
                        .withDictionaryMediaTags(mediaTags)
                }

            val tags = AnkiHandlebarRenderer
                .renderTags(current.tags, resolvedPayload, miningContext, current.selectedGlossaryFallback)
                .split(' ', '\n')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet()

            val added = runCatching {
                backend.addNote(
                    deck = deck,
                    noteType = noteType,
                    fieldsByName = fields,
                    tags = tags,
                    allowDupes = current.allowDupes,
                    duplicateScope = current.duplicateScope,
                    checkDuplicatesAcrossAllModels = current.checkDuplicatesAcrossAllModels,
                )
            }.getOrElse { return@withContext AnkiMineResult.Failed }

            if (!added) return@withContext AnkiMineResult.Duplicate
            if (current.backendKind == AnkiBackendKind.AnkiConnect && current.ankiConnectForceSync) {
                runCatching { backend.sync() }
            }
            AnkiMineResult.Added
        }

    suspend fun isDuplicate(expression: String): Boolean = withContext(Dispatchers.IO) {
        val current = settings.value
        val deck = current.selectedDeck ?: return@withContext false
        val noteType = current.selectedNoteType ?: return@withContext false
        runCatching {
            activeBackend().isDuplicate(
                deck = deck,
                noteType = noteType,
                key = expression,
                duplicateScope = current.duplicateScope,
                checkDuplicatesAcrossAllModels = current.checkDuplicatesAcrossAllModels,
            )
        }.getOrDefault(false)
    }

    /// popup.js sends the audio as a URL; it has to be downloaded and handed to
    /// the collection as a file before the note can reference it.
    private fun storeWordAudio(backend: AnkiBackend, url: String): String? {
        val bytes = runCatching { URL(url).openStream().use { it.readBytes() } }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val extension = url.substringAfterLast('.', "mp3").substringBefore('?').take(4)
        val name = "donguri_${bytes.sha1().take(16)}.$extension"
        return storeMedia(backend, bytes, name, "audio/mpeg")
    }

    private fun storeDictionaryMedia(backend: AnkiBackend, media: DictionaryMedia): String? {
        val bytes = dictionaryRepository.dictionaryMedia(media.dictionary, media.path)
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        return storeMedia(backend, bytes, media.filename, "image/png")
    }

    private fun storeMedia(
        backend: AnkiBackend,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): String? {
        backend.addMediaFromBytes(bytes, fileName, mimeType)?.let { return it }
        // AnkiDroid only takes content URIs, so the bytes go through a cache
        // file exposed by the app's FileProvider.
        val cacheDir = File(context.cacheDir, "anki").apply { mkdirs() }
        val file = File(cacheDir, fileName)
        return runCatching {
            file.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.grantUriPermission("com.ichi2.anki", uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            backend.addMediaFromUri(uri.toString(), fileName, mimeType)
        }.getOrNull()
    }

    private fun activeBackend(): AnkiBackend {
        val current = settings.value
        return when (current.backendKind) {
            AnkiBackendKind.AnkiDroid -> ankiDroidBackend
            AnkiBackendKind.AnkiConnect -> AnkiConnectBackend(
                endpoint = current.ankiConnectUrl,
                apiKey = current.ankiConnectApiKey,
            )
        }
    }
}

/// popup.js writes `<img src="filename">` for dictionary images; the tag the
/// backend returns is what the collection actually knows the file as.
private fun String.withDictionaryMediaTags(mediaTags: Map<String, String>): String {
    if (mediaTags.isEmpty()) return this
    var result = this
    mediaTags.forEach { (filename, tag) ->
        if (tag.isNotBlank() && tag != filename) result = result.replace(filename, tag)
    }
    return result
}

private fun ByteArray.sha1(): String =
    MessageDigest.getInstance("SHA-1").digest(this).joinToString("") { "%02x".format(it) }

/// The slice of the Anki settings popup.js needs in order to draw (and enable)
/// its mining button.
fun AnkiSettings.toPopupSettings(isBackendAvailable: Boolean): world.wumbo.donguri.popup.AnkiPopupSettings =
    world.wumbo.donguri.popup.AnkiPopupSettings(
        isBackendAvailable = isBackendAvailable,
        needsAudio = needsAudio,
        allowDupes = allowDupes,
        useAnkiConnect = backendKind == AnkiBackendKind.AnkiConnect,
        compactGlossaries = compactGlossaries,
        disableShowNotes = disableShowNotes,
        formats = if (isConfigured) {
            listOf(
                world.wumbo.donguri.popup.AnkiPopupFormat(
                    id = world.wumbo.donguri.popup.AnkiPopupFormat.DEFAULT_ID,
                    isValid = true,
                ),
            )
        } else {
            emptyList()
        },
    )
