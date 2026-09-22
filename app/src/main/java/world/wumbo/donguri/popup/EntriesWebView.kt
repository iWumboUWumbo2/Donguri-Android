//
//  EntriesWebView.kt
//  Donguri
//
//  A WebView running Hoshi Reader's popup.js over a set of lookup results.
//  Both the floating tap-to-lookup pop-up and the standalone dictionary search
//  are this view; only their placement differs.
//  SPDX-License-Identifier: GPL-3.0-or-later
//
package world.wumbo.donguri.popup

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import de.manhhao.hoshi.LookupResult
import kotlinx.serialization.json.JsonPrimitive
import world.wumbo.donguri.config.UserConfig

/// The entry list the bridge answers `getEntry` from.
///
/// It has to outlive any one lookup: the WebView and its injected bridge are
/// created once, while `results` changes on every new lookup and again on
/// every nested lookup made from inside the pop-up.
private class PopupEntryHolder {
    var entries: List<LookupResult> = emptyList()
    var sentence: String = ""
    var sentenceOffset: Int = 0
    var generation: Int = 0
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DictionaryEntriesWebView(
    results: List<LookupResult>,
    dictionaryStyles: Map<String, String>,
    config: UserConfig,
    generation: Int,
    loadMedia: (dictionary: String, path: String) -> ByteArray?,
    onLookupRedirect: (String) -> List<LookupResult>,
    modifier: Modifier = Modifier,
    sentence: String = "",
    sentenceOffset: Int = 0,
    ankiSettings: AnkiPopupSettings = AnkiPopupSettings(),
    onMineEntry: (payloadJson: String, respond: (String) -> Unit) -> Unit = { _, respond -> respond("null") },
    onDuplicateCheck: (valuesJson: String, respond: (String) -> Unit) -> Unit = { _, respond -> respond("null") },
    onTapOutside: () -> Unit = {},
    onSwipeDismiss: () -> Unit = {},
) {
    val context = LocalContext.current
    val assets = remember { PopupAssets.load(context) }
    val darkMode = MaterialTheme.colorScheme.background.isDark()

    val currentOnTapOutside by rememberUpdatedState(onTapOutside)
    val currentOnSwipeDismiss by rememberUpdatedState(onSwipeDismiss)
    val currentOnLookupRedirect by rememberUpdatedState(onLookupRedirect)
    val currentOnMine by rememberUpdatedState(onMineEntry)
    val currentOnDuplicateCheck by rememberUpdatedState(onDuplicateCheck)
    val currentLoadMedia by rememberUpdatedState(loadMedia)

    var webView by remember { mutableStateOf<WebView?>(null) }
    val holder = remember { PopupEntryHolder() }
    holder.entries = results
    holder.sentence = sentence
    holder.sentenceOffset = sentenceOffset
    holder.generation = generation

    // Created once, alongside the bridge it is injected with: a host rebuilt
    // per lookup would leave the WebView talking to a stale one.
    val host = remember {
        object : PopupHost {
            override fun onShellReady() = renderEntries(webView, holder)

            override fun onTapOutside() = currentOnTapOutside()

            override fun onSwipeDismiss() = currentOnSwipeDismiss()

            override fun onOpenLink(url: String) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }

            override fun onLookupRedirect(query: String, respond: (Int) -> Unit) {
                val found = runCatching { currentOnLookupRedirect(query) }.getOrDefault(emptyList())
                holder.entries = found
                respond(found.size)
            }

            override fun onGetEntry(index: Int, respond: (String?) -> Unit) {
                respond(holder.entries.getOrNull(index)?.toEntryJsonString())
            }

            override fun onMineEntry(payloadJson: String, respond: (String) -> Unit) =
                currentOnMine(payloadJson, respond)

            override fun onDuplicateCheck(valuesJson: String, respond: (String) -> Unit) =
                currentOnDuplicateCheck(valuesJson, respond)

            override fun onPlayWordAudio(payloadJson: String) = Unit
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { factoryContext ->
            WebView(factoryContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Everything the page needs is inlined or served from the
                // asset origin; file and content access stay off.
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mediaPlaybackRequiresUserGesture = false
                overScrollMode = WebView.OVER_SCROLL_NEVER

                val resourceHandler = PopupResourceHandler(factoryContext) { dictionary, path ->
                    currentLoadMedia(dictionary, path)
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? = resourceHandler.handle(request.url)
                }

                val mainHandler = Handler(Looper.getMainLooper())
                addJavascriptInterface(
                    PopupBridge(
                        host = host,
                        webView = { this },
                        postToMain = { block -> mainHandler.post(block) },
                    ),
                    POPUP_BRIDGE_NAME,
                )
                webView = this
            }
        },
        update = { view -> webView = view },
        onRelease = { view ->
            view.removeJavascriptInterface(POPUP_BRIDGE_NAME)
            view.destroy()
            webView = null
        },
    )

    // The settings and dictionary CSS are baked into the document, so those
    // need a reload; the page reports `shellReady` when it comes back up and
    // the entries are rendered from there.
    LaunchedEffect(webView, config, darkMode, ankiSettings, dictionaryStyles) {
        val view = webView ?: return@LaunchedEffect
        view.loadDataWithBaseURL(
            POPUP_ASSET_ORIGIN,
            PopupHtml.render(
                assets = assets,
                config = config,
                dictionaryStyles = dictionaryStyles,
                darkMode = darkMode,
                ankiSettings = ankiSettings,
            ),
            "text/html",
            "UTF-8",
            null,
        )
    }

    // A new lookup into an already-loaded page only needs a re-render.
    LaunchedEffect(webView, generation) {
        renderEntries(webView, holder)
    }
}

private fun renderEntries(webView: WebView?, holder: PopupEntryHolder) {
    val view = webView ?: return
    val initialEntry = holder.entries.firstOrNull()?.toEntryJsonString()
    view.evaluateJavascript(
        "window.donguriRenderPopup?.(" +
            jsString(holder.generation.toString()) + ", " +
            holder.entries.size + ", " +
            (if (initialEntry == null) "null" else jsString(initialEntry)) + ", " +
            jsString(holder.sentence) + ", " +
            holder.sentenceOffset +
            ");",
        null,
    )
}

private fun jsString(value: String): String = JsonPrimitive(value).toString()

internal fun Color.isDark(): Boolean = (0.2126f * red + 0.7152f * green + 0.0722f * blue) < 0.5f
