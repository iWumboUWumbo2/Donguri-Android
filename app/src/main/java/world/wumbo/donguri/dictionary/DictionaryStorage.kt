//
//  DictionaryStorage.kt
//  Donguri
//
//  On-disk layout of imported dictionaries and the user's ordering/enabled
//  config. Ported from Hoshi Reader Android's DictionaryStorageDataSource with
//  the multi-profile indirection dropped — Donguri has one profile.
//  Copyright © 2026 HuangAntimony.
//  SPDX-License-Identifier: GPL-3.0-or-later
//
package world.wumbo.donguri.dictionary

import kotlinx.serialization.json.Json
import world.wumbo.donguri.di.FilesDir
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryStorage @Inject constructor(
    @FilesDir filesDir: File,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val dictionariesDir = File(filesDir, "Dictionaries")
    private val configFile: File get() = File(dictionariesDir, "config.json")

    fun importRootDirectory(): File = dictionariesDir.apply { mkdirs() }

    fun typeDirectory(type: DictionaryType): File = File(dictionariesDir, type.directoryName)

    fun loadDictionaries(type: DictionaryType): List<DictionaryInfo> =
        DictionaryManager.collectDictionaries(
            storedDicts = storedDictionaries(type),
            configDicts = loadConfig().entriesForType(type),
        )

    fun enabledDictionaryPaths(type: DictionaryType): List<File> =
        loadDictionaries(type).filter { it.isEnabled }.map { it.path }

    fun currentConfig(): DictionaryConfig = DictionaryConfig(
        termDictionaries = configEntries(DictionaryType.Term),
        frequencyDictionaries = configEntries(DictionaryType.Frequency),
        pitchDictionaries = configEntries(DictionaryType.Pitch),
    )

    fun updatableDictionaries(): List<DictionaryUpdateCandidate> =
        DictionaryType.entries.flatMap { type ->
            loadDictionaries(type)
                .filter { it.index.isUpdatable && it.index.indexUrl.isNotBlank() && it.index.downloadUrl.isNotBlank() }
                .map { DictionaryUpdateCandidate(it, type) }
        }

    fun setEnabled(type: DictionaryType, fileName: String, enabled: Boolean) {
        saveConfig(
            currentConfig().copyForType(type) { entries ->
                entries.map { if (it.fileName == fileName) it.copy(isEnabled = enabled) else it }
            },
        )
    }

    fun move(type: DictionaryType, fromIndex: Int, toIndex: Int) {
        saveConfig(
            currentConfig().copyForType(type) {
                DictionaryManager.moveDictionaries(loadDictionaries(type), fromIndex, toIndex)
            },
        )
    }

    /// Records the dictionaries currently on disk, so a freshly imported one
    /// gets a config entry instead of being treated as unconfigured forever.
    fun saveConfigFromStorage() = saveConfig(currentConfig())

    fun saveConfig(config: DictionaryConfig) {
        dictionariesDir.mkdirs()
        configFile.writeText(json.encodeToString(config))
    }

    fun deleteDictionary(type: DictionaryType, fileName: String) {
        File(typeDirectory(type), fileName).deleteRecursively()
        saveConfig(
            currentConfig().copyForType(type) { entries ->
                entries.filterNot { it.fileName == fileName }
            },
        )
    }

    fun hasDictionaryWithTitle(type: DictionaryType, title: String): Boolean =
        loadDictionaries(type).any { it.index.title == title }

    fun readIndex(directory: File): DictionaryIndex? = runCatching {
        json.decodeFromString<DictionaryIndex>(File(directory, "index.json").readText())
    }.getOrNull()

    private fun storedDictionaries(type: DictionaryType): List<DictionaryInfo> {
        val directory = typeDirectory(type)
        directory.mkdirs()
        return directory.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir -> readIndex(dir)?.let { DictionaryInfo(index = it, path = dir) } }
            .orEmpty()
    }

    private fun configEntries(type: DictionaryType): List<DictionaryConfig.DictionaryEntry> =
        loadDictionaries(type).mapIndexed { index, dictionary ->
            DictionaryConfig.DictionaryEntry(
                fileName = dictionary.path.name,
                isEnabled = dictionary.isEnabled,
                order = index,
            )
        }

    private fun loadConfig(): DictionaryConfig = runCatching {
        if (!configFile.exists()) DictionaryConfig() else json.decodeFromString(configFile.readText())
    }.getOrDefault(DictionaryConfig())
}

private fun DictionaryConfig.entriesForType(type: DictionaryType): List<DictionaryConfig.DictionaryEntry> =
    when (type) {
        DictionaryType.Term -> termDictionaries
        DictionaryType.Frequency -> frequencyDictionaries
        DictionaryType.Pitch -> pitchDictionaries
    }

private fun DictionaryConfig.copyForType(
    type: DictionaryType,
    transform: (List<DictionaryConfig.DictionaryEntry>) -> List<DictionaryConfig.DictionaryEntry>,
): DictionaryConfig = when (type) {
    DictionaryType.Term -> copy(termDictionaries = transform(termDictionaries))
    DictionaryType.Frequency -> copy(frequencyDictionaries = transform(frequencyDictionaries))
    DictionaryType.Pitch -> copy(pitchDictionaries = transform(pitchDictionaries))
}
