//
//  Ported from Hoshi Reader Android.
//  Copyright © 2026 HuangAntimony.
//  SPDX-License-Identifier: GPL-3.0-or-later
//
package world.wumbo.donguri.dictionary

enum class DictionaryImportFailureKind {
    Native, FileAccess, InvalidArchive, InvalidIndex, UnsupportedContents, WriteFiles
}

class DictionaryImportException(
    val kind: DictionaryImportFailureKind,
    val detail: String? = null,
) : java.io.IOException(detail ?: kind.name) {
    companion object {
        fun fromNative(detail: String): DictionaryImportException = DictionaryImportException(
            kind = when (detail) {
                "failed to open zip" -> DictionaryImportFailureKind.InvalidArchive
                "could not find index.json", "could not read index.json", "failed to parse index.json" ->
                    DictionaryImportFailureKind.InvalidIndex
                "empty dictionary" -> DictionaryImportFailureKind.UnsupportedContents
                "failed to write index.json" -> DictionaryImportFailureKind.WriteFiles
                else -> DictionaryImportFailureKind.Native
            },
            detail = detail,
        )
    }
}
