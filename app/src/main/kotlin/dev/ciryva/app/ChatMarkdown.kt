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
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.URI

private val markdownText = Color(0xFFF7E7E2)
private val markdownMuted = Color(0xFFCDAFB8)
private val markdownHighlight = Color(0xFFF1A9B1)
private val markdownCode = Color(0xFF261D28)

/** Render provider text as native Compose elements, never raw HTML.
 * Original Markdown remains unchanged in controller state for copying.
 * Supports headings, paragraphs, inline emphasis, lists, quotes, fenced
 * code, dividers, safe HTTPS/HTTP links, strikethrough, task lists and simple
 * pipe tables. More GFM features are later work.
 */
@Composable
fun ChatMarkdown(markdown: String, readingFont: FontFamily = FontFamily.Default) {
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
                            fontSize = 14.sp, lineHeight = 21.sp, fontFamily = readingFont)
                    }
                    "divider" -> HorizontalDivider(
                        color = markdownHighlight.copy(alpha = 0.3f))
                    "title" -> Text(styledText(block.text), fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold, lineHeight = 27.sp,
                        fontFamily = readingFont,
                        color = markdownText)
                    "heading" -> Text(styledText(block.text), fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold, lineHeight = 24.sp,
                        fontFamily = readingFont,
                        color = markdownText)
                    "list" -> Text(styledText(block.text), fontSize = 14.sp,
                        lineHeight = 23.sp, color = markdownText, fontFamily = readingFont)
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
                                    line.trim().trim('|').split('|').forEach { cell ->
                                        Text(styledText(cell.trim()),
                                            fontWeight = if (index == 0)
                                                FontWeight.SemiBold else FontWeight.Normal,
                                            fontSize = 12.sp, color = markdownText,
                                            fontFamily = readingFont,
                                            modifier = Modifier.width(130.dp).padding(5.dp))
                                    }
                                }
                                if (index == 0) HorizontalDivider(
                                    color = markdownHighlight.copy(alpha = 0.18f))
                            }
                        }
                    }
                    else -> Text(styledText(block.text), fontSize = 15.sp,
                        lineHeight = 23.sp, color = markdownText, fontFamily = readingFont)
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
        val headingPattern = Regex("^#{1,6}\\s+")
        val dividerPattern = Regex("^(-{3,}|\\*{3,}|_{3,})$")
        var index = 0
        while (index < lines.size) {
            val trimmed = lines[index].trim()
            if (trimmed.isEmpty()) { index++; continue }
            if (trimmed.startsWith(fence)) {
                val opening = trimmed.takeWhile { it == 96.toChar() }
                val language = trimmed.removePrefix(opening).take(32).trim()
                val closingFence = Regex("^" + 96.toChar() + "{" +
                    opening.length + ",}\\s*$")
                val contents = mutableListOf<String>()
                index++
                // Four-tick outer fences must not terminate at an inner triple fence.
                while (index < lines.size && !closingFence.matches(lines[index].trim())) {
                    contents += lines[index++]
                }
                if (index < lines.size) index++
                blocks += ChatMarkdownBlock("code", contents.joinToString("\n"), language)
                continue
            }
            if (dividerPattern.matches(trimmed)) {
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
            val heading = headingPattern.find(trimmed)
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
                while (index < lines.size) {
                    val original = lines[index]
                    val item = original.trim()
                    if (bullet.matches(item)) {
                        val depth = (original.takeWhile { it == ' ' }.length / 2)
                            .coerceIn(0, 4)
                        val indent = "  ".repeat(depth)
                        val task = Regex("^[-*+]\\s+\\[([ xX])]\\s+").find(item)
                        items += when {
                            task != null -> indent +
                                (if (task.groupValues[1].equals("x", ignoreCase = true))
                                    "☑ " else "☐ ") + item.removePrefix(task.value)
                            else -> indent + item.replaceFirst(Regex("^[-*+]\\s+"), "• ")
                        }
                        index++
                    } else if (item.isNotEmpty() && original.first().isWhitespace() &&
                        !item.startsWith(fence) &&
                        !item.startsWith("> ") && !item.startsWith("|") &&
                        !headingPattern.containsMatchIn(item) &&
                        !dividerPattern.matches(item)) {
                        // An indented wrapped line belongs to the preceding list item.
                        // Preserve it here instead of creating an unrelated paragraph.
                        items[items.lastIndex] += "\n" + item
                        index++
                    } else break
                }
                blocks += ChatMarkdownBlock("list", items.joinToString("\n"))
                continue
            }
            val para = mutableListOf(trimmed)
            index++
            while (index < lines.size && lines[index].isNotBlank()) {
                val candidate = lines[index].trim()
                if (candidate.startsWith(fence) ||
                    headingPattern.containsMatchIn(candidate) ||
                    dividerPattern.matches(candidate) ||
                    candidate.startsWith("> ") || candidate.startsWith("|") ||
                    bullet.matches(candidate)) break
                para += candidate
                index++
            }
            blocks += ChatMarkdownBlock("paragraph", para.joinToString("\n"))
        }
        return blocks
    }
}

/**
 * Conservatively allow only ordinary web links, with a parseable host.
 * No javascript:, data:, file:, intent: or browser-embedded credentials.
 * Unsafe/unsupported Markdown links remain literal visible text.
 */
internal fun safeMarkdownUrl(candidate: String): String? {
    if (candidate.length !in 1..2_048 || candidate.any {
            it.isWhitespace() || it.isISOControl() || it == '\\'
        }) return null
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    if (!uri.isAbsolute || !(uri.scheme.equals("https", ignoreCase = true) ||
            uri.scheme.equals("http", ignoreCase = true))) return null
    if (uri.host.isNullOrBlank() || uri.userInfo != null ||
        uri.port > 65535 || uri.port == 0) return null
    return candidate
}

/**
 * Converts safe inline Markdown into native Compose spans. This never
 * interprets HTML, and LinkAnnotation opens only validated web URLs.
 */
internal fun styledText(raw: String): AnnotatedString = buildAnnotatedString {
    val tick = 96.toChar()
    val inlinePattern = Regex(
        "\\[([^\\[\\]\\n]{1,256})]\\(([^\\s()\\n]+)\\)" +
            "|~~[^~\\n]+~~|\\*\\*[^*\\n]+\\*\\*" +
            "|" + tick + "[^" + tick + "\\n]+" + tick +
            "|\\*[^*\\n]+\\*"
    )
    var cursor = 0
    inlinePattern.findAll(raw).forEach { match ->
        append(raw.substring(cursor, match.range.first))
        val token = match.value
        when {
            token.startsWith("[") -> {
                val label = match.groups[1]?.value.orEmpty()
                val url = match.groups[2]?.value?.let(::safeMarkdownUrl)
                if (url == null) append(token)
                else withLink(
                    LinkAnnotation.Url(
                        url = url,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = markdownHighlight,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ),
                    ),
                ) { append(label) }
            }
            token.startsWith("~~") ->
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(token.removeSurrounding("~~"))
                }
            token.startsWith("**") ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(token.removeSurrounding("**"))
                }
            token.startsWith(tick) ->
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace,
                    color = markdownHighlight)) {
                    append(token.trim(tick))
                }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(token.removeSurrounding("*"))
            }
        }
        cursor = match.range.last + 1
    }
    append(raw.substring(cursor))
}
