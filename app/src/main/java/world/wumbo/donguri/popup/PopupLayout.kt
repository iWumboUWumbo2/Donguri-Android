//
//  PopupLayout.kt
//  Donguri
//
//  Where the pop-up sits relative to the tapped word. Ported from Hoshi Reader
//  Android's LookupPopupLayout, which in turn mirrors the iOS placement — the
//  vertical-writing branch is dropped, since 5ch posts are always horizontal.
//  Copyright © 2026 HuangAntimony.
//  SPDX-License-Identifier: GPL-3.0-or-later
//
package world.wumbo.donguri.popup

data class PopupAnchor(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
)

data class PopupFrame(
    val width: Double,
    val height: Double,
    val centerX: Double,
    val centerY: Double,
) {
    val left: Double get() = centerX - width / 2
    val top: Double get() = centerY - height / 2
}

data class PopupLayout(
    val anchor: PopupAnchor,
    val screenWidth: Double,
    val screenHeight: Double,
    val maxWidth: Double,
    val maxHeight: Double,
    val isFullWidth: Boolean = false,
    val topInset: Double = 0.0,
    val bottomInset: Double = 0.0,
) {
    fun calculate(): PopupFrame {
        val width = width()
        val height = height()
        return PopupFrame(
            width = width,
            height = height,
            centerX = centerX(width),
            centerY = centerY(height),
        )
    }

    private fun width(): Double =
        if (isFullWidth) screenWidth - SCREEN_BORDER_PADDING * 2
        else minOf(screenWidth - SCREEN_BORDER_PADDING * 2, maxWidth)

    private fun height(): Double =
        if (isFullWidth) minOf(availableHeight(), maxHeight)
        // Otherwise the popup takes whichever side of the word has more room.
        else minOf(maxOf(spaceAbove(), spaceBelow()) - SCREEN_BORDER_PADDING, maxHeight)

    private fun availableHeight(): Double =
        (screenHeight - topInset - bottomInset - SCREEN_BORDER_PADDING * 2).coerceAtLeast(0.0)

    private fun centerX(width: Double): Double {
        if (isFullWidth) return width / 2 + SCREEN_BORDER_PADDING
        val raw = anchor.x + width / 2
        return raw.coerceIn(
            width / 2 + SCREEN_BORDER_PADDING,
            (screenWidth - width / 2 - SCREEN_BORDER_PADDING).coerceAtLeast(width / 2 + SCREEN_BORDER_PADDING),
        )
    }

    private fun centerY(height: Double): Double {
        if (isFullWidth) return screenHeight - bottomInset - height / 2 - SCREEN_BORDER_PADDING
        val raw = if (spaceBelow() >= height) {
            anchor.y + anchor.height + POPUP_PADDING + height / 2
        } else {
            anchor.y - POPUP_PADDING - height / 2
        }
        val minimum = height / 2 + topInset + SCREEN_BORDER_PADDING
        val maximum = screenHeight - bottomInset - height / 2 - SCREEN_BORDER_PADDING
        return raw.coerceIn(minimum, maximum.coerceAtLeast(minimum))
    }

    private fun spaceAbove(): Double = anchor.y - topInset - POPUP_PADDING
    private fun spaceBelow(): Double =
        screenHeight - bottomInset - anchor.y - anchor.height - POPUP_PADDING

    private companion object {
        const val POPUP_PADDING = 4.0
        const val SCREEN_BORDER_PADDING = 6.0
    }
}
