package dev.ciryva.app

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Computed once per selected palette; every screen receives the same four tokens. */
internal data class ChatUiColors(
    val background: Color,
    val panel: Color,
    val accent: Color,
    val text: Color,
) {
    val elevated: Color get() = mix(panel, text, if (light) .07f else .09f)
    val bubble: Color get() = mix(panel, accent, .16f)
    val muted: Color get() = mix(text, panel, .30f)
    val border: Color get() = mix(accent, panel, .48f)
    val light: Boolean get() = background.luminance() > .48f
    // Select the foreground using actual WCAG contrast. A medium-grey
    // accent can fail badly under a luminance-only boolean cutoff.
    val onAccent: Color get() =
        if (colorContrast(Color.Black, accent) >= colorContrast(Color.White, accent))
            Color.Black else Color.White
}

internal fun ChatThemeTokens.toUiColors(): ChatUiColors = ChatUiColors(
    background = hexComposeColor(background),
    panel = hexComposeColor(panel),
    accent = hexComposeColor(accent),
    text = hexComposeColor(text),
)

internal fun hexComposeColor(hex: String): Color {
    val normalized = HexThemeInput.complete(hex) ?: "#11191B"
    return Color((0xFF000000L or normalized.substring(1).toLong(16)).toInt())
}

internal fun mix(a: Color, b: Color, weight: Float): Color {
    val t = weight.coerceIn(0f, 1f)
    return Color(
        red = a.red * (1f - t) + b.red * t,
        green = a.green * (1f - t) + b.green * t,
        blue = a.blue * (1f - t) + b.blue * t,
        alpha = 1f,
    )
}

internal val LocalChatColors = compositionLocalOf {
    ChatThemePalettes.carbon.tokens.toUiColors()
}

/** WCAG relative luminance contrast; applies to buttons and preview warnings. */
internal fun colorContrast(a: Color, b: Color): Float {
    val x = a.luminance()
    val y = b.luminance()
    return (maxOf(x, y) + .05f) / (minOf(x, y) + .05f)
}
