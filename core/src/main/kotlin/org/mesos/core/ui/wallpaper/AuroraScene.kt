package org.mesos.core.ui.wallpaper

import android.graphics.Bitmap
import org.mesos.core.prefs.Accent
import org.mesos.core.prefs.Wallpaper
import kotlin.random.Random

/**
 * Everything the Aurora wallpaper draws besides the sky: star positions and the
 * mountain silhouettes (from the Aurora design, on its 390 × 844 artboard).
 */
object AuroraScene {

    /** Low-resolution render size; the image is scaled up with filtering. */
    const val RENDER_WIDTH = 108
    const val RENDER_HEIGHT = 234

    /** Frame interval of the animated wallpaper (the aurora drifts slowly). */
    const val FRAME_MS = 50L

    /** Time used for the still variants. */
    const val STILL_TIME = 12f

    const val ARTBOARD_WIDTH = 390f
    const val ARTBOARD_HEIGHT = 844f

    val backMountains = floatArrayOf(
        0f, 642f, 52f, 598f, 96f, 626f, 160f, 548f, 222f, 610f, 280f, 570f, 338f, 616f, 390f, 584f,
    )
    val frontMountains = floatArrayOf(
        0f, 700f, 70f, 660f, 130f, 690f, 200f, 640f, 262f, 684f, 330f, 650f, 390f, 676f,
    )

    /** Stars as (x, y, radius, phase) on the artboard, fixed so they never jump. */
    val stars: List<FloatArray> = Random(7).let { random ->
        List(44) {
            floatArrayOf(
                random.nextFloat() * ARTBOARD_WIDTH,
                random.nextFloat() * ARTBOARD_HEIGHT * 0.42f,
                0.6f + random.nextFloat() * 0.9f,
                random.nextFloat() * 6.28f,
            )
        }
    }

    fun palette(wallpaper: Wallpaper, accent: Accent): AuroraPalette {
        val rgb = accent.base.toInt() and 0xFFFFFF
        return when (wallpaper) {
            Wallpaper.DAWN -> AuroraPalettes.dawn(rgb)
            Wallpaper.OCEAN -> AuroraPalettes.ocean(rgb)
            Wallpaper.GRAPHITE -> AuroraPalettes.graphite(rgb)
            Wallpaper.AURORA, Wallpaper.AURORA_STILL, Wallpaper.SYSTEM -> AuroraPalettes.aurora(rgb)
        }
    }
}

/** Renderer plus three bitmaps used in turn, so a frame on screen is never overwritten. */
class AuroraFrames {
    private val renderer = AuroraRenderer(AuroraScene.RENDER_WIDTH, AuroraScene.RENDER_HEIGHT)
    private val bitmaps = Array(3) {
        Bitmap.createBitmap(AuroraScene.RENDER_WIDTH, AuroraScene.RENDER_HEIGHT, Bitmap.Config.ARGB_8888)
    }
    private var next = 0

    @Synchronized
    fun render(palette: AuroraPalette, timeSeconds: Float): Bitmap {
        renderer.render(palette, timeSeconds)
        val bitmap = bitmaps[next]
        next = (next + 1) % bitmaps.size
        bitmap.setPixels(renderer.pixels, 0, renderer.width, 0, 0, renderer.width, renderer.height)
        return bitmap
    }
}
