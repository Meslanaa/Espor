package org.mesos.weather

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin

private val SunColor = Color(0xFFFDE68A)
private val SunCore = Color(0xFFFBBF24)
private val MoonColor = Color(0xFFE2E8F0)
private val CloudLight = Color(0xFFFFFFFF)
private val CloudGrey = Color(0xFFCBD5E1)
private val CloudDark = Color(0xFF94A3B8)
private val RainColor = Color(0xFF7DD3FC)
private val BoltColor = Color(0xFFFACC15)

/** The Aurora weather symbol for a WMO weather code, drawn to any size. */
@Composable
fun WeatherIcon(code: Int, isDay: Boolean, modifier: Modifier = Modifier) {
    val condition = Condition.fromCode(code)
    Canvas(modifier) {
        val s = size.minDimension
        val o = Offset((size.width - s) / 2f, (size.height - s) / 2f)
        when (condition) {
            Condition.CLEAR -> if (isDay) sun(o + Offset(s * 0.5f, s * 0.5f), s * 0.24f) else moon(o + Offset(s * 0.5f, s * 0.5f), s * 0.28f)
            Condition.MAINLY_CLEAR, Condition.PARTLY_CLOUDY -> {
                val c = o + Offset(s * 0.38f, s * 0.36f)
                if (isDay) sun(c, s * 0.17f) else moon(c, s * 0.2f)
                cloud(o + Offset(s * 0.56f, s * 0.64f), s * (if (condition == Condition.MAINLY_CLEAR) 0.5f else 0.62f), CloudLight)
            }
            Condition.OVERCAST -> {
                cloud(o + Offset(s * 0.42f, s * 0.46f), s * 0.52f, CloudGrey)
                cloud(o + Offset(s * 0.56f, s * 0.6f), s * 0.64f, CloudLight)
            }
            Condition.FOG -> {
                cloud(o + Offset(s * 0.5f, s * 0.42f), s * 0.62f, CloudGrey)
                for (i in 0..2) {
                    val y = o.y + s * (0.66f + i * 0.1f)
                    drawLine(CloudLight, Offset(o.x + s * (0.2f + i * 0.05f), y), Offset(o.x + s * (0.8f - i * 0.05f), y), s * 0.045f, StrokeCap.Round)
                }
            }
            Condition.DRIZZLE, Condition.RAIN, Condition.SHOWERS, Condition.FREEZING_RAIN -> {
                cloud(o + Offset(s * 0.5f, s * 0.42f), s * 0.66f, if (condition == Condition.DRIZZLE) CloudLight else CloudGrey)
                val drops = if (condition == Condition.RAIN || condition == Condition.SHOWERS) 3 else 2
                for (i in 0 until drops) {
                    val x = o.x + s * (0.36f + i * 0.14f)
                    drawLine(RainColor, Offset(x, o.y + s * 0.68f), Offset(x - s * 0.05f, o.y + s * 0.84f), s * 0.05f, StrokeCap.Round)
                }
            }
            Condition.SNOW, Condition.SNOW_SHOWERS -> {
                cloud(o + Offset(s * 0.5f, s * 0.42f), s * 0.66f, CloudLight)
                for (i in 0..2) {
                    drawCircle(CloudLight, s * 0.04f, Offset(o.x + s * (0.34f + i * 0.16f), o.y + s * (0.72f + (i % 2) * 0.08f)))
                }
            }
            Condition.THUNDERSTORM -> {
                cloud(o + Offset(s * 0.5f, s * 0.4f), s * 0.68f, CloudDark)
                val bolt = Path().apply {
                    moveTo(o.x + s * 0.54f, o.y + s * 0.52f)
                    lineTo(o.x + s * 0.42f, o.y + s * 0.72f)
                    lineTo(o.x + s * 0.52f, o.y + s * 0.72f)
                    lineTo(o.x + s * 0.46f, o.y + s * 0.9f)
                    lineTo(o.x + s * 0.64f, o.y + s * 0.64f)
                    lineTo(o.x + s * 0.54f, o.y + s * 0.64f)
                    close()
                }
                drawPath(bolt, BoltColor)
            }
        }
    }
}

private fun DrawScope.sun(center: Offset, radius: Float) {
    for (i in 0 until 8) {
        val a = i * Math.PI / 4
        val start = center + Offset((cos(a) * radius * 1.35f).toFloat(), (sin(a) * radius * 1.35f).toFloat())
        val end = center + Offset((cos(a) * radius * 1.7f).toFloat(), (sin(a) * radius * 1.7f).toFloat())
        drawLine(SunColor, start, end, radius * 0.22f, StrokeCap.Round)
    }
    drawCircle(Brush.radialGradient(listOf(SunColor, SunCore), center, radius), radius, center)
}

private fun DrawScope.moon(center: Offset, radius: Float) {
    val path = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(center, radius))
    }
    val cut = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(center + Offset(radius * 0.55f, -radius * 0.35f), radius * 0.85f))
    }
    val crescent = Path().apply { op(path, cut, androidx.compose.ui.graphics.PathOperation.Difference) }
    drawPath(crescent, MoonColor)
}

/** A cloud [width] wide whose bottom-centre is near [center]. */
private fun DrawScope.cloud(center: Offset, width: Float, color: Color) {
    val h = width * 0.42f
    val left = center.x - width / 2f
    val bottom = center.y + h * 0.35f
    drawRoundRect(color, Offset(left, bottom - h * 0.55f), Size(width, h * 0.55f), CornerRadius(h * 0.28f))
    drawCircle(color, h * 0.42f, Offset(left + width * 0.32f, bottom - h * 0.5f))
    drawCircle(color, h * 0.56f, Offset(left + width * 0.58f, bottom - h * 0.62f))
    drawCircle(color, h * 0.3f, Offset(left + width * 0.8f, bottom - h * 0.35f))
}
