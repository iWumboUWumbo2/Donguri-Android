//
//  PopupBridge.kt
//  Donguri
//
//  The host side of popup.js's `webkit.messageHandlers` calls.
//  SPDX-License-Identifier: GPL-3.0-or-later
//
package world.wumbo.donguri.popup

import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/// What the host has to answer for the popup. Everything arrives on the
/// WebView's JavaScript thread, so implementations post their own work.
interface PopupHost {
    fun onShellReady()
    fun onContentReady() {}
    fun onTapOutside()
    fun onSwipeDismiss()
    fun onOpenLink(url: String)
    /// A tap inside the popup's own glossary text: run a nested lookup and
    /// return how many characters matched (0 means nothing found).
    fun onLookupRedirect(query: String, respond: (Int) -> Unit)
    /// Entry `index` of the current result set, as popup.js entry JSON.
    fun onGetEntry(index: Int, respond: (String?) -> Unit)
    fun onMineEntry(payloadJson: String, respond: (String) -> Unit)
    fun onDuplicateCheck(valuesJson: String, respond: (String) -> Unit)
    fun onPlayWordAudio(payloadJson: String)
    fun onTextSelected(selectionJson: String) {}
}

class PopupBridge(
    private val host: PopupHost,
    private val webView: () -> WebView?,
    private val postToMain: (() -> Unit) -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true }

    @JavascriptInterface
    fun post(name: String, bodyJson: String, id: String) {
        val body = runCatching { json.parseToJsonElement(bodyJson) }.getOrDefault(JsonNull)
        postToMain {
            when (name) {
                "shellReady" -> host.onShellReady()
                "contentReady" -> host.onContentReady()
                "tapOutside" -> host.onTapOutside()
                "swipeDismiss" -> host.onSwipeDismiss()
                "openLink" -> host.onOpenLink(body.asString().orEmpty())
                "playWordAudio" -> host.onPlayWordAudio(bodyJson)
                "textSelected" -> host.onTextSelected(bodyJson)
                "lookupRedirect" -> host.onLookupRedirect(body.asString().orEmpty()) { count ->
                    resolve(id, JsonPrimitive(count).toString())
                }
                "getEntry" -> host.onGetEntry(body.asInt() ?: 0) { entryJson ->
                    resolve(id, entryJson ?: "null")
                }
                "mineEntry" -> host.onMineEntry(bodyJson) { resultJson -> resolve(id, resultJson) }
                "duplicateCheck" -> host.onDuplicateCheck(bodyJson) { resultJson -> resolve(id, resultJson) }
                // Messages Donguri has nothing to do for: popup scroll state,
                // source history and kanji redirects (no kanji dictionaries).
                else -> if (id.isNotEmpty()) resolve(id, "null")
            }
        }
    }

    /// Hands a `requestMessage` promise its value. `resultJson` must already be
    /// valid JSON: it is parsed on the page side.
    private fun resolve(id: String, resultJson: String) {
        if (id.isEmpty()) return
        val encodedResult = JsonPrimitive(resultJson).toString()
        webView()?.evaluateJavascript(
            "window.DonguriPopup.resolveMessage(${JsonPrimitive(id)}, $encodedResult);",
            null,
        )
    }

    private fun JsonElement.asString(): String? =
        runCatching { jsonPrimitive.content }.getOrNull()

    private fun JsonElement.asInt(): Int? =
        runCatching { jsonPrimitive.int }.getOrNull()
}
