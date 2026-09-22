//
//  TextScanner.kt
//  Donguri
//
//  The scan-window and sentence-extraction halves of Hoshi Reader's
//  selection.js, ported to Kotlin.
//
//  Hoshi Reader (and Donguri on iOS) run selection.js inside the webview that
//  renders the text, because neither UIKit's nor SwiftUI's text views expose
//  per-character hit testing. Compose does — `TextLayoutResult` maps a tap
//  straight to a character offset — so on Android the post text stays a native
//  `Text` and only the scanning logic has to come across. The delimiter tables
//  and the bracket-balancing pass are selection.js's, character for character,
//  so a tap resolves to the same word and the same sentence on both platforms.
//
//  Copyright © 2026 Manhhao.
//  SPDX-License-Identifier: GPL-3.0-or-later
//
package world.wumbo.donguri.lookup

/// What a tap resolved to: the text the dictionary should scan, and the
/// sentence around it for the Anki sentence field.
data class TextSelection(
    /// Up to `maxLength` characters starting at the tapped character, stopping
    /// at the first scan boundary.
    val text: String,
    val sentence: String,
    /// Offset of the tapped character within the source text.
    val offset: Int,
)

object TextScanner {
    private const val SCAN_DELIMITERS = "。、！？…‥「」『』（）()【】〈〉《》〔〕｛｝{}［］[]・：；:;，,.─\n\r"
    private const val SENTENCE_DELIMITERS = "。！？.!?\n\r"
    private const val TRAILING_SENTENCE_CHARS = "。、！？…‥」』）)】〉》〕｝}］]"

    private val BRACKETS = mapOf(
        '「' to '」', '『' to '』', '（' to '）', '(' to ')', '【' to '】',
        '〈' to '〉', '《' to '》', '〔' to '〕', '｛' to '｝', '{' to '}',
        '［' to '］', '[' to ']',
    )

    // https://github.com/yomidevs/yomitan/blob/master/ext/js/language/ja/japanese.js
    private val JAPANESE_RANGES = listOf(
        0x3040..0x309f, // Hiragana
        0x30a0..0x30ff, // Katakana
        0x4e00..0x9fff, // CJK unified ideographs
        0x3400..0x4dbf, // CJK unified ideographs extension A
        0x20000..0x2a6df, // CJK unified ideographs extension B
        0xf900..0xfaff, // CJK compatibility ideographs
        0xff66..0xff9f, // Halfwidth katakana
        0x30fb..0x30fc, // Katakana punctuation
        0xff61..0xff65, // Kana punctuation
        0x3000..0x303f, // CJK punctuation
        0xff00..0xffef, // Fullwidth characters
    )

    fun isCodePointJapanese(codePoint: Int): Boolean =
        JAPANESE_RANGES.any { codePoint in it }

    private fun isScanBoundary(char: Char, scanNonJapaneseText: Boolean): Boolean =
        char.isWhitespace() ||
            char == '　' ||
            SCAN_DELIMITERS.contains(char) ||
            (!scanNonJapaneseText && !isCodePointJapanese(char.code))

    /// Resolves a tap at `offset` into the text a lookup should be run against,
    /// or null when the tapped character is itself a boundary.
    fun select(
        source: String,
        offset: Int,
        maxLength: Int,
        scanNonJapaneseText: Boolean,
    ): TextSelection? {
        if (offset !in source.indices) return null

        val builder = StringBuilder()
        var i = offset
        while (i < source.length && builder.length < maxLength) {
            val char = source[i]
            if (isScanBoundary(char, scanNonJapaneseText)) break
            builder.append(char)
            i++
        }
        if (builder.isEmpty()) return null

        return TextSelection(
            text = builder.toString(),
            sentence = sentenceAround(source, offset),
            offset = offset,
        )
    }

    /// The sentence containing `offset`, trimmed of brackets it does not close.
    fun sentenceAround(source: String, offset: Int): String {
        if (source.isEmpty()) return ""
        val anchor = offset.coerceIn(0, source.length)

        var start = 0
        for (i in anchor - 1 downTo 0) {
            if (SENTENCE_DELIMITERS.contains(source[i])) {
                start = i + 1
                break
            }
        }

        var end = source.length
        for (i in anchor until source.length) {
            if (SENTENCE_DELIMITERS.contains(source[i])) {
                var stop = i + 1
                while (stop < source.length && TRAILING_SENTENCE_CHARS.contains(source[stop])) {
                    stop++
                }
                end = stop
                break
            }
        }

        return balanceBrackets(source.substring(start, end).trim())
    }

    /// Drops leading opens and trailing closes the sentence never pairs up —
    /// a sentence sliced out of a quoted passage otherwise carries half a
    /// bracket into the Anki card.
    private fun balanceBrackets(sentence: String): String {
        if (sentence.isEmpty()) return sentence

        val openBrackets = BRACKETS.keys
        val closeBrackets = BRACKETS.values.toSet()
        val stack = ArrayDeque<Char>()
        val unmatchedClose = ArrayDeque<Char>()

        for (char in sentence) {
            when {
                char in openBrackets -> stack.addLast(char)
                char in closeBrackets ->
                    if (stack.isNotEmpty() && BRACKETS[stack.last()] == char) {
                        stack.removeLast()
                    } else {
                        unmatchedClose.addLast(char)
                    }
            }
        }

        var startSlice = 0
        while (stack.isNotEmpty() && startSlice < sentence.length - 1) {
            // The stack holds unmatched opens in the order they appeared.
            if (stack.first() == sentence[startSlice]) stack.removeFirst() else break
            startSlice++
        }

        var endSlice = sentence.length - 1
        var endIdx = sentence.length - 1
        while (unmatchedClose.isNotEmpty() && endIdx > startSlice) {
            if (unmatchedClose.last() == sentence[endIdx]) {
                unmatchedClose.removeLast()
                endSlice = endIdx - 1
            } else if (!SENTENCE_DELIMITERS.contains(sentence[endIdx])) {
                break
            }
            endIdx--
        }

        if (endSlice < startSlice) return ""
        return sentence.substring(startSlice, endSlice + 1).trim()
    }
}
