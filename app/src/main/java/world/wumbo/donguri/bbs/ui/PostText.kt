package world.wumbo.donguri.bbs.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import world.wumbo.donguri.bbs.text.RenderedPostText
import world.wumbo.donguri.bbs.text.renderPostText

/// A tap that landed on a word rather than a link: the character offset it hit
/// and where that character sits on screen, so a pop-up can be anchored to it.
data class PostTextTap(
    val offset: Int,
    val text: String,
    val charBounds: Rect,
)

/// One post's body.
///
/// On iOS this is a `WKWebView` running selection.js, because SwiftUI's `Text`
/// cannot tell you which character a tap landed on. Compose can, via
/// `TextLayoutResult`, so the Android port keeps the text native: no webview
/// per row, and the list scrolls like any other Compose list.
@Composable
fun PostText(
    text: String,
    modifier: Modifier = Modifier,
    highlightRange: IntRange? = null,
    onLinkClick: (String) -> Unit = {},
    onWordTap: (PostTextTap) -> Unit = {},
    onLongPress: () -> Unit = {},
) {
    val rendered: RenderedPostText = remember(text) { text.renderPostText() }
    val linkColor = MaterialTheme.colorScheme.primary
    val selectionColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)

    val annotated: AnnotatedString = remember(rendered, highlightRange, linkColor, selectionColor) {
        buildAnnotatedString {
            append(rendered.text)
            for (link in rendered.links) {
                addStyle(
                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                    link.start,
                    link.end,
                )
            }
            if (highlightRange != null && !highlightRange.isEmpty()) {
                val start = highlightRange.first.coerceIn(0, rendered.text.length)
                val end = (highlightRange.last + 1).coerceIn(start, rendered.text.length)
                addStyle(SpanStyle(background = selectionColor), start, end)
            }
        }
    }

    var layout by remember(rendered) { mutableStateOf<TextLayoutResult?>(null) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Text(
        text = annotated,
        style = LocalTextStyle.current,
        onTextLayout = { layout = it },
        modifier = modifier
            .onGloballyPositioned { coordinates = it }
            .pointerInput(rendered) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onTap = { position ->
                        val result = layout ?: return@detectTapGestures
                        val offset = result.getOffsetForPosition(position)
                        if (offset !in rendered.text.indices) return@detectTapGestures

                        // A tap on a link follows it; anything else is a lookup.
                        val link = rendered.links.firstOrNull { offset in it.start until it.end }
                        if (link != null) {
                            onLinkClick(link.url)
                            return@detectTapGestures
                        }

                        val local = result.getBoundingBox(offset)
                        val origin = coordinates?.localToRoot(Offset(local.left, local.top)) ?: Offset.Zero
                        onWordTap(
                            PostTextTap(
                                offset = offset,
                                text = rendered.text,
                                charBounds = Rect(origin, local.size),
                            ),
                        )
                    },
                )
            },
    )
}
