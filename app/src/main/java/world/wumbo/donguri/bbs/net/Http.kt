package world.wumbo.donguri.bbs.net

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

/// Everything 5ch serves — bbsmenu aside — is Shift-JIS, not UTF-8.
internal val SHIFT_JIS: Charset = Charset.forName("Shift_JIS")

internal class HttpStatusException(val statusCode: Int, url: String) :
    IOException("HTTP $statusCode for $url")

/// 5ch rejects the default Java user agent outright.
private const val USER_AGENT = "Monazilla/1.00 Donguri/1.0"

/// A plain blocking GET. Callers are responsible for running this off the main
/// thread; every call site already sits inside `withContext(Dispatchers.IO)`.
internal fun httpGet(url: String): ByteArray {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15_000
        readTimeout = 20_000
        setRequestProperty("User-Agent", USER_AGENT)
        setRequestProperty("Accept-Encoding", "gzip")
        instanceFollowRedirects = true
    }
    try {
        val status = connection.responseCode
        if (status != HttpURLConnection.HTTP_OK) throw HttpStatusException(status, url)
        val stream = if (connection.contentEncoding.equals("gzip", ignoreCase = true)) {
            java.util.zip.GZIPInputStream(connection.inputStream)
        } else {
            connection.inputStream
        }
        return stream.use { it.readBytes() }
    } finally {
        connection.disconnect()
    }
}

/// Resolves a path against a board URL the way `URL(string:relativeTo:)` does
/// on iOS — board URLs from bbsmenu always end in a slash, but not all do.
internal fun resolveAgainstBoard(boardUrl: String, path: String): String =
    URL(URL(if (boardUrl.endsWith("/")) boardUrl else "$boardUrl/"), path).toString()
