package org.mesos.launcher.wallpaper

import android.os.SystemClock
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.mesos.core.prefs.Accent
import org.mesos.core.prefs.Wallpaper
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The MesOS Home background. Aurora is animated while Home is on screen and still
 * otherwise; [Wallpaper.SYSTEM] leaves Android's wallpaper visible with a light
 * shade so white text stays readable.
 */
@Composable
internal fun WallpaperLayer(wallpaper: Wallpaper, accent: Accent, modifier: Modifier = Modifier) {
    if (wallpaper == Wallpaper.SYSTEM) {
        Spacer(
            modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.28f),
                            0.3f to Color.Transparent,
                            0.7f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.38f),
                        ),
                    )
                },
        )
        return
    }

    val palette = remember(wallpaper, accent) { AuroraScene.palette(wallpaper, accent) }
    val frames = remember { AuroraFrames() }
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    var time by remember { mutableFloatStateOf(AuroraScene.STILL_TIME) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(palette, wallpaper) {
        if (wallpaper != Wallpaper.AURORA) {
            time = AuroraScene.STILL_TIME
            frame = withContext(Dispatchers.Default) { frames.render(palette, time).asImageBitmap() }
            return@LaunchedEffect
        }
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                val t = (SystemClock.elapsedRealtime() % 3_600_000L) / 1000f
                frame = withContext(Dispatchers.Default) { frames.render(palette, t).asImageBitmap() }
                time = t
                delay(AuroraScene.FRAME_MS)
            }
        }
    }

    Spacer(
        modifier
            .fillMaxSize()
            .drawBehind {
                val image = frame
                if (image == null) {
                    drawRect(Color(0xFF070C1A))
                } else {
                    drawImage(
                        image = image,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(image.width, image.height),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        filterQuality = FilterQuality.Low,
                    )
                }
                if (palette.stars) drawStars(time)
                drawMountains(palette)
            },
    )
}

private fun DrawScope.drawStars(time: Float) {
    val sx = size.width / AuroraScene.ARTBOARD_WIDTH
    val sy = size.height / AuroraScene.ARTBOARD_HEIGHT
    for (star in AuroraScene.stars) {
        val twinkle = 0.45f + 0.35f * sin(time * 0.8f + star[3])
        drawCircle(
            color = Color.White.copy(alpha = twinkle.coerceIn(0f, 1f)),
            radius = star[2] * sx,
            center = Offset(star[0] * sx, star[1] * sy),
        )
    }
}

private fun DrawScope.drawMountains(palette: AuroraPalette) {
    drawRidge(AuroraScene.backMountains, Color(0xFF000000.toInt() or palette.mountainBack))
    drawRidge(AuroraScene.frontMountains, Color(0xFF000000.toInt() or palette.mountainFront))
}

private fun DrawScope.drawRidge(points: FloatArray, color: Color) {
    val sx = size.width / AuroraScene.ARTBOARD_WIDTH
    val sy = size.height / AuroraScene.ARTBOARD_HEIGHT
    val path = Path().apply {
        moveTo(points[0] * sx, points[1] * sy)
        var i = 2
        while (i < points.size) {
            lineTo(points[i] * sx, points[i + 1] * sy)
            i += 2
        }
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(path, color)
}
