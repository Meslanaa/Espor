package org.mesos.launcher

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import org.mesos.core.prefs.IconShape
import org.mesos.core.ui.iconShapePath

/**
 * Draws any app's icon in the MesOS icon shape, so Play Store and user apps look
 * like MesOS apps on the home screen.
 *
 * Adaptive icons are rebuilt from their layers; legacy icons sit on a light tile.
 * With [themed], apps that ship a monochrome layer (Android 13+) are drawn in the
 * accent colour on a dark tile.
 */
internal class IconRenderer(
    private val shape: IconShape,
    private val themed: Boolean,
    private val accent: Int,
) {

    fun render(drawable: Drawable, sizePx: Int): Bitmap {
        val content = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(content)
        val monochrome = if (themed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            (drawable as? AdaptiveIconDrawable)?.monochrome
        } else {
            null
        }

        when {
            monochrome != null -> {
                canvas.drawColor(THEMED_BACKGROUND)
                monochrome.mutate()
                monochrome.colorFilter = PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN)
                drawLayer(canvas, monochrome, sizePx)
            }
            drawable is AdaptiveIconDrawable -> {
                drawable.background?.let { drawLayer(canvas, it, sizePx) }
                drawable.foreground?.let { drawLayer(canvas, it, sizePx) }
            }
            else -> {
                // Legacy icon: a light tile with the icon inset, like Android's own treatment.
                canvas.drawColor(LEGACY_BACKGROUND)
                val inset = (sizePx * LEGACY_INSET).toInt()
                drawable.setBounds(inset, inset, sizePx - inset, sizePx - inset)
                drawable.draw(canvas)
            }
        }

        // Clip with an anti-aliased shape by painting the content through a shader.
        val result = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(content, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        Canvas(result).drawPath(iconShapePath(shape, sizePx.toFloat()), paint)
        content.recycle()
        return result
    }

    /** Adaptive-icon layers are 108 dp with the visible 72 dp in the middle. */
    private fun drawLayer(canvas: Canvas, layer: Drawable, sizePx: Int) {
        val extra = sizePx / 4
        layer.setBounds(-extra, -extra, sizePx + extra, sizePx + extra)
        layer.draw(canvas)
    }

    private companion object {
        val THEMED_BACKGROUND = Color.rgb(0x1E, 0x28, 0x42)
        val LEGACY_BACKGROUND = Color.rgb(0xF1, 0xF4, 0xF9)
        const val LEGACY_INSET = 0.14f
    }
}
