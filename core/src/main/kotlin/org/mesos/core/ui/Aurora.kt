package org.mesos.core.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.mesos.core.prefs.IconShape

/**
 * The MesOS squircle: a continuous-curvature rounded square (the Aurora icon
 * silhouette). Same curve as [iconShapePath] with [IconShape.SQUIRCLE].
 */
object SquircleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0.5f * w, 0f)
            cubicTo(0.88f * w, 0f, w, 0.12f * h, w, 0.5f * h)
            cubicTo(w, 0.88f * h, 0.88f * w, h, 0.5f * w, h)
            cubicTo(0.12f * w, h, 0f, 0.88f * h, 0f, 0.5f * h)
            cubicTo(0f, 0.12f * h, 0.12f * w, 0f, 0.5f * w, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Outline of an app icon of [size] pixels in [shape], for drawing icons into
 * bitmaps (android.graphics).
 */
fun iconShapePath(shape: IconShape, size: Float): android.graphics.Path =
    android.graphics.Path().apply {
        when (shape) {
            IconShape.SQUIRCLE -> {
                moveTo(0.5f * size, 0f)
                cubicTo(0.88f * size, 0f, size, 0.12f * size, size, 0.5f * size)
                cubicTo(size, 0.88f * size, 0.88f * size, size, 0.5f * size, size)
                cubicTo(0.12f * size, size, 0f, 0.88f * size, 0f, 0.5f * size)
                cubicTo(0f, 0.12f * size, 0.12f * size, 0f, 0.5f * size, 0f)
                close()
            }
            IconShape.CIRCLE -> addCircle(size / 2f, size / 2f, size / 2f, android.graphics.Path.Direction.CW)
            IconShape.ROUNDED -> {
                val r = size * 0.24f
                addRoundRect(0f, 0f, size, size, r, r, android.graphics.Path.Direction.CW)
            }
        }
    }

/**
 * Aurora glass: a translucent white surface with a thin light edge, meant to sit
 * on wallpapers and dark backgrounds.
 */
fun Modifier.glass(
    shape: Shape,
    fill: Float = 0.12f,
    edge: Float = 0.18f,
    tint: Color = Color.White,
): Modifier = this
    .clip(shape)
    .background(tint.copy(alpha = fill))
    .border(1.dp, Color.White.copy(alpha = edge), shape)

/** MesOS motion: springs used across the system so everything moves alike. */
object MesOSMotion {
    /** Sheets, drawers and panels following a finger, then settling. */
    fun <T> sheet() = spring<T>(dampingRatio = 0.86f, stiffness = 420f)

    /** Small UI elements: toggles, chips, badges. */
    fun <T> snappy() = spring<T>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)

    /** Items moving to a new grid cell. */
    val gridMove = spring<IntOffset>(dampingRatio = 0.8f, stiffness = 380f)
}
