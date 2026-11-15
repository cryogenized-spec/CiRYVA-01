package dev.ciryva.app

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text as MdText
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

private val gfmInk = Color(0xFFF7E7E2)
private val gfmRose = Color(0xFFF1A9B1)
private val gfmMuted = Color(0xFFCDAFB8)
private val gfmCode = Color(0xFF261D28)

/**
 * Parse GitHub Flavored Markdown as an AST, not HTML. HTML remains inert text,
 * provider-authored remote images are references only (no automatic fetch).
 * The normalized AST is memoized by the message renderer.
 */
internal object NativeGfm {
    private val extensions: List<Extension> = listOf(
        TablesExtension.create(), StrikethroughExtension.create(),
        TaskListItemsExtension.create(), AutolinkExtension.create(),
    )
    private val parser: Parser = Parser.builder().extensions(extensions).build()
    fun parse(source: String): Node = parser.parse(source.take(160_000))
    fun children(node: Node): List<Node> {
        val items = mutableListOf<Node>()
        var current = node.firstChild
        while (current != null && items.size < 10_000) {
            items.add(current)
            current = current.next
        }
        return items
    }
    fun plain(node: Node): String {
        val out = StringBuilder()
        fun visit(part: Node, depth: Int) {
            if (depth > 40 || out.length > 160_000) return
            when (part) {
                is MdText -> out.append(part.literal)
                is Code -> out.append(part.literal)
                is HardLineBreak, is SoftLineBreak -> out.append('\n')
                else -> children(part).forEach { visit(it, depth + 1) }
            }
        }
        visit(node, 0)
        return out.toString().take(160_000)
    }
}

@Composable
internal fun NativeGfmMarkdown(
    markdown: String, font: FontFamily = FontFamily.Default, readingSizeSp: Int = 15,
) {
    val document = remember(markdown) { NativeGfm.parse(markdown) }
    SelectionContainer {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            NativeGfm.children(document).forEach { GfmBlock(it, font, 0, readingSizeSp) }
        }
    }
}

@Composable
private fun GfmBlock(node: Node, font: FontFamily, depth: Int, sizeSp: Int) {
    if (depth > 16) return
    when (node) {
        is Paragraph -> Text(
            GfmInline(node), color = gfmInk, fontFamily = font,
            fontSize = sizeSp.sp, lineHeight = (sizeSp + 8).sp,
            modifier = Modifier.fillMaxWidth(),
        )
        is Heading -> Text(
            GfmInline(node), color = gfmInk, fontFamily = font,
            fontSize = when (node.level) { 1 -> (sizeSp + 9).sp; 2 -> (sizeSp + 6).sp;
                3 -> (sizeSp + 3).sp; else -> (sizeSp + 1).sp },
            lineHeight = if (node.level <= 2) (sizeSp + 14).sp else (sizeSp + 9).sp,
            fontWeight = FontWeight.SemiBold,
        )
        is FencedCodeBlock -> GfmCode(node.literal, node.info, sizeSp)
        is IndentedCodeBlock -> GfmCode(node.literal, null, sizeSp)
        is ThematicBreak -> HorizontalDivider(color = gfmRose.copy(alpha = 0.32f))
        is BlockQuote -> Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(3.dp).height(34.dp).background(gfmRose))
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NativeGfm.children(node).forEach { GfmBlock(it, font, depth + 1, sizeSp) }
            }
        }
        is BulletList, is OrderedList -> {
            var number = (node as? OrderedList)?.startNumber ?: 1
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                NativeGfm.children(node).filterIsInstance<ListItem>().forEach { item ->
                    val task = NativeGfm.children(item).filterIsInstance<TaskListItemMarker>()
                        .firstOrNull()
                    val prefix = when {
                        task != null -> if (task.isChecked) "☑" else "☐"
                        node is OrderedList -> (number++).toString() + "."
                        else -> "•"
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(prefix, color = gfmRose, fontSize = 14.sp,
                            modifier = Modifier.width(24.dp))
                        Column(Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            NativeGfm.children(item).filterNot { it is TaskListItemMarker }
                                .forEach { GfmBlock(it, font, depth + 1, sizeSp) }
                        }
                    }
                }
            }
        }
        is TableBlock -> Surface(color = gfmCode, shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp)) {
                NativeGfm.children(node).forEach { section ->
                    val header = section is TableHead
                    NativeGfm.children(section).filterIsInstance<TableRow>().forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NativeGfm.children(row).forEach { cell ->
                                Text(GfmInline(cell), color = gfmInk, fontFamily = font,
                                    fontSize = (sizeSp - 2).coerceAtLeast(12).sp,
                                    lineHeight = (sizeSp + 4).sp,
                                    fontWeight = if (header) FontWeight.SemiBold
                                        else FontWeight.Normal,
                                    modifier = Modifier.width(140.dp).padding(6.dp))
                            }
                        }
                        if (header) HorizontalDivider(color = gfmRose.copy(alpha = 0.3f))
                    }
                }
            }
        }
        is HtmlBlock -> Text(node.literal, color = gfmMuted,
            fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        else -> NativeGfm.children(node).forEach { GfmBlock(it, font, depth + 1, sizeSp) }
    }
}

@Composable
private fun GfmCode(content: String, info: String?, sizeSp: Int) {
    Surface(color = gfmCode, shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            if (!info.isNullOrBlank()) {
                Text(info.take(30), fontSize = 11.sp, color = gfmRose)
                Spacer(Modifier.height(8.dp))
            }
            Text(content, fontFamily = FontFamily.Monospace,
                fontSize = (sizeSp - 2).coerceAtLeast(12).sp,
                lineHeight = (sizeSp + 4).sp, color = gfmInk,
                modifier = Modifier.horizontalScroll(rememberScrollState()))
        }
    }
}

/** Safe native spans with validated web URLs; inline HTML is never interpreted. */
internal fun GfmInline(parent: Node): AnnotatedString = buildAnnotatedString {
    fun visit(node: Node, depth: Int) {
        if (depth > 40) return
        fun descend() { NativeGfm.children(node).forEach { visit(it, depth + 1) } }
        when (node) {
            is MdText -> append(node.literal)
            is Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = gfmRose)) {
                append(node.literal)
            }
            is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { descend() }
            is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { descend() }
            is Strikethrough -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                descend()
            }
            is Link -> {
                val url = safeMarkdownUrl(node.destination)
                if (url != null) withLink(LinkAnnotation.Url(
                    url, styles = TextLinkStyles(style = SpanStyle(
                        color = gfmRose, textDecoration = TextDecoration.Underline,
                    )),
                )) { descend() } else descend()
            }
            is Image -> {
                val alt = NativeGfm.plain(node).ifBlank { "image" }
                val url = safeMarkdownUrl(node.destination)
                if (url != null) withLink(LinkAnnotation.Url(
                    url, styles = TextLinkStyles(style = SpanStyle(
                        color = gfmRose, textDecoration = TextDecoration.Underline,
                    )),
                )) { append("▧ " + alt) }
                else append("▧ " + alt)
            }
            is HtmlInline -> append(node.literal)
            is HardLineBreak -> append("\n")
            is SoftLineBreak -> append(" ")
            is TaskListItemMarker -> append(if (node.isChecked) "☑ " else "☐ ")
            else -> descend()
        }
    }
    NativeGfm.children(parent).forEach { visit(it, 0) }
}
