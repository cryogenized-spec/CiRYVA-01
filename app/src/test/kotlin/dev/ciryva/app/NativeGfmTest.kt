package dev.ciryva.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.commonmark.node.Node
import org.commonmark.node.Link
import org.commonmark.node.HtmlBlock
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.task.list.items.TaskListItemMarker

class NativeGfmTest {
    private fun allNodes(source: String): List<Node> {
        val result = mutableListOf<Node>()
        fun walk(node: Node) {
            result.add(node)
            NativeGfm.children(node).forEach(::walk)
        }
        walk(NativeGfm.parse(source))
        return result
    }

    @Test fun gfmParsesTablesWithEscapedPipesAndNestedInlineMarkup() {
        val nodes = allNodes("| Part | Status |\n| --- | :---: |\n| A \\| B | **Ready** |")
        assertTrue(nodes.any { it is TableBlock })
        assertTrue(nodes.any { NativeGfm.plain(it) == "A | B" })
        assertTrue(nodes.any { NativeGfm.plain(it) == "Ready" })
    }

    @Test fun gfmTaskCheckboxesStrikeAndNestedBullets() {
        val nodes = allNodes("- [x] ~~Closed~~\n- [ ] Open\n  - nested")
        assertTrue(nodes.any { it is TaskListItemMarker && it.isChecked })
        assertTrue(nodes.any { it is TaskListItemMarker && !it.isChecked })
        assertTrue(nodes.any { it is Strikethrough })
    }

    @Test fun gfmAutoLinksAndExplicitLinksUseOnlySafeSchemes() {
        val parsed = allNodes("https://example.com\n\n[site](https://example.org)")
        assertTrue(parsed.any { it is Link && it.destination == "https://example.com" })
        assertTrue(parsed.any { it is Link && it.destination == "https://example.org" })
        assertEquals(null, safeMarkdownUrl("javascript:alert(1)"))
        assertEquals(null, safeMarkdownUrl("data:text/html,<script>"))
        assertEquals(null, safeMarkdownUrl("https://name:password@example.com"))
    }

    @Test fun htmlIsAParsedNodeButNotExecutable() {
        val nodes = allNodes("<script>alert(1)</script>\n\n**visible**")
        assertTrue(nodes.any { it is HtmlBlock })
        assertFalse(NativeGfm.plain(nodes.first()).contains("window.location"))
    }

    @Test fun gfmRendersMultiLineFencesAndHeadingsAsNodes() {
        val fence = 96.toChar().toString().repeat(3)
        val nodes = allNodes("# Technical\n\n" + fence +
            "kotlin\nprintln(42)\n" + fence)
        assertTrue(nodes.any { it.javaClass.simpleName == "Heading" })
        assertTrue(nodes.any { it.javaClass.simpleName == "FencedCodeBlock" })
        assertNotNull(nodes.firstOrNull())
    }
}
