//
//  Ported from Hoshi Reader Android.
//  Copyright © 2026 HuangAntimony.
//  SPDX-License-Identifier: GPL-3.0-or-later
//
package world.wumbo.donguri.dictionary

import kotlinx.serialization.json.Json
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

interface DictionaryRemoteDataSource {
    fun fetchIndex(url: String): DictionaryIndex
    fun downloadArchive(url: String): InputStream
}

@Singleton
class UrlDictionaryRemoteDataSource @Inject constructor() : DictionaryRemoteDataSource {
    private val json = Json { ignoreUnknownKeys = true }

    override fun fetchIndex(url: String): DictionaryIndex =
        openConnection(url).useConnection { connection ->
            require(connection.responseCode in 200..299) { "Unable to fetch dictionary index." }
            connection.inputStream.use { json.decodeFromString(it.readBytes().decodeToString()) }
        }

    override fun downloadArchive(url: String): InputStream =
        openConnection(url).let { connection ->
            if (connection.responseCode !in 200..299) {
                connection.disconnect()
                error("Unable to download dictionary.")
            }
            HttpConnectionInputStream(connection)
        }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
}

/// Keeps the connection alive for as long as the caller is reading from it.
private class HttpConnectionInputStream(
    private val connection: HttpURLConnection,
) : InputStream() {
    private val delegate = connection.inputStream

    override fun read(): Int = delegate.read()

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate.read(buffer, offset, length)

    override fun close() {
        try {
            delegate.close()
        } finally {
            connection.disconnect()
        }
    }
}

private inline fun <T : HttpURLConnection, R> T.useConnection(block: (T) -> R): R =
    try {
        block(this)
    } finally {
        disconnect()
    }
