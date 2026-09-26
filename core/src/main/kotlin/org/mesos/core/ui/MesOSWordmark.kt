package org.mesos.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mesos.core.ui.theme.MesOSTheme
import org.mesos.core.ui.theme.Sora

/** The MesOS wordmark in the Aurora display face. */
@Composable
fun MesOSWordmark(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onBackground) {
    Text(
        text = "MesOS",
        modifier = modifier,
        style = MaterialTheme.typography.displayMedium.copy(
            fontFamily = Sora,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-1).sp,
        ),
        color = color,
    )
}

/** The MesOS mark: an "M" on an accent-to-teal squircle. */
@Composable
fun MesOSMark(modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val accent = MesOSTheme.colors.accentBright
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val outline = SquircleShape.createOutline(this.size, layoutDirection, this)
        drawOutline(
            outline = outline,
            brush = Brush.linearGradient(listOf(accent, Color(0xFF2DD4BF)), start = Offset.Zero, end = Offset(s, s)),
        )
        val m = Path().apply {
            moveTo(0.28f * s, 0.70f * s)
            lineTo(0.28f * s, 0.32f * s)
            lineTo(0.50f * s, 0.56f * s)
            lineTo(0.72f * s, 0.32f * s)
            lineTo(0.72f * s, 0.70f * s)
        }
        drawPath(m, Color.White, style = Stroke(width = 0.09f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
