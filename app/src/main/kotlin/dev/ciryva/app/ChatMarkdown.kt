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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val markdownText = Color(0xFFF7E7E2)
private val markdownMuted = Color(0xFFCDAFB8)
private val markdownHighlight = Color(0xFFF1A9B1)
private val markdownCode = Color(0xFF261D28)

/** Render provider text as native Compose elements, never raw HTML.
 * Original Markdown remains unchanged in controller state for copying.
 * Supports headings, paragraphs, inline emphasis, lists, quotes, fenced
 * code, dividers and simple pipe tables. More GFM features are later work.
 */
@Composable
fun ChatMarkdown(markdown: String) {
    val blocks = remember(markdown) { ChatMarkdownParser.parse(markdown) }
    SelectionContainer {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            blocks.forEach { block ->
                when (block.kind) {
                    "code" -> Surface(color = markdownCode, shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            if (block.info.isNotBlank()) {
                                Text(block.info, fontSize = 11.sp, color = markdownHighlight)
                                Spacer(Modifier.height(8.dp))
                            }
                            Text(block.text, fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp, lineHeight = 18.sp, color = markdownText,
                                modifier = Modifier.horizontalScroll(rememberScrollState()))
                        }
                    }
                    "quote" -> Row(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.width(3.dp).height(38.dp).background(markdownHighlight))
                        Spacer(Modifier.width(10.dp))
                        Text(styledText(block.text), color = markdownMuted,
                            fontSize = 14.sp, lineHeight = 21.sp)
                    }
                    "divider" -> HorizontalDivider(
                        color = markdownHighlight.copy(alpha = 0.3f))
                    "title" -> Text(styledText(block.text), fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold, lineHeight = 27.sp,
                        color = markdownText)
                    "heading" -> Text(styledText(block.text), fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold, lineHeight = 24.sp,
                        color = markdownText)
                    "list" -> Text(styledText(block.text), fontSize = 14.sp,
                        lineHeight = 23.sp, color = markdownText)
                    "table" -> Surface(color = markdownCode,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.horizontalScroll(rememberScrollState()).padding(9.dp)) {
                            block.text.lines().filterNot { line ->
                                line.trim().trim('|').split('|').all { cell ->
                                    cell.trim().matches(Regex("^:?-{3,}:?$"))
                                }
                            }.forEachIndexed { index, line ->
                                Row {
                                    line.trim().trim('|').split('|').take(6).forEach { cell ->
                                        Text(styledText(cell.trim()),
                                            fontWeight = if (index == 0)
                                                FontWeight.SemiBold else FontWeight.Normal,
                                            fontSize = 12.sp, color = markdownText,
                                            modifier = Modifier.width(130.dp).padding(5.dp))
                                    }
                                }
                                if (index == 0) HorizontalDivider(
                                    color = markdownHighlight.copy(alpha = 0.18f))
                            }
                        }
                    }
                    else -> Text(styledText(block.text), fontSize = 15.sp,
                        lineHeight = 23.sp, color = markdownText)
                }
            }
        }
    }
}

data class ChatMarkdownBlock(val kind: String, val text: String, val info: String = "")

/** Pure parser for stable offline JVM regression tests. */
object ChatMarkdownParser {
    fun parse(raw: String): List<ChatMarkdownBlock> {
        val lines = raw.replace("\r\n", "\n").lines()
        val blocks = mutableListOf<ChatMarkdownBlock>()
        val fence = 96.toChar().toString().repeat(3)
        var index = 0
        while (index < lines.size) {
            val trimmed = lines[index].trim()
            if (trimmed.isEmpty()) { index++; continue }
            if (trimmed.startsWith(fence)) {
                val language = trimmed.removePrefix(fence).take(32).trim()
                val contents = mutableListOf<String>()
                index++
                while (index < lines.size && !lines[index].trim().startsWith(fence)) {
                    contents += lines[index++]
                }
                if (index < lines.size) index++
                blocks += ChatMarkdownBlock("code", contents.joinToString("\n"), language)
                continue
            }
            if (trimmed.matches(Regex("^(-{3,}|\\*{3,}|_{3,})$"))) {
                blocks += ChatMarkdownBlock("divider", "")
                index++; continue
            }
            if (trimmed.startsWith("|") && trimmed.count { it == '|' } >= 2) {
                val rows = mutableListOf<String>()
                while (index < lines.size && lines[index].trim().startsWith("|")) {
                    rows += lines[index++]
                }
                blocks += ChatMarkdownBlock("table", rows.joinToString("\n"))
                continue
            }
            val heading = Regex("^#{1,6}\\s+").find(trimmed)
            if (heading != null) {
                blocks += ChatMarkdownBlock(
                    if (heading.value.count { it == '#' } == 1) "title" else "heading",
                    trimmed.removePrefix(heading.value))
                index++; continue
            }
            if (trimmed.startsWith("> ")) {
                val quotes = mutableListOf<String>()
                while (index < lines.size && lines[index].trim().startsWith("> ")) {
                    quotes += lines[index++].trim().removePrefix("> ")
                }
                blocks += ChatMarkdownBlock("quote", quotes.joinToString("\n"))
                continue
            }
            val bullet = Regex("^([-*+]\\s+|\\d+[.)]\\s+).*")
            if (bullet.matches(trimmed)) {
                val items = mutableListOf<String>()
                while (index < lines.size && bullet.matches(lines[index].trim())) {
                    val item = lines[index++].trim()
                    items += item.replaceFirst(Regex("^[-*+]\\s+"), "• ")
                }
                blocks += ChatMarkdownBlock("list", items.joinToString("\n"))
                continue
            }
            val para = mutableListOf(trimmed)
            index++
            while (index < lines.size && lines[index].isNotBlank()) {
                val candidate = lines[index].trim()
                if (candidate.startsWith(fence) || candidate.startsWith("# ") ||
                    candidate.startsWith("## ") || candidate.startsWith("> ") ||
                    candidate.startsWith("|") || bullet.matches(candidate)) break
                para += candidate
                index++
            }
            blocks += ChatMarkdownBlock("paragraph", para.joinToString("\n"))
        }
        return blocks
    }
}

private val inlinePattern = Regex(
    "(\\*\\*[^*\\n]+\\*\\*|" + 96.toChar() + "[^" + 96.toChar() +
        "\\n]+" + 96.toChar() + "|\\*[^*\\n]+\\*)"
)

private fun styledText(raw: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    inlinePattern.findAll(raw).forEach { match ->
        append(raw.substring(cursor, match.range.first))
        val token = match.value
        when {
            token.startsWith("**") ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(token.removeSurrounding("**"))
                }
            token.startsWith(96.toChar()) ->
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace,
                    color = markdownHighlight)) {
                    append(token.trim(96.toChar()))
                }
            else -> withStyle(SpanStyle(fontFamily = FontFamily.Serif)) {
                append(token.removeSurrounding("*"))
            }
        }
        cursor = match.range.last + 1
    }
    append(raw.substring(cursor))
}
