//
//  PopupResourceHandler.kt
//  Donguri
//
//  Serves the URLs popup.js fetches: dictionary media out of the engine, the
//  popup's own SVG icons out of assets, and Yomitan online audio source lists
//  proxied from the network (the WebView cannot reach them itself, and the
//  popup expects a same-origin JSON reply).
//  Copyright © 2026 HuangAntimony.
//  SPDX-License-Identifier: GPL-3.0-or-later
//
package world.wumbo.donguri.popup

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

private const val ASSET_HOST = "appassets.androidplatform.net"

class PopupResourceHandler(
    private val context: Context,
    private val loadMedia: (dictionary: String, path: String) -> ByteArray?,
) {
    fun handle(uri: Uri): WebResourceResponse? {
        if (uri.scheme != "https" || uri.host != ASSET_HOST) return null
        return when {
            uri.path == "/image" -> imageResponse(uri) ?: notFound()
            uri.path == "/audio" -> audioResponse(uri) ?: notFound()
            uri.path.orEmpty().startsWith("/popup/icons/") -> iconResponse(uri) ?: notFound()
            else -> notFound()
        }
    }

    private fun imageResponse(uri: Uri): WebResourceResponse? {
        val dictionary = uri.getQueryParameter("dictionary").orEmpty()
        val mediaPath = uri.getQueryParameter("path").orEmpty()
        if (dictionary.isBlank() || mediaPath.isBlank()) return null
        val data = loadMedia(dictionary, mediaPath)?.takeIf { it.isNotEmpty() } ?: return null
        return WebResourceResponse(
            dictionaryImageMimeType(mediaPath),
            null,
            ByteArrayInputStream(data),
        ).apply { responseHeaders = mapOf("Access-Control-Allow-Origin" to "*") }
    }

    private fun iconResponse(uri: Uri): WebResourceResponse? {
        val name = uri.lastPathSegment?.takeIf { it.endsWith(".svg") } ?: return null
        return runCatching {
            WebResourceResponse("image/svg+xml", "UTF-8", context.assets.open("donguri-web/popup/icons/$name"))
        }.getOrNull()
    }

    /// The WebView calls this on its own worker thread, and must be answered
    /// synchronously, so the fetch is blocking by design.
    private fun audioResponse(uri: Uri): WebResourceResponse? {
        val target = uri.getQueryParameter("url")?.takeIf { it.startsWith("https://") }
            ?: return jsonResponse(EMPTY_AUDIO_LIST)
        val body = runCatching {
            val connection = (URL(target).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching EMPTY_AUDIO_LIST
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(EMPTY_AUDIO_LIST)
        return jsonResponse(body)
    }

    private fun jsonResponse(body: ByteArray): WebResourceResponse =
        WebResourceResponse("application/json", "UTF-8", ByteArrayInputStream(body)).apply {
            responseHeaders = mapOf("Access-Control-Allow-Origin" to "*")
        }

    private fun notFound(): WebResourceResponse =
        WebResourceResponse(
            "text/plain",
            "UTF-8",
            404,
            "Not Found",
            mapOf("Access-Control-Allow-Origin" to "*"),
            ByteArrayInputStream(ByteArray(0)),
        )

    private companion object {
        val EMPTY_AUDIO_LIST = """{"type":"audioSourceList","audioSources":[]}""".toByteArray()
    }
}

internal fun dictionaryImageMimeType(path: String): String =
    when (path.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "avif" -> "image/avif"
        "heic" -> "image/heic"
        "svg" -> "image/svg+xml"
        else -> "application/octet-stream"
    }
