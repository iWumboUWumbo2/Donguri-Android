package world.wumbo.donguri.features.anki

import org.junit.Assert.assertEquals
import org.junit.Test

class AnkiHandlebarRendererTest {

    private val payload = AnkiMiningPayload(
        expression = "食べる",
        reading = "たべる",
        matched = "食べさせられた",
        glossary = """<li data-dictionary="JMdict"><i>(JMdict)</i> to eat</li>""",
        glossaryFirst = """<li data-dictionary="JMdict"><i>(JMdict)</i> to eat</li>""",
        singleGlossaries = mapOf("大辞林" to "<li>たべる</li>"),
        selectedDictionary = "大辞林",
        popupSelectionText = "食べさせられた",
        frequenciesHtml = "<span>1200</span>",
    )

    private val context = AnkiMiningContext(
        sentence = "昨日は食べさせられたよ。",
        documentTitle = "スレタイ",
        sentenceOffset = 3,
    )

    @Test
    fun `plain handlebars are substituted`() {
        assertEquals("食べる", AnkiHandlebarRenderer.render("{expression}", payload, context))
        assertEquals("たべる", AnkiHandlebarRenderer.render("{reading}", payload, context))
        assertEquals("スレタイ", AnkiHandlebarRenderer.render("{document-title}", payload, context))
    }

    @Test
    fun `an unknown handlebar renders empty rather than literally`() {
        assertEquals("", AnkiHandlebarRenderer.render("{no-such-thing}", payload, context))
    }

    @Test
    fun `the sentence bolds the matched word`() {
        assertEquals(
            "昨日は<b>食べさせられた</b>よ。",
            AnkiHandlebarRenderer.render("{sentence}", payload, context),
        )
    }

    @Test
    fun `cloze parts split the sentence around the match`() {
        assertEquals("昨日は", AnkiHandlebarRenderer.render("{cloze-prefix}", payload, context))
        assertEquals("食べさせられた", AnkiHandlebarRenderer.render("{cloze-body}", payload, context))
        assertEquals("よ。", AnkiHandlebarRenderer.render("{cloze-suffix}", payload, context))
    }

    @Test
    fun `a wrong offset falls back to searching the sentence`() {
        val wrongOffset = context.copy(sentenceOffset = 99)

        assertEquals("昨日は", AnkiHandlebarRenderer.render("{cloze-prefix}", payload, wrongOffset))
    }

    @Test
    fun `a match that is not in the sentence leaves the sentence whole`() {
        val mismatched = payload.copy(matched = "まったく別の語")

        assertEquals("昨日は食べさせられたよ。", AnkiHandlebarRenderer.render("{sentence}", mismatched, context))
    }

    @Test
    fun `the selected glossary wins over the first one`() {
        assertEquals("<li>たべる</li>", AnkiHandlebarRenderer.render("{selected-glossary-fallback}", payload, context))
    }

    @Test
    fun `the selected glossary falls back when that dictionary has no entry`() {
        val noSelection = payload.copy(selectedDictionary = "存在しない辞書")

        assertEquals(
            payload.glossaryFirst,
            AnkiHandlebarRenderer.render("{selected-glossary-fallback}", noSelection, context),
        )
    }

    @Test
    fun `a dictionary revision suffix still matches its glossary`() {
        val revisioned = payload.copy(selectedDictionary = "大辞林 [2026-01-01]")

        assertEquals(
            "<li>たべる</li>",
            AnkiHandlebarRenderer.render("{selected-glossary-fallback}", revisioned, context),
        )
    }

    @Test
    fun `brief strips the dictionary header`() {
        assertEquals(
            """<li data-dictionary="JMdict">to eat</li>""",
            AnkiHandlebarRenderer.render("{glossary-brief}", payload, context),
        )
    }

    @Test
    fun `tags collapse whitespace into underscores`() {
        assertEquals(
            "donguri スレタイ",
            AnkiHandlebarRenderer.renderTags("donguri {document-title}", payload, context),
        )
        assertEquals(
            "昨日は_<b>食べさせられた</b>_よ。",
            AnkiHandlebarRenderer.renderTags("{sentence}", payload, context.copy(sentence = "昨日は 食べさせられた よ。")),
        )
    }

    @Test
    fun `field mappings are filtered to the note type's own fields`() {
        val noteType = AnkiNoteType(id = 1, name = "Lapis", fields = listOf("Expression", "Sentence"))
        val mappings = mapOf(
            "Expression" to "{expression}",
            "Sentence" to "{sentence}",
            "Gone" to "{reading}",
        )

        assertEquals(setOf("Expression", "Sentence"), mappings.activeAnkiFieldMappings(noteType).keys)
    }

    @Test
    fun `a known note type seeds its template mappings`() {
        val noteType = AnkiNoteType(
            id = 1,
            name = "Lapis",
            fields = listOf("Expression", "Sentence", "SomethingCustom"),
        )
        val mappings = AnkiFieldTemplates.defaultMappings(noteType)

        assertEquals("{expression}", mappings["Expression"])
        assertEquals("{sentence}", mappings["Sentence"])
        // Fields the template does not know about are left unmapped.
        assertEquals(null, mappings["SomethingCustom"])
    }
}
