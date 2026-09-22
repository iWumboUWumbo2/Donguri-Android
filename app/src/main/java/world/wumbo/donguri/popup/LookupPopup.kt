//
//  LookupPopup.kt
//  Donguri
//
//  The floating pop-up dictionary: `DictionaryEntriesWebView` placed over the
//  thread and anchored to the tapped word, as iOS's `PopupView` is.
//  SPDX-License-Identifier: GPL-3.0-or-later
//
package world.wumbo.donguri.popup

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.manhhao.hoshi.LookupResult
import world.wumbo.donguri.config.UserConfig

/// Everything one open pop-up needs. `generation` gives each lookup a fresh
/// identity — popup.js caches what it rendered, so reusing the previous one
/// would keep the previous word on screen.
data class LookupPopupState(
    val anchor: Rect,
    val results: List<LookupResult>,
    val dictionaryStyles: Map<String, String>,
    val sourceText: String,
    val sentence: String,
    /// Where in `sentence` the tapped word starts, for Anki's sentence field.
    val sentenceOffset: Int = 0,
    val generation: Int = 0,
)

@Composable
fun LookupPopup(
    state: LookupPopupState,
    config: UserConfig,
    loadMedia: (dictionary: String, path: String) -> ByteArray?,
    onDismiss: () -> Unit,
    onLookupRedirect: (String) -> List<LookupResult>,
    modifier: Modifier = Modifier,
    ankiSettings: AnkiPopupSettings = AnkiPopupSettings(),
    onMineEntry: (payloadJson: String, respond: (String) -> Unit) -> Unit = { _, respond -> respond("null") },
    onDuplicateCheck: (valuesJson: String, respond: (String) -> Unit) -> Unit = { _, respond -> respond("null") },
) {
    val density = LocalDensity.current
    var containerSize by remember { mutableStateOf(Size.Zero) }

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val topInset = with(density) { insets.calculateTopPadding().toPx() }
    val bottomInset = with(density) { insets.calculateBottomPadding().toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                containerSize = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
            }
            // An invisible scrim: popup.js only sees taps that land inside its
            // own WebView, so without this a tap on the thread behind would
            // leave the pop-up open (or start a second lookup under it).
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        if (containerSize.width <= 0f) return@Box

        val frame = PopupLayout(
            anchor = PopupAnchor(
                x = state.anchor.left.toDouble(),
                y = state.anchor.top.toDouble(),
                width = state.anchor.width.toDouble(),
                height = state.anchor.height.toDouble(),
            ),
            screenWidth = containerSize.width.toDouble(),
            screenHeight = containerSize.height.toDouble(),
            maxWidth = with(density) { config.popupWidth.dp.toPx() }.toDouble(),
            maxHeight = with(density) { config.popupHeight.dp.toPx() }.toDouble(),
            isFullWidth = config.popupFullWidth,
            topInset = topInset.toDouble(),
            bottomInset = bottomInset.toDouble(),
        ).calculate()

        DictionaryEntriesWebView(
            results = state.results,
            dictionaryStyles = state.dictionaryStyles,
            config = config,
            generation = state.generation,
            loadMedia = loadMedia,
            onLookupRedirect = onLookupRedirect,
            sentence = state.sentence,
            sentenceOffset = state.sentenceOffset,
            ankiSettings = ankiSettings,
            onMineEntry = onMineEntry,
            onDuplicateCheck = onDuplicateCheck,
            onTapOutside = onDismiss,
            onSwipeDismiss = onDismiss,
            modifier = Modifier
                .offset { IntOffset(frame.left.toInt(), frame.top.toInt()) }
                .size(
                    width = with(density) { frame.width.toFloat().toDp() },
                    height = with(density) { frame.height.toFloat().toDp() },
                )
                .shadow(8.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp)),
        )
    }
}
