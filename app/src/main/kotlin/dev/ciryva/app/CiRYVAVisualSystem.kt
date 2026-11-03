package dev.ciryva.app

import androidx.compose.foundation.Canvas
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

/** Only background, not user messages, changes with these legible presets. */
internal fun backdropColors(preset: String): List<Color> = when (preset) {
    ChatPreferences.BACKGROUND_MIDNIGHT -> listOf(
        Color(0xFF0E0D1A), Color(0xFF151225),
        Color(0xFF201529), Color(0xFF100E1A),
    )
    ChatPreferences.BACKGROUND_GRAPHITE -> listOf(
        Color(0xFF161B21), Color(0xFF11181D),
        Color(0xFF22232B), Color(0xFF0E1218),
    )
    else -> listOf(
        Color(0xFF230E1B), Color(0xFF170D17),
        Color(0xFF32111F), Color(0xFF140A14),
    )
}

/**
 * Real user-adjustable native Compose background. Decorative paths can be
 * switched off independently for a cleaner, less distracting reading view.
 * No network or bitmap asset is involved.
 */
@Composable
internal fun CiRYVABackdrop(
    preset: String = ChatPreferences.BACKGROUND_BURGUNDY,
    decorativeWaves: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(backdropColors(preset)),
        ),
    ) {
        if (decorativeWaves) Canvas(Modifier.fillMaxSize()) {
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
            drawPath(upper, CiRYVAVisual.rose.copy(alpha = 0.09f),
                style = Stroke(width = 40.dp.toPx()))
            drawPath(upper, CiRYVAVisual.gold.copy(alpha = 0.21f),
                style = Stroke(width = 1.dp.toPx()))
            drawPath(lower, CiRYVAVisual.rose.copy(alpha = 0.07f),
                style = Stroke(width = 32.dp.toPx()))
            drawPath(lower, CiRYVAVisual.rose.copy(alpha = 0.20f),
                style = Stroke(width = 1.dp.toPx()))
        }
        content()
    }
}

@Composable
internal fun CiRYVAStar(modifier: Modifier = Modifier) {
    Canvas(modifier.size(29.dp)) {
        val x = size.width / 2f
        val y = size.height / 2f
        val r = size.minDimension * 0.48f
        val fine = 1.3.dp.toPx()
        val strong = 2.dp.toPx()
        drawLine(CiRYVAVisual.gold, Offset(x, y - r), Offset(x, y + r),
            strokeWidth = strong, cap = StrokeCap.Round)
        drawLine(CiRYVAVisual.gold, Offset(x - r, y), Offset(x + r, y),
            strokeWidth = strong, cap = StrokeCap.Round)
        val diagonal = r * 0.70f
        drawLine(CiRYVAVisual.rose, Offset(x - diagonal, y - diagonal),
            Offset(x + diagonal, y + diagonal), strokeWidth = fine)
        drawLine(CiRYVAVisual.rose, Offset(x - diagonal, y + diagonal),
            Offset(x + diagonal, y - diagonal), strokeWidth = fine)
        drawCircle(CiRYVAVisual.ink, radius = r * 0.20f, center = Offset(x, y))
        drawCircle(CiRYVAVisual.gold, radius = r * 0.11f, center = Offset(x, y))
    }
}

@Composable
internal fun CiRYVAOrnamentDivider(modifier: Modifier = Modifier, label: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        HorizontalDivider(Modifier.weight(1f),
            color = CiRYVAVisual.gold.copy(alpha = 0.43f))
        Spacer(Modifier.width(8.dp))
        if (label != null) {
            Text(label, color = CiRYVAVisual.gold, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 7.dp))
        } else {
            Text("✦", color = CiRYVAVisual.gold, fontSize = 14.sp)
        }
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(Modifier.weight(1f),
            color = CiRYVAVisual.gold.copy(alpha = 0.43f))
    }
}

@Composable
internal fun CiRYVAFramedPanel(
    modifier: Modifier = Modifier,
    corner: androidx.compose.ui.unit.Dp = 22.dp,
    color: Color = CiRYVAVisual.raised.copy(alpha = 0.95f),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(corner),
        color = color,
        border = BorderStroke(1.dp, CiRYVAVisual.outline),
        shadowElevation = 2.dp,
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
    CiRYVAFramedPanel(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        corner = 16.dp,
        color = CiRYVAVisual.wine.copy(alpha = if (enabled) 0.78f else 0.4f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = CiRYVAVisual.muted, fontSize = 10.sp,
                    maxLines = 1)
                Text(detail, color = CiRYVAVisual.ivory, fontSize = 11.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            if (enabled) Text("⌄", color = CiRYVAVisual.gold, fontSize = 12.sp)
        }
    }
}
