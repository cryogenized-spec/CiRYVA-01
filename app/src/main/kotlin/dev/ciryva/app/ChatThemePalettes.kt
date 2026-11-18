package dev.ciryva.app

import java.text.Normalizer
import java.util.Locale

/**
 * Four deliberately small, owner-editable theme tokens. Six-digit sRGB only:
 * alpha/transparency is not accepted because transparent text or surfaces
 * would make parts of the assistant unusable.
 */
internal data class ChatThemeTokens(
    val background: String,
    val panel: String,
    val accent: String,
    val text: String,
) {
    fun validated(fallback: ChatThemeTokens = ChatThemePalettes.carbon.tokens): ChatThemeTokens =
        ChatThemeTokens(
            HexThemeInput.complete(background) ?: fallback.background,
            HexThemeInput.complete(panel) ?: fallback.panel,
            HexThemeInput.complete(accent) ?: fallback.accent,
            HexThemeInput.complete(text) ?: fallback.text,
        )
}

internal data class ChatThemeOption(
    val id: String,
    val name: String,
    val description: String,
    val tokens: ChatThemeTokens,
)

/**
 * Eleven equal-width presets, including the current Elara-inspired dark chat palette.
 * The old burgundy/midnight/graphite IDs migrate deterministically.
 */
internal object ChatThemePalettes {
    const val CUSTOM = "custom"
    const val DEFAULT = "elara"
    val carbon = ChatThemeOption(
        "carbon", "Carbon & Sea Glass", "Quietly futuristic",
        ChatThemeTokens("#11191B", "#213034", "#71D2BF", "#F2F5F3"),
    )
    val presets: List<ChatThemeOption> = listOf(
        ChatThemeOption("elara", "Elara Night", "Midnight blue, soft light",
            ChatThemeTokens("#050507", "#15161F", "#6EA8FF", "#F7F8FF")),
        carbon,
        ChatThemeOption("obsidian", "Obsidian & Brass", "Warm and restrained",
            ChatThemeTokens("#111619", "#252B2C", "#D9B570", "#F5F1E9")),
        ChatThemeOption("coastal", "Coastal Slate", "Cool and focused",
            ChatThemeTokens("#172631", "#273C49", "#87BED7", "#F2F6F8")),
        ChatThemeOption("forest", "Forest & Bone", "Natural and calm",
            ChatThemeTokens("#18251F", "#2A3B30", "#AFD1A4", "#F1F0E5")),
        ChatThemeOption("porcelain", "Porcelain & Steel", "Spacious light theme",
            ChatThemeTokens("#F4F2EC", "#FFFFFF", "#326E7E", "#26343A")),
        ChatThemeOption("signal", "Graphite & Signal", "Minimal and high-contrast",
            ChatThemeTokens("#111315", "#25292B", "#B9E56C", "#F3F5F4")),
        ChatThemeOption("terracotta", "Terracotta & Cream", "Warm earth tones",
            ChatThemeTokens("#271A17", "#42302A", "#E5A17B", "#F8EDE5")),
        ChatThemeOption("glacier", "Glacier & Ink", "Crisp, oceanic teal",
            ChatThemeTokens("#10242D", "#1B3744", "#83D1E0", "#E9F7F9")),
        ChatThemeOption("parchment", "Parchment & Ink", "Soft, editorial light",
            ChatThemeTokens("#EAE5DA", "#FCFBF5", "#9A5E42", "#34312C")),
        ChatThemeOption("cobalt", "Cobalt & Ivory", "Technical midnight blue",
            ChatThemeTokens("#111F3A", "#20365A", "#75A7F7", "#F0F5FF")),
    )

    fun normalizeId(candidate: String): String = when (candidate) {
        // Old saves migrate forward without purple being forced on existing users.
        "burgundy" -> "carbon"
        "midnight" -> "coastal"
        "graphite" -> "signal"
        CUSTOM -> CUSTOM
        else -> presets.firstOrNull { it.id == candidate }?.id ?: DEFAULT
    }

    fun resolve(id: String, custom: ChatThemeTokens): ChatThemeTokens =
        if (normalizeId(id) == CUSTOM) custom.validated()
        else presets.first { it.id == normalizeId(id) }.tokens
}

/**
 * Android's Compose TextField receives plain Strings even from styled clipboard
 * spans. Still sanitize defensively: strip HTML tags, Unicode whitespace and
 * zero-width formatting, then require one unambiguous #RRGGBB value.
 *
 * Prefixes/metadata are never persisted. Returns null for ambiguous, malformed,
 * overlong or unsupported input, so the UI can retain the last valid draft.
 */
internal object HexThemeInput {
    private val partial = Regex("^#?[0-9a-fA-F]{0,6}$")
    private val full = Regex("^#[0-9A-F]{6}$")
    // Search ALL supported hex notations in metadata, including spaced digits.
    // Otherwise "color: #112233; background: 0xAA BB CC" might accidentally
    // apply the first colour rather than rejecting the ambiguous paste.
    private val hexFromMetadata = Regex(
        "(?<![0-9A-Za-z])(?:#|0[\\s\\p{Z}\\p{Cf}]*[xX])((?:[\\s\\p{Z}\\p{Cf}]*[0-9a-fA-F]){6})(?![0-9A-Za-z])"
    )
    private val tag = Regex("<[^>]{0,512}>")

    private fun compact(source: String): String = source.filterNot { ch ->
        Character.isWhitespace(ch) || Character.isSpaceChar(ch) ||
            Character.getType(ch) == Character.FORMAT.toInt()
    }

    fun input(raw: String): String? {
        if (raw.length > 4096 || raw.any { it == '\u0000' }) return null
        val plain = Normalizer.normalize(raw.replace(tag, ""), Normalizer.Form.NFKC)
        val cleaned = compact(plain)
        if (partial.matches(cleaned)) return "#" +
            cleaned.removePrefix("#").uppercase(Locale.ROOT)
        if (Regex("^0[xX][0-9a-fA-F]{6}$").matches(cleaned))
            return "#" + cleaned.drop(2).uppercase(Locale.ROOT)
        // Pasted CSS/HTML/plain-text wrappers may supply metadata. Accept
        // exactly one isolated explicitly marked colour; never guess between
        // multiple distinct values.
        val matches = hexFromMetadata.findAll(plain).map {
            compact(it.groupValues[1]).uppercase(Locale.ROOT)
        }.toList()
        return matches.singleOrNull()?.let { "#" + it }
    }

    fun complete(raw: String): String? = input(raw)?.takeIf(full::matches)

    fun isComplete(raw: String): Boolean = complete(raw) != null
}
