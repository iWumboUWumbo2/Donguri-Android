//
//  PopupHtml.kt
//  Donguri
//
//  Builds the document the pop-up dictionary WebView loads.
//
//  `popup.js` and `popup.css` are Hoshi Reader's, copied verbatim, and they
//  talk to their host through `webkit.messageHandlers.*` — the WKWebView API.
//  Hoshi Reader Android shims that onto `window.parent.postMessage` because its
//  pop-ups are iframes inside a host page stacked over the reader's WebView.
//  Donguri renders posts natively, so each pop-up is its own WebView in a
//  Compose overlay and the shim can talk straight to a `@JavascriptInterface`
//  object instead, with no iframe and no parent frame in between.
//
//  Copyright © 2026 Manhhao, HuangAntimony.
//  SPDX-License-Identifier: GPL-3.0-or-later
//
package world.wumbo.donguri.popup

import android.content.Context
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import world.wumbo.donguri.config.UserConfig
import java.util.Locale

/// Everything the popup loads by URL (dictionary images, audio) is served from
/// this origin by the WebView's asset loader.
const val POPUP_ASSET_ORIGIN = "https://appassets.androidplatform.net"

/// The name the `@JavascriptInterface` bridge object is injected under.
const val POPUP_BRIDGE_NAME = "DonguriPopupHost"

data class PopupAssets(
    val popupJs: String,
    val popupCss: String,
    val popupGesturesJs: String,
    val selectionJs: String,
    val languageJapaneseJs: String,
    val selectionJapaneseJs: String,
) {
    companion object {
        @Volatile
        private var cached: PopupAssets? = null

        fun load(context: Context): PopupAssets =
            cached ?: synchronized(this) { cached ?: read(context.applicationContext).also { cached = it } }

        private fun read(context: Context) = PopupAssets(
            popupJs = context.readAsset("donguri-web/popup/popup.js"),
            popupCss = context.readAsset("donguri-web/popup/popup.css"),
            popupGesturesJs = context.readAsset("donguri-web/popup/popup-gestures.js"),
            selectionJs = context.readAsset("donguri-web/shared/selection.js"),
            languageJapaneseJs = context.readAsset("donguri-web/shared/language-ja.js"),
            selectionJapaneseJs = context.readAsset("donguri-web/shared/selection-ja.js"),
        )

        private fun Context.readAsset(path: String): String =
            assets.open(path).bufferedReader().use { it.readText() }
    }
}

/// What the Anki half of the pop-up needs to draw its buttons. With no format
/// configured the list is empty and popup.js draws no mining button at all.
data class AnkiPopupSettings(
    val isBackendAvailable: Boolean = false,
    val needsAudio: Boolean = false,
    val allowDupes: Boolean = false,
    val useAnkiConnect: Boolean = false,
    val compactGlossaries: Boolean = true,
    val disableShowNotes: Boolean = true,
    val formats: List<AnkiPopupFormat> = emptyList(),
)

/// Donguri configures one card format, as its iOS build does. `icon` is one of
/// popup.js's shapes: "square", "circle" or "diamond".
data class AnkiPopupFormat(
    val id: String,
    val isValid: Boolean,
    val icon: String = "square",
) {
    companion object {
        const val DEFAULT_ID = "default"
    }
}

object PopupHtml {
    fun render(
        assets: PopupAssets,
        config: UserConfig,
        dictionaryStyles: Map<String, String>,
        darkMode: Boolean,
        ankiSettings: AnkiPopupSettings = AnkiPopupSettings(),
        noAudioFoundText: String = "No audio found",
        audioLoadingText: String = "Loading…",
    ): String {
        val settings = config.normalized()
        val colorScheme = if (darkMode) "dark" else "light"

        return """
            <!DOCTYPE html>
            <html lang="ja" data-hoshi-color-scheme="$colorScheme">
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>${assets.popupCss}</style>
                <style>$COLOR_SCHEME_CSS</style>
                <style>
                    :root { --hoshi-content-font-family: "Noto Sans CJK JP", "NotoSansCJKJP-Regular", sans-serif; }
                    html { zoom: ${cssNumber(settings.popupScale)}; overscroll-behavior: none; }
                    body { overscroll-behavior: none; }
                </style>
                ${customCssStyle(settings.customCss)}
                <script>
                    ${bridgeScript()}
                    ${windowGlobals(settings, dictionaryStyles, ankiSettings, noAudioFoundText, audioLoadingText)}
                </script>
                <script>${assets.languageJapaneseJs}</script>
                <script>${assets.selectionJapaneseJs}</script>
                <script>${assets.selectionJs}</script>
                <script>window.hoshiSelection?.configure?.({ language: "ja" });</script>
                <script>${assets.popupJs}</script>
            </head>
            <body>
                <script>${assets.popupGesturesJs}</script>
                <div id="search-text" hidden style="--hoshi-search-text-size: 22px;"></div>
                <div id="entries-container"></div>
                <div class="overlay">
                    <div class="overlay-close" onclick="closeOverlay()">×</div>
                    <div class="overlay-content"></div>
                </div>
                <script>${readyScript()}</script>
            </body>
            </html>
        """.trimIndent()
    }

    /// Maps `webkit.messageHandlers.*` onto the injected bridge object. The
    /// handlers popup.js awaits a value from go through `requestMessage`, which
    /// the host answers by calling `resolveMessage` back.
    private fun bridgeScript(): String = """
        window.DonguriPopup = window.DonguriPopup || (function() {
            var nextMessageId = 1;
            var pendingMessages = {};
            function post(name, body, id) {
                try {
                    $POPUP_BRIDGE_NAME.post(name, JSON.stringify(body === undefined ? null : body), id || '');
                } catch (e) {
                    console.warn('Donguri popup bridge failed', e);
                }
            }
            return {
                postMessage: post,
                requestMessage: function(name, body) {
                    return new Promise(function(resolve) {
                        var id = String(nextMessageId++);
                        pendingMessages[id] = resolve;
                        post(name, body, id);
                    });
                },
                resolveMessage: function(id, resultJson) {
                    var resolve = pendingMessages[id];
                    if (!resolve) return;
                    delete pendingMessages[id];
                    var value = null;
                    try { value = resultJson ? JSON.parse(resultJson) : null; } catch (e) {}
                    resolve(value);
                }
            };
        })();
        window.webkit = {
            messageHandlers: {
                openLink: { postMessage: function(url) { window.DonguriPopup.postMessage('openLink', url); } },
                textSelected: { postMessage: function(s) { window.DonguriPopup.postMessage('textSelected', s); } },
                tapOutside: { postMessage: function() { window.DonguriPopup.postMessage('tapOutside'); } },
                swipeDismiss: { postMessage: function() { window.DonguriPopup.postMessage('swipeDismiss'); } },
                playWordAudio: { postMessage: function(c) { window.DonguriPopup.postMessage('playWordAudio', c); } },
                shellReady: { postMessage: function() { window.DonguriPopup.postMessage('shellReady'); } },
                contentReady: { postMessage: function() { window.DonguriPopup.postMessage('contentReady'); } },
                popupScrolled: { postMessage: function() { window.DonguriPopup.postMessage('popupScrolled'); } },
                sourceHistoryRestored: { postMessage: function(o) { window.DonguriPopup.postMessage('sourceHistoryRestored', { sentenceOffset: o }); } },
                kanjiRedirectCommitted: { postMessage: function() { window.DonguriPopup.postMessage('kanjiRedirectCommitted'); } },
                mineEntry: { postMessage: function(c) { return window.DonguriPopup.requestMessage('mineEntry', c); } },
                duplicateCheck: { postMessage: function(v) { return window.DonguriPopup.requestMessage('duplicateCheck', v); } },
                showNotes: { postMessage: function(c) { return window.DonguriPopup.requestMessage('showNotes', c); } },
                getEntry: { postMessage: function(i) { return window.DonguriPopup.requestMessage('getEntry', i); } },
                lookupRedirect: { postMessage: function(q) { return window.DonguriPopup.requestMessage('lookupRedirect', q); } },
                kanjiRedirect: { postMessage: function(k) { return window.DonguriPopup.requestMessage('kanjiRedirect', k); } }
            }
        };
    """.trimIndent()

    private fun windowGlobals(
        settings: UserConfig,
        dictionaryStyles: Map<String, String>,
        anki: AnkiPopupSettings,
        noAudioFoundText: String,
        audioLoadingText: String,
    ): String = """
        window.scanNonJapaneseText = ${settings.scanNonJapaneseText};
        window.scanLength = ${settings.scanLength};
        window.collapseMode = "${settings.collapseMode.rawValue}";
        window.expandFirstDictionary = ${settings.expandFirstDictionary};
        window.collapsedDictionaries = ${jsonArray(settings.collapsedDictionaries.sorted())};
        window.twoColumnLayout = ${settings.twoColumnLayout};
        window.compactGlossaries = ${settings.compactGlossaries};
        window.showExpressionTags = ${settings.showExpressionTags};
        window.harmonicFrequency = ${settings.harmonicFrequency};
        window.deduplicatePitchAccents = ${settings.deduplicatePitchAccents};
        window.compactPitchAccents = ${settings.compactPitchAccents};
        window.audioSources = ${audioSourcesJson(settings)};
        window.noAudioFoundText = ${JsonPrimitive(noAudioFoundText)};
        window.audioLoadingText = ${JsonPrimitive(audioLoadingText)};
        window.audioRequestEndpoint = "$POPUP_ASSET_ORIGIN/audio";
        window.dictionaryMediaRequestEndpoint = "$POPUP_ASSET_ORIGIN/image";
        window.disablePopupImageViewportMaxHeight = true;
        window.audioEnableAutoplay = ${settings.audioEnableAutoplay};
        window.audioPlaybackMode = "${settings.audioPlaybackMode.rawValue}";
        window.needsAudio = ${anki.needsAudio};
        window.allowDupes = ${anki.allowDupes};
        window.useAnkiConnect = ${anki.useAnkiConnect};
        window.compactGlossariesAnki = ${anki.compactGlossaries};
        window.ankiFormats = ${ankiFormatsJson(anki)};
        window.ankiBackendAvailable = ${anki.isBackendAvailable};
        window.disableShowNotes = ${anki.disableShowNotes};
        window.customCSS = ${JsonPrimitive(settings.customCss)};
        window.swipeThreshold = ${if (settings.popupSwipeToDismiss) settings.popupSwipeThreshold.coerceAtLeast(0) else 0};
        window.reducedMotionScrolling = false;
        window.reducedMotionScrollScale = 1.0;
        window.reducedMotionSwipeThreshold = 40;
        window.dictionaryStyles = ${dictionaryStylesJson(dictionaryStyles)};
        window.lookupEntries = [];
        window.entryCount = 0;
        window.popupId = null;
    """.trimIndent()

    /// The host drives rendering by calling `window.donguriRenderPopup(...)`
    /// once the WebView reports the shell is up.
    private fun readyScript(): String = """
        (function() {
            var container = document.getElementById('entries-container');
            var posted = false;
            var observer = null;
            function postReady() {
                if (posted) return;
                posted = true;
                webkit.messageHandlers.contentReady.postMessage();
            }
            function hasRenderableContent() {
                if (!container || !window.entryCount) return true;
                return !!container.querySelector('.entry .glossary-content');
            }
            window.hoshiPopupObserveContentReady = function() {
                posted = false;
                if (observer) { observer.disconnect(); observer = null; }
                if (hasRenderableContent()) { postReady(); return; }
                observer = new MutationObserver(function() {
                    if (hasRenderableContent()) {
                        postReady();
                        observer.disconnect();
                        observer = null;
                    }
                });
                observer.observe(container, { childList: true, subtree: true });
                if (hasRenderableContent()) postReady();
            };
            window.donguriRenderPopup = function(popupId, entriesCount, initialEntryJson, sourceText, sourceSentenceOffset) {
                window.popupId = popupId || null;
                closeOverlay();
                window.entryCount = entriesCount || 0;
                var initialEntries = [];
                if (initialEntryJson) {
                    try { initialEntries[0] = JSON.parse(initialEntryJson); } catch (e) { initialEntries = []; }
                }
                if (window.replacePopupResults) {
                    window.replacePopupResults(window.entryCount, initialEntries, sourceText, sourceSentenceOffset);
                } else {
                    window.lookupEntries = initialEntries;
                    window.hoshiPopupObserveContentReady?.();
                    window.renderPopup();
                }
            };
            window.donguriResetPopup = function() {
                window.popupId = null;
                closeOverlay();
                window.hoshiSelection?.clearSelection();
                window.resetPopupResults?.();
            };
            webkit.messageHandlers.shellReady.postMessage(null);
        })();
    """.trimIndent()

    private fun jsonArray(values: Collection<String>): String =
        buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }.toString()

    private fun dictionaryStylesJson(styles: Map<String, String>): String =
        buildJsonObject { styles.forEach { (dictionary, css) -> put(dictionary, css) } }.toString()

    private fun audioSourcesJson(settings: UserConfig): String =
        buildJsonArray {
            settings.audioSources.filter { it.isEnabled }.forEach { source ->
                add(
                    buildJsonObject {
                        put("name", source.name)
                        put("url", source.url)
                    },
                )
            }
        }.toString()

    private fun ankiFormatsJson(anki: AnkiPopupSettings): String =
        buildJsonArray {
            anki.formats.forEach { format ->
                add(
                    buildJsonObject {
                        put("id", format.id)
                        put("isValid", format.isValid)
                        put("icon", format.icon)
                    },
                )
            }
        }.toString()

    private fun cssNumber(value: Double): String {
        val formatted = String.format(Locale.US, "%.2f", value).trimEnd('0')
        return if (formatted.endsWith('.')) "${formatted}0" else formatted
    }

    private fun customCssStyle(css: String): String {
        val content = css.trim()
        if (content.isEmpty()) return ""
        // A `</style` inside user CSS would otherwise close the element early.
        val escaped = content.replace(Regex("</style", RegexOption.IGNORE_CASE), "<\\/style")
        return """<style id="popup-custom-css">$escaped</style>"""
    }

    /// popup.css follows the system colour scheme; the WebView does not, so the
    /// host tells it which one to use.
    private const val COLOR_SCHEME_CSS = """
        html[data-hoshi-color-scheme="light"],
        html[data-hoshi-color-scheme="light"] body {
            --background-color: #fff;
            --background-color-light: #fff;
            --text-color: #000;
            color-scheme: light;
            background-color: #fff !important;
        }

        html[data-hoshi-color-scheme="light"] .overlay {
            background: #eee;
            color: #000;
        }

        html[data-hoshi-color-scheme="dark"],
        html[data-hoshi-color-scheme="dark"] body {
            --background-color: #000;
            --background-color-light: #000;
            --text-color: #fff;
            --text-color-light1: #aaaaaa;
            --text-color-light2: #999999;
            --text-color-light3: #888888;
            --text-color-light4: #777777;
            --background-color-dark1: #333333;
            color-scheme: dark;
            background-color: #000 !important;
        }

        html[data-hoshi-color-scheme="dark"] .overlay {
            background: #000;
            color: #fff;
        }

        html[data-hoshi-color-scheme="dark"] .glossary-group > div[data-dictionary] {
            color: var(--text-color) !important;
        }
    """
}
