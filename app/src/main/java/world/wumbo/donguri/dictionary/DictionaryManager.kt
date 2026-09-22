//
//  DictionaryManager.kt
//  Donguri
//
//  Ported verbatim in behaviour from Hoshi Reader Android.
//  Copyright © 2026 HuangAntimony.
//  SPDX-License-Identifier: GPL-3.0-or-later
//
package world.wumbo.donguri.dictionary

/// Reconciles what is on disk with the user's saved ordering: configured
/// dictionaries keep their order and enabled flag, and anything newly imported
/// lands at the end, enabled.
object DictionaryManager {
    fun collectDictionaries(
        storedDicts: List<DictionaryInfo>,
        configDicts: List<DictionaryConfig.DictionaryEntry>,
        unconfiguredDictionariesEnabled: Boolean = true,
    ): List<DictionaryInfo> {
        val result = mutableListOf<DictionaryInfo>()
        configDicts.sortedBy { it.order }.forEach { config ->
            val stored = storedDicts.firstOrNull { it.path.name == config.fileName } ?: return@forEach
            result += stored.copy(isEnabled = config.isEnabled, order = config.order)
        }

        val collectedFileNames = result.mapTo(mutableSetOf()) { it.path.name }
        storedDicts.forEach { stored ->
            if (stored.path.name !in collectedFileNames) {
                result += stored.copy(isEnabled = unconfiguredDictionariesEnabled, order = result.size)
            }
        }
        return result
    }

    fun moveDictionaries(
        dictionaries: List<DictionaryInfo>,
        fromIndex: Int,
        toIndex: Int,
    ): List<DictionaryConfig.DictionaryEntry> {
        if (dictionaries.isEmpty() || fromIndex !in dictionaries.indices) {
            return dictionaries.toConfigEntries()
        }
        val reordered = dictionaries.toMutableList()
        val moved = reordered.removeAt(fromIndex)
        reordered.add(toIndex.coerceIn(0, reordered.size), moved)
        return reordered.toConfigEntries()
    }

    private fun List<DictionaryInfo>.toConfigEntries(): List<DictionaryConfig.DictionaryEntry> =
        mapIndexed { index, dictionary ->
            DictionaryConfig.DictionaryEntry(
                fileName = dictionary.path.name,
                isEnabled = dictionary.isEnabled,
                order = index,
            )
        }
}
