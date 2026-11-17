package dev.ciryva.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Two balanced columns on a phone; the final odd preset remains full width. */
@Composable
internal fun ChatThemePalettePicker(
    selectedId: String,
    custom: ChatThemeTokens,
    onSelect: (String) -> Unit,
    onCustomChange: (ChatThemeTokens) -> Unit,
) {
    val ui = LocalChatColors.current
    Column(
        modifier = Modifier.fillMaxWidth().testTag("theme-palette-picker"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Colour palettes", color = ui.text, fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold)
        Text(
            "Eleven presets, including Elara Night, or choose Custom colours.",
            color = ui.muted, fontSize = 12.sp, lineHeight = 18.sp,
        )
        ChatThemePalettes.presets.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                pair.forEach { option ->
                    val selected = selectedId == option.id
                    val p = option.tokens.toUiColors()
                    Surface(
                        modifier = Modifier.weight(1f)
                            .heightIn(min = 128.dp)
                            .clickable { onSelect(option.id) }
                            .testTag("theme-preset-" + option.id),
                        color = p.background,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(if (selected) 2.dp else 1.dp,
                            if (selected) p.accent else p.muted.copy(alpha = .30f)),
                    ) {
                        Column(Modifier.padding(11.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                option.name, color = p.text,
                                fontSize = 12.sp, lineHeight = 16.sp,
                                fontWeight = if (selected) FontWeight.Bold
                                    else FontWeight.Medium,
                                minLines = 2,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(p.background, p.panel, p.accent, p.text).forEach { color ->
                                    Box(Modifier.weight(1f).size(20.dp)
                                        .background(color, RoundedCornerShape(4.dp)))
                                }
                            }
                            Text(if (selected) "✓ Selected" else option.description,
                                color = p.text.copy(alpha = .83f),
                                fontSize = 10.sp, maxLines = 2, lineHeight = 13.sp)
                        }
                    }
                }
            }
        }
        Surface(
            color = if (selectedId == ChatThemePalettes.CUSTOM) ui.bubble else ui.panel,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                if (selectedId == ChatThemePalettes.CUSTOM) 2.dp else 1.dp,
                if (selectedId == ChatThemePalettes.CUSTOM) ui.accent else ui.border,
            ),
            modifier = Modifier.fillMaxWidth()
                .clickable { onSelect(ChatThemePalettes.CUSTOM) }
                .testTag("theme-preset-custom"),
        ) {
            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Custom colours", color = ui.text, fontWeight = FontWeight.SemiBold)
                    Text("Four hex values · #RRGGBB", color = ui.muted, fontSize = 12.sp)
                }
                Text(if (selectedId == ChatThemePalettes.CUSTOM) "✓ Selected" else "Edit",
                    color = ui.accent, fontSize = 12.sp)
            }
        }
        if (selectedId == ChatThemePalettes.CUSTOM) {
            Column(
                modifier = Modifier.fillMaxWidth().testTag("theme-custom-editor"),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                ThemeHexField("Background", "background", custom.background) {
                    onCustomChange(custom.copy(background = it))
                }
                ThemeHexField("Panel / message surface", "panel", custom.panel) {
                    onCustomChange(custom.copy(panel = it))
                }
                ThemeHexField("Accent / buttons", "accent", custom.accent) {
                    onCustomChange(custom.copy(accent = it))
                }
                ThemeHexField("Text", "text", custom.text) {
                    onCustomChange(custom.copy(text = it))
                }
                val colors = custom.validated().toUiColors()
                val contrastPanel = colorContrast(colors.text, colors.panel)
                val contrastBackground = colorContrast(colors.text, colors.background)
                if (contrastPanel < 4.5f || contrastBackground < 4.5f) {
                    Text(
                        "Contrast warning: your text and background colours may be hard to read. " +
                            "Recommended text contrast is 4.5:1 or higher.",
                        color = ui.accent, fontSize = 12.sp, lineHeight = 17.sp,
                        modifier = Modifier.testTag("theme-contrast-warning"),
                    )
                }
                Text(
                    "Pasting strips rich-text tags, whitespace and metadata. " +
                        "Only a single six-digit sRGB value is saved; incomplete or invalid " +
                        "input never changes your active theme.",
                    color = ui.muted, fontSize = 11.sp, lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun ThemeHexField(
    title: String,
    key: String,
    currentValue: String,
    onComplete: (String) -> Unit,
) {
    val ui = LocalChatColors.current
    var draft by rememberSaveable(currentValue) { mutableStateOf(currentValue) }
    var invalid by rememberSaveable(currentValue) { mutableStateOf(false) }
    OutlinedTextField(
        value = draft,
        onValueChange = { raw ->
            val cleaned = HexThemeInput.input(raw)
            if (cleaned == null) {
                invalid = true
            } else {
                draft = cleaned
                invalid = false
                HexThemeInput.complete(cleaned)?.let(onComplete)
            }
        },
        label = { Text(title + " · #RRGGBB") },
        singleLine = true,
        isError = invalid || !HexThemeInput.isComplete(draft),
        supportingText = {
            if (invalid) Text("Invalid or ambiguous paste. Use one #RRGGBB value.")
            else if (!HexThemeInput.isComplete(draft)) Text("Enter all six hexadecimal digits.")
        },
        leadingIcon = {
            Box(Modifier.size(23.dp)
                .background(hexComposeColor(currentValue), RoundedCornerShape(6.dp)))
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        modifier = Modifier.fillMaxWidth().testTag("theme-hex-" + key),
    )
}

