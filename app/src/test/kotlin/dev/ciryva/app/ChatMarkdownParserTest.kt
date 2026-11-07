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

    @Test fun safeInlineLinksRenderTextAndRejectDangerousSchemes() {
        val sample = styledText(
            "Read [the docs](https://example.com/help) or " +
                "[do not run](javascript:alert)."
        )
        assertTrue(sample.text.contains("Read the docs or"))
        assertTrue(sample.text.contains("[do not run](javascript:alert)."))
        assertTrue(sample.getLinkAnnotations(0, sample.length).size == 1)
        val link = sample.getLinkAnnotations(0, sample.length).single().item
        assertTrue(link is androidx.compose.ui.text.LinkAnnotation.Url)
        assertEquals("https://example.com/help",
            (link as androidx.compose.ui.text.LinkAnnotation.Url).url)
        assertEquals(null, safeMarkdownUrl("file:///tmp/secret.txt"))
        assertEquals(null, safeMarkdownUrl("https://name:pass@example.com/path"))
        assertEquals(null, safeMarkdownUrl("//example.com/path"))
        assertEquals(null, safeMarkdownUrl("data:text/html,x"))
        assertEquals(null, safeMarkdownUrl("https://"))
        assertEquals("http://example.com/ok", safeMarkdownUrl("http://example.com/ok"))
    }

    @Test fun taskListsNestedIndentAndStrikeThroughAreVisible() {
        val result = ChatMarkdownParser.parse(
            "- [ ] Review\n  - [x] Completed\n- Keep **important**\n"
        )
        assertEquals("list", result.first().kind)
        assertTrue(result.first().text.contains("☐ Review"))
        assertTrue(result.first().text.contains("  ☑ Completed"))
        assertEquals("remove and keep", styledText("~~remove~~ and *keep*").text)
        assertEquals("<script>alert(1)</script>",
            styledText("<script>alert(1)</script>").text)
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

    @Test fun headingsOfAllLevelsAndDividersSplitAdjacentParagraphs() {
        val blocks = ChatMarkdownParser.parse(
            "Intro\n### Third-level heading\nBody\n###### Sixth-level heading\n" +
                "After heading\n***\nFinal paragraph"
        )
        assertEquals(
            listOf("paragraph", "heading", "paragraph", "heading",
                "paragraph", "divider", "paragraph"),
            blocks.map { it.kind },
        )
        assertEquals("Third-level heading", blocks[1].text)
        assertEquals("Sixth-level heading", blocks[3].text)
        assertEquals("Final paragraph", blocks.last().text)
    }

    @Test fun indentedWrappedLinesStayWithNestedAndNumberedListItems() {
        val blocks = ChatMarkdownParser.parse(
            "- First item\n  wrapped explanation\n  - Nested item\n" +
                "2) Numbered item\n  second line\n\nAfter the list"
        )
        assertEquals(listOf("list", "paragraph"), blocks.map { it.kind })
        assertEquals(
            "• First item\nwrapped explanation\n  • Nested item\n" +
                "2) Numbered item\nsecond line",
            blocks[0].text,
        )
        assertEquals("After the list", blocks[1].text)
    }
}
