//
//  LookupEngine.kt
//  Donguri
//
//  Owns the hoshidicts lookup session. Ported from Hoshi Reader Android's
//  DictionaryLookupQueryService; the iOS build calls this LookupEngine, and the
//  name is kept so the two ports read the same.
//  Copyright © 2026 Manhhao, HuangAntimony.
//  SPDX-License-Identifier: GPL-3.0-or-later
//
package world.wumbo.donguri.dictionary

import de.manhhao.hoshi.DictionaryStyle
import de.manhhao.hoshi.LookupResult
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.read
import kotlin.concurrent.write

@Singleton
class LookupEngine @Inject constructor(
    private val nativeBridge: DictionaryNativeBridge,
) {
    private val rebuildLock = Any()
    private val queryLock = ReentrantReadWriteLock()
    private var currentSession: Long? = null

    /// Builds a fresh session and swaps it in only once it is ready, so a
    /// rebuild triggered by a settings change never leaves a tapped word
    /// looking at a half-built query.
    fun rebuild(
        termDictionaries: List<File>,
        frequencyDictionaries: List<File>,
        pitchDictionaries: List<File>,
    ) {
        synchronized(rebuildLock) {
            val nextSession = nativeBridge.createLookupObject(DICTIONARY_LANGUAGE_ID)
            var committed = false
            try {
                nativeBridge.rebuildQuery(
                    session = nextSession,
                    termPaths = termDictionaries.toAbsolutePathArray(),
                    freqPaths = frequencyDictionaries.toAbsolutePathArray(),
                    pitchPaths = pitchDictionaries.toAbsolutePathArray(),
                )
                val previousSession = queryLock.write {
                    val previous = currentSession
                    currentSession = nextSession
                    committed = true
                    previous
                }
                previousSession?.let(nativeBridge::destroyLookupObject)
            } finally {
                if (!committed) nativeBridge.destroyLookupObject(nextSession)
            }
        }
    }

    val hasDictionaries: Boolean
        get() = queryLock.read { currentSession != null }

    fun lookup(
        text: String,
        maxResults: Int = 16,
        scanLength: Int = 16,
    ): List<LookupResult> = queryLock.read {
        currentSession?.let { nativeBridge.lookup(it, text, maxResults, scanLength) }.orEmpty()
    }

    fun getStyles(): List<DictionaryStyle> = queryLock.read {
        currentSession?.let(nativeBridge::getStyles).orEmpty()
    }

    fun getMediaFile(dictionary: String, path: String): ByteArray? = queryLock.read {
        currentSession?.let { nativeBridge.getMediaFile(it, dictionary, path) }
    }

    private fun List<File>.toAbsolutePathArray(): Array<String> =
        map { it.absolutePath }.toTypedArray()
}
