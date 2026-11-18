package dev.ciryva.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Native, scalable, asset-free CiRYVA visual elements. The design reference is
 * a direction, not a bitmap overlay: all interactive components are real Compose.
 * Soft decoration is behind content and does not intercept taps or focus.
 */
internal object CiRYVAVisual {
    val ink = Color(0xFF160C15)
    val wine = Color(0xFF371523)
    val rose = Color(0xFFF4ADB2)
    val gold = Color(0xFFF4C7AD)
    val ivory = Color(0xFFFFEDE9)
    val muted = Color(0xFFD9B8BE)
    val raised = Color(0xFF30202C)
    val bubble = Color(0xFF5D273C)
    val softBorder = Color(0xFFAF687A)
    val outline = Brush.linearGradient(
        listOf(gold.copy(alpha = 0.86f), rose.copy(alpha = 0.6f),
            Color(0xFF77364E).copy(alpha = 0.92f))
    )
}

/** Backdrop exactly matches the selected sRGB background token. */
internal fun backdropColors(preset: String, custom: ChatThemeTokens =
    ChatThemePalettes.carbon.tokens): List<Color> =
    listOf(hexComposeColor(ChatThemePalettes.resolve(preset, custom).background))

/**
 * Real user-adjustable native Compose background. Decorative paths can be
 * switched off independently for a cleaner, less distracting reading view.
 * No network or bitmap asset is involved.
 */
@Composable
internal fun CiRYVABackdrop(
    preset: String = ChatThemePalettes.DEFAULT,
    decorativeWaves: Boolean = false,
    wallpaper: ImageBitmap? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalChatColors.current
    Box(Modifier.fillMaxSize().background(colors.background)) {
        if (wallpaper != null) {
            Image(
                bitmap = wallpaper,
                contentDescription = null, // purely decorative; setting identifies its presence
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Scrim guarantees text contrast even for bright or busy wallpapers.
            Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = .75f)))
        }
        if (decorativeWaves && wallpaper == null) Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val upper = Path().apply {
                moveTo(-0.1f * w, 0.16f * h)
                cubicTo(0.25f * w, -0.04f * h, 0.48f * w, 0.01f * h, 1.12f * w, 0.10f * h)
            }
            val lower = Path().apply {
                moveTo(-0.2f * w, 0.78f * h)
                cubicTo(0.20f * w, 0.63f * h, 0.63f * w, 0.92f * h, 1.2f * w, 0.74f * h)
            }
            drawPath(upper, colors.accent.copy(alpha = 0.09f),
                style = Stroke(width = 40.dp.toPx()))
            drawPath(upper, colors.accent.copy(alpha = 0.21f),
                style = Stroke(width = 1.dp.toPx()))
            drawPath(lower, colors.accent.copy(alpha = 0.07f),
                style = Stroke(width = 32.dp.toPx()))
            drawPath(lower, colors.accent.copy(alpha = 0.20f),
                style = Stroke(width = 1.dp.toPx()))
        }
        content()
    }
}

@Composable
internal fun CiRYVAStar(modifier: Modifier = Modifier) {
    val colors = LocalChatColors.current
    Canvas(modifier.size(29.dp)) {
        val x = size.width / 2f
        val y = size.height / 2f
        val r = size.minDimension * 0.48f
        val fine = 1.3.dp.toPx()
        val strong = 2.dp.toPx()
        drawLine(colors.accent, Offset(x, y - r), Offset(x, y + r),
            strokeWidth = strong, cap = StrokeCap.Round)
        drawLine(colors.accent, Offset(x - r, y), Offset(x + r, y),
            strokeWidth = strong, cap = StrokeCap.Round)
        val diagonal = r * 0.70f
        drawLine(colors.accent, Offset(x - diagonal, y - diagonal),
            Offset(x + diagonal, y + diagonal), strokeWidth = fine)
        drawLine(colors.accent, Offset(x - diagonal, y + diagonal),
            Offset(x + diagonal, y - diagonal), strokeWidth = fine)
        drawCircle(colors.background, radius = r * 0.20f, center = Offset(x, y))
        drawCircle(colors.accent, radius = r * 0.11f, center = Offset(x, y))
    }
}

@Composable
internal fun CiRYVAOrnamentDivider(modifier: Modifier = Modifier, label: String? = null) {
    val colors = LocalChatColors.current
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        HorizontalDivider(Modifier.weight(1f),
            color = colors.accent.copy(alpha = 0.43f))
        Spacer(Modifier.width(8.dp))
        if (label != null) {
            Text(label, color = colors.accent, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 7.dp))
        } else {
            Text("✦", color = colors.accent, fontSize = 14.sp)
        }
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(Modifier.weight(1f),
            color = colors.accent.copy(alpha = 0.43f))
    }
}

@Composable
internal fun CiRYVAFramedPanel(
    modifier: Modifier = Modifier,
    corner: androidx.compose.ui.unit.Dp = 22.dp,
    color: Color? = null,
    /** Open assistant messages and the keyboard rail use Elara-like unboxed chrome. */
    frameless: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = LocalChatColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(corner),
        color = if (frameless) Color.Transparent else color ?: colors.panel,
        border = if (frameless) null else BorderStroke(1.dp, colors.border),
        shadowElevation = if (frameless) 0.dp else 2.dp,
    ) { content() }
}

@Composable
internal fun CiRYVAControlTile(
    title: String,
    detail: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalChatColors.current
    CiRYVAFramedPanel(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        corner = 16.dp,
        color = colors.panel.copy(alpha = if (enabled) 1f else .55f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = colors.muted, fontSize = 10.sp,
                    maxLines = 1)
                Text(detail, color = colors.text, fontSize = 11.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            if (enabled) Text("⌄", color = colors.accent, fontSize = 12.sp)
        }
    }
}
