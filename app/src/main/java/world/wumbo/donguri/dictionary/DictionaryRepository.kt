//
//  DictionaryRepository.kt
//  Donguri
//
//  The single entry point to the dictionary stack: import, ordering, updates
//  and lookup. Ported from Hoshi Reader Android's DictionaryRepository, trimmed
//  to Donguri's scope (one profile, Japanese only, no kanji dictionaries) the
//  way Donguri's iOS DictionaryManager is.
//  Copyright © 2026 Manhhao, HuangAntimony.
//  SPDX-License-Identifier: GPL-3.0-or-later
//
package world.wumbo.donguri.dictionary

import android.content.ContentResolver
import android.net.Uri
import de.manhhao.hoshi.LookupResult
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryRepository @Inject constructor(
    private val storage: DictionaryStorage,
    private val importer: DictionaryImporter,
    private val lookupEngine: LookupEngine,
    private val remoteDataSource: DictionaryRemoteDataSource,
) {
    private val lookupQueryLock = Any()

    @Volatile
    private var lookupQueryReady = false

    fun loadDictionaries(type: DictionaryType): List<DictionaryInfo> = storage.loadDictionaries(type)

    fun updatableDictionaries(): List<DictionaryUpdateCandidate> = storage.updatableDictionaries()

    fun importDictionary(contentResolver: ContentResolver, uri: Uri, lowRamImport: Boolean = false): Int {
        val imported = importer.importFromUri(
            contentResolver = contentResolver,
            uri = uri,
            importRootDirectory = storage.importRootDirectory(),
            typeDirectories = typeDirectories(),
            lowRamImport = lowRamImport,
        ).values.sumOf { it.size }
        if (imported > 0) {
            storage.saveConfigFromStorage()
            rebuildLookupQuery()
        }
        return imported
    }

    fun importDictionary(input: InputStream, lowRamImport: Boolean = false): Int {
        val imported = importer.importByDetectedTypes(
            input = input,
            importRootDirectory = storage.importRootDirectory(),
            typeDirectories = typeDirectories(),
            lowRamImport = lowRamImport,
        ).values.sumOf { it.size }
        if (imported > 0) {
            storage.saveConfigFromStorage()
            rebuildLookupQuery()
        }
        return imported
    }

    fun setDictionaryEnabled(type: DictionaryType, fileName: String, enabled: Boolean) {
        storage.setEnabled(type, fileName, enabled)
        rebuildLookupQuery()
    }

    fun deleteDictionary(type: DictionaryType, fileName: String) {
        storage.deleteDictionary(type, fileName)
        rebuildLookupQuery()
    }

    fun moveDictionary(type: DictionaryType, fromIndex: Int, toIndex: Int) {
        storage.move(type, fromIndex, toIndex)
        rebuildLookupQuery()
    }

    /// Checks every self-updatable dictionary's index for a newer revision and
    /// re-imports the ones that have moved on.
    fun updateDictionaries(
        lowRamImport: Boolean = false,
        onProgress: (DictionaryUpdateProgress) -> Unit = {},
    ): DictionaryUpdateSummary {
        val candidates = updatableDictionaries()
        var updatedCount = 0
        val failures = mutableListOf<DictionaryUpdateFailure>()

        candidates.forEach { candidate ->
            val installed = candidate.dictionary
            val installedIndex = installed.index
            try {
                onProgress(DictionaryUpdateProgress(DictionaryUpdateStage.Checking, installedIndex.title))
                val remoteIndex = remoteDataSource.fetchIndex(installedIndex.indexUrl)
                if (remoteIndex.revision == installedIndex.revision) return@forEach

                onProgress(DictionaryUpdateProgress(DictionaryUpdateStage.Downloading, remoteIndex.title))
                val imported = remoteDataSource.downloadArchive(remoteIndex.downloadUrl).use { input ->
                    onProgress(DictionaryUpdateProgress(DictionaryUpdateStage.Importing, remoteIndex.title))
                    importer.importByDetectedTypes(
                        input = input,
                        importRootDirectory = storage.importRootDirectory(),
                        typeDirectories = typeDirectories(),
                        lowRamImport = lowRamImport,
                    )
                }
                val replacement = imported[candidate.type]?.firstOrNull()
                if (replacement == null) {
                    failures += DictionaryUpdateFailure(installedIndex.title, "Import failed")
                    return@forEach
                }
                // A new revision can arrive under a different directory name;
                // the old one would otherwise stay behind as a duplicate.
                if (replacement.fileName != installed.path.name) {
                    storage.deleteDictionary(candidate.type, installed.path.name)
                }
                storage.saveConfigFromStorage()
                updatedCount += 1
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                failures += DictionaryUpdateFailure(
                    title = installedIndex.title,
                    message = error.localizedMessage ?: error::class.java.simpleName,
                )
            }
        }

        if (updatedCount > 0) rebuildLookupQuery()
        return DictionaryUpdateSummary(
            checkedCount = candidates.size,
            updatedCount = updatedCount,
            failures = failures,
        )
    }

    fun importRecommendedDictionaries(
        dictionaries: List<RecommendedDictionary>,
        lowRamImport: Boolean = false,
        onProgress: (DictionaryUpdateProgress) -> Unit = {},
    ): Int {
        var importedCount = 0
        dictionaries.forEach { dictionary ->
            val remoteIndex = if (dictionary.downloadUrl.isBlank()) {
                onProgress(DictionaryUpdateProgress(DictionaryUpdateStage.Fetching, dictionary.name))
                remoteDataSource.fetchIndex(dictionary.indexUrl)
            } else {
                DictionaryIndex(title = dictionary.name, downloadUrl = dictionary.downloadUrl)
            }
            if (storage.hasDictionaryWithTitle(dictionary.type, remoteIndex.title)) return@forEach

            onProgress(DictionaryUpdateProgress(DictionaryUpdateStage.Downloading, remoteIndex.title))
            val imported = remoteDataSource.downloadArchive(remoteIndex.downloadUrl).use { input ->
                onProgress(DictionaryUpdateProgress(DictionaryUpdateStage.Importing, remoteIndex.title))
                importer.importByDetectedTypes(
                    input = input,
                    importRootDirectory = storage.importRootDirectory(),
                    typeDirectories = typeDirectories(),
                    lowRamImport = lowRamImport,
                )
            }.values.sumOf { it.size }

            importedCount += imported
            if (imported > 0) storage.saveConfigFromStorage()
        }
        if (importedCount > 0) rebuildLookupQuery()
        return importedCount
    }

    fun rebuildLookupQuery() {
        synchronized(lookupQueryLock) { rebuildLookupQueryLocked() }
    }

    /// Builds the lookup session on first use. The app builds it at launch so a
    /// tap on a post never waits for it, but a lookup that somehow arrives
    /// first must not silently return nothing.
    fun ensureLookupQueryReady() {
        if (lookupQueryReady) return
        synchronized(lookupQueryLock) {
            if (!lookupQueryReady) rebuildLookupQueryLocked()
        }
    }

    fun lookup(
        text: String,
        maxResults: Int = 16,
        scanLength: Int = 16,
    ): List<LookupResult> {
        ensureLookupQueryReady()
        return lookupEngine.lookup(text, maxResults, scanLength)
    }

    fun dictionaryStyles(): Map<String, String> {
        ensureLookupQueryReady()
        return lookupEngine.getStyles().associate { it.dictName to it.styles }
    }

    fun dictionaryMedia(dictionary: String, path: String): ByteArray? {
        ensureLookupQueryReady()
        return lookupEngine.getMediaFile(dictionary, path)
    }

    private fun rebuildLookupQueryLocked() {
        lookupEngine.rebuild(
            termDictionaries = storage.enabledDictionaryPaths(DictionaryType.Term),
            frequencyDictionaries = storage.enabledDictionaryPaths(DictionaryType.Frequency),
            pitchDictionaries = storage.enabledDictionaryPaths(DictionaryType.Pitch),
        )
        lookupQueryReady = true
    }

    private fun typeDirectories(): Map<DictionaryType, File> =
        DictionaryType.entries.associateWith { storage.typeDirectory(it) }
}
