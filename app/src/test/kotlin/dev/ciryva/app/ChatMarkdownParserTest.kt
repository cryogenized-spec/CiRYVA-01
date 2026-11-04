package dev.ciryva.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkdownParserTest {
    @Test fun keepsHeadersParagraphListsCodeAndQuotesSeparate() {
        val fence = 96.toChar().toString().repeat(3)
        val input = listOf(
            "# Main heading", "",
            "Two **bold** words.", "",
            "- First", "- Second", "",
            fence + "kotlin",
            "println(1)",
            fence, "",
            "> A quotation", "",
            "| Name | Count |", "| --- | --- |", "| A | 2 |",
        ).joinToString("\n")
        val blocks = ChatMarkdownParser.parse(input)
        assertEquals(
            listOf("title", "paragraph", "list", "code", "quote", "table"),
            blocks.map { it.kind },
        )
        assertEquals("println(1)", blocks[3].text)
        assertEquals("kotlin", blocks[3].info)
        assertTrue(blocks[1].text.contains("**bold**"))
    }

    @Test fun rawHtmlRemainsInertTextNotRenderedMarkup() {
        val blocks = ChatMarkdownParser.parse("<script>alert(1)</script>")
        assertEquals("paragraph", blocks.single().kind)
        assertEquals("<script>alert(1)</script>", blocks.single().text)
    }

    @Test fun fourTickOuterFencePreservesInnerTripleTickExample() {
        val tick = 96.toChar().toString()
        val sample = listOf(
            tick.repeat(4) + "markdown",
            "Example:",
            tick.repeat(3) + "kotlin",
            "println(1)",
            tick.repeat(3),
            tick.repeat(4),
        ).joinToString("\n")
        val result = ChatMarkdownParser.parse(sample)
        assertEquals(1, result.size)
        assertEquals("code", result.single().kind)
        assertTrue(result.single().text.contains(tick.repeat(3) + "kotlin"))
        assertTrue(result.single().text.contains("println(1)"))
    }

}
