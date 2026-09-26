package org.mesos.launcher.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import org.mesos.core.prefs.MesOSPreferences
import org.mesos.core.prefs.Wallpaper
import kotlin.math.sin

/**
 * The Aurora wallpaper as an Android live wallpaper, so it can also appear on the
 * lock screen. Renders on its own thread and only while visible.
 */
class AuroraWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = AuroraEngine()

    private inner class AuroraEngine : Engine() {
        private val thread = HandlerThread("MesOSWallpaper").apply { start() }
        private val handler = Handler(thread.looper)
        private val frames = AuroraFrames()
        private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val dst = Rect()
        @Volatile private var visible = false
        private val draw = Runnable { drawFrame() }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            handler.removeCallbacks(draw)
            if (visible) handler.post(draw)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            handler.removeCallbacks(draw)
            handler.post(draw)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(draw)
        }

        override fun onDestroy() {
            visible = false
            handler.removeCallbacks(draw)
            thread.quitSafely()
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            val locked: Canvas? = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) holder.lockHardwareCanvas() else holder.lockCanvas()
            } catch (e: IllegalStateException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
            val canvas = locked ?: return
            try {
                render(canvas)
            } finally {
                try {
                    holder.unlockCanvasAndPost(canvas)
                } catch (e: IllegalStateException) {
                    return
                }
            }
            handler.removeCallbacks(draw)
            if (visible) handler.postDelayed(draw, AuroraScene.FRAME_MS)
        }

        private fun render(canvas: Canvas) {
            val preferences = MesOSPreferences.get(applicationContext)
            val chosen = preferences.wallpaper.value
            val palette = AuroraScene.palette(chosen, preferences.accent.value)
            val animated = chosen == Wallpaper.AURORA || chosen == Wallpaper.SYSTEM
            val t = if (animated) (SystemClock.elapsedRealtime() % 3_600_000L) / 1000f else AuroraScene.STILL_TIME
            val bitmap = frames.render(palette, t)
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()
            dst.set(0, 0, canvas.width, canvas.height)
            canvas.drawBitmap(bitmap, null, dst, imagePaint)

            val sx = w / AuroraScene.ARTBOARD_WIDTH
            val sy = h / AuroraScene.ARTBOARD_HEIGHT
            if (palette.stars) {
                for (star in AuroraScene.stars) {
                    val twinkle = (0.45f + 0.35f * sin(t * 0.8f + star[3])).coerceIn(0f, 1f)
                    shapePaint.color = Color.argb((twinkle * 255).toInt(), 255, 255, 255)
                    canvas.drawCircle(star[0] * sx, star[1] * sy, star[2] * sx, shapePaint)
                }
            }
            drawRidge(canvas, AuroraScene.backMountains, palette.mountainBack, sx, sy, w, h)
            drawRidge(canvas, AuroraScene.frontMountains, palette.mountainFront, sx, sy, w, h)
        }

        private fun drawRidge(canvas: Canvas, points: FloatArray, rgb: Int, sx: Float, sy: Float, w: Float, h: Float) {
            val path = Path().apply {
                moveTo(points[0] * sx, points[1] * sy)
                var i = 2
                while (i < points.size) {
                    lineTo(points[i] * sx, points[i + 1] * sy)
                    i += 2
                }
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            shapePaint.color = Color.BLACK or rgb
            canvas.drawPath(path, shapePaint)
        }
    }

    companion object {
        /** Android's screen for making Aurora the system (and lock screen) wallpaper. */
        fun chooserIntent(context: Context): Intent =
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(context, AuroraWallpaperService::class.java),
                )

        /** Whether Android's current wallpaper is MesOS Aurora. */
        fun isActive(context: Context): Boolean =
            try {
                WallpaperManager.getInstance(context).wallpaperInfo?.component ==
                    ComponentName(context, AuroraWallpaperService::class.java)
            } catch (e: RuntimeException) {
                false
            }
    }
}
