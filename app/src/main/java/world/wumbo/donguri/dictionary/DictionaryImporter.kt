//
//  DictionaryImporter.kt
//  Donguri
//
//  Unpacks a Yomitan dictionary .zip through hoshidicts and files the result
//  under the right type directory. Ported from Hoshi Reader Android's
//  DictionaryImportDataSource, minus kanji dictionaries.
//
//  Imports stage into a temporary directory and are only moved into place once
//  the native importer has succeeded, so a failed or cancelled import can never
//  leave a half-written dictionary where the lookup engine will find it.
//
//  Copyright © 2026 HuangAntimony.
//  SPDX-License-Identifier: GPL-3.0-or-later
//
package world.wumbo.donguri.dictionary

import android.content.ContentResolver
import android.net.Uri
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryImporter @Inject constructor(
    private val nativeBridge: DictionaryNativeBridge,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun importFromUri(
        contentResolver: ContentResolver,
        uri: Uri,
        importRootDirectory: File,
        typeDirectories: Map<DictionaryType, File>,
        lowRamImport: Boolean = false,
    ): Map<DictionaryType, List<ImportedDictionary>> =
        contentResolver.openInputStream(uri).use { input ->
            if (input == null) throw DictionaryImportException(DictionaryImportFailureKind.FileAccess)
            importByDetectedTypes(input, importRootDirectory, typeDirectories, lowRamImport)
        }

    /// A Yomitan archive does not say what kind of dictionary it is; the
    /// importer reports how many term/frequency/pitch records it found, and
    /// that is what decides where the result is filed.
    fun importByDetectedTypes(
        input: InputStream,
        importRootDirectory: File,
        typeDirectories: Map<DictionaryType, File>,
        lowRamImport: Boolean = false,
    ): Map<DictionaryType, List<ImportedDictionary>> {
        importRootDirectory.mkdirs()
        val importId = UUID.randomUUID()
        val tempZip = importRootDirectory.resolve(".dictionary-import-$importId.zip")
        val stagingRoot = importRootDirectory.resolve(".dictionary-import-$importId")
        try {
            input.use { source -> tempZip.outputStream().use { source.copyTo(it) } }
            stagingRoot.mkdirs()

            val result = nativeBridge.importDictionary(tempZip.absolutePath, stagingRoot.absolutePath, lowRamImport)
            if (!result.success) throw DictionaryImportException(DictionaryImportFailureKind.Native)

            val targetTypes = result.detectedTypes()
            if (targetTypes.isEmpty()) throw DictionaryImportException(DictionaryImportFailureKind.UnsupportedContents)

            return commitStagedDictionariesByType(stagingRoot, typeDirectories, targetTypes)
        } finally {
            tempZip.delete()
            stagingRoot.deleteRecursively()
        }
    }

    private fun commitStagedDictionariesByType(
        stagingRoot: File,
        typeDirectories: Map<DictionaryType, File>,
        targetTypes: Set<DictionaryType>,
    ): Map<DictionaryType, List<ImportedDictionary>> {
        val staged = stagingRoot.listFiles()?.filter(File::isDirectory).orEmpty()
        if (staged.isEmpty()) throw DictionaryImportException(DictionaryImportFailureKind.UnsupportedContents)

        return targetTypes.associateWith { type ->
            val typeDirectory = requireNotNull(typeDirectories[type]) {
                "Missing ${type.directoryName} dictionary directory."
            }
            typeDirectory.mkdirs()
            staged.map { stagedDictionary ->
                val imported = ImportedDictionary(
                    fileName = stagedDictionary.name,
                    index = readIndexFile(stagedDictionary.resolve("index.json")),
                )
                // One archive can land in more than one type directory, so the
                // staged copy is duplicated rather than moved out from under
                // the next type.
                val copyRoot = typeDirectory.resolve(".${stagedDictionary.name}-copy-${UUID.randomUUID()}")
                copyDirectory(stagedDictionary.toPath(), copyRoot.toPath())
                commitStagedDictionary(copyRoot, typeDirectory.resolve(stagedDictionary.name))
                imported
            }
        }.filterValues { it.isNotEmpty() }
    }

    private fun readIndexFile(indexFile: File): DictionaryIndex =
        json.decodeFromString(indexFile.readText())

    private fun commitStagedDictionary(stagedDictionary: File, target: File) {
        val replacementBackup = target.takeIf(File::exists)?.let { existing ->
            requireNotNull(target.parentFile).resolve(".${target.name}-replace-${UUID.randomUUID()}")
                .also { backup -> moveReplacing(existing, backup) }
        }
        try {
            moveReplacing(stagedDictionary, target)
            replacementBackup?.deleteRecursively()
        } catch (error: Throwable) {
            target.deleteRecursively()
            if (replacementBackup?.exists() == true) moveReplacing(replacementBackup, target)
            throw error
        }
    }

    private fun copyDirectory(source: Path, target: Path) {
        Files.walkFileTree(
            source,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.createDirectories(target.resolve(source.relativize(dir)))
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    private fun moveReplacing(source: File, target: File) {
        runCatching {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.recoverCatching { error ->
            if (error !is AtomicMoveNotSupportedException) throw error
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.getOrThrow()
    }
}

private fun NativeDictionaryImportResult.detectedTypes(): Set<DictionaryType> = buildSet {
    if (termCount > 0) add(DictionaryType.Term)
    if (freqCount > 0) add(DictionaryType.Frequency)
    if (pitchCount > 0) add(DictionaryType.Pitch)
}
