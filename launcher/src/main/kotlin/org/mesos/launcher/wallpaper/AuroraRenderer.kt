package org.mesos.launcher.wallpaper

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** One glowing band of light. Positions are fractions of the image height. */
data class Ribbon(
    val color: Int,
    val base: Float,
    val amplitude: Float,
    val thickness: Float,
    val intensity: Float,
    val speed: Float,
    val phase: Float,
)

/** Sky gradient, ribbons and mountain colours of one wallpaper. */
data class AuroraPalette(
    val skyTop: Int,
    val skyMiddle: Int,
    val skyBottom: Int,
    val ribbons: List<Ribbon>,
    val mountainBack: Int,
    val mountainFront: Int,
    val stars: Boolean = true,
)

/**
 * Renders the Aurora wallpaper into a small ARGB pixel buffer that is then scaled
 * up with filtering, which gives the soft glow for free. Pure Kotlin: the same
 * code feeds MesOS Home and the Android live wallpaper.
 *
 * Each ribbon is a curtain of light: a bright lower edge that fades slowly upwards,
 * whose height, brightness and position drift with time.
 */
class AuroraRenderer(val width: Int, val height: Int) {

    val pixels = IntArray(width * height)

    private val r = FloatArray(width * height)
    private val g = FloatArray(width * height)
    private val b = FloatArray(width * height)
    private val skyR = FloatArray(height)
    private val skyG = FloatArray(height)
    private val skyB = FloatArray(height)
    private var skyFor: AuroraPalette? = null

    fun render(palette: AuroraPalette, timeSeconds: Float) {
        if (skyFor != palette) prepareSky(palette)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                r[row + x] = skyR[y]
                g[row + x] = skyG[y]
                b[row + x] = skyB[y]
            }
        }
        for (ribbon in palette.ribbons) addRibbon(ribbon, timeSeconds, r, g, b)
        for (i in pixels.indices) {
            pixels[i] = (0xFF shl 24) or
                (clamp(r[i]) shl 16) or
                (clamp(g[i]) shl 8) or
                clamp(b[i])
        }
    }

    private fun addRibbon(ribbon: Ribbon, t: Float, r: FloatArray, g: FloatArray, b: FloatArray) {
        val cr = (ribbon.color shr 16 and 0xFF).toFloat()
        val cg = (ribbon.color shr 8 and 0xFF).toFloat()
        val cb = (ribbon.color and 0xFF).toFloat()
        val s = ribbon.speed * t
        for (x in 0 until width) {
            val u = x.toFloat() / width
            val center = height * (
                ribbon.base +
                    ribbon.amplitude * wave(u * 1.3f + s * 0.05f + ribbon.phase) +
                    ribbon.amplitude * 0.5f * wave(u * 2.7f - s * 0.08f + ribbon.phase * 1.7f)
                )
            val thickness = height * ribbon.thickness * (0.75f + 0.25f * wave(u * 1.9f + s * 0.07f + ribbon.phase * 0.6f))
            val shimmer = 0.82f + 0.18f * wave(u * 9f + s * 0.11f + ribbon.phase)
            val strength = ribbon.intensity * shimmer * (0.55f + 0.45f * wave(u * 0.9f - s * 0.04f + ribbon.phase * 2.3f))
            if (thickness <= 0f) continue
            val top = (center - thickness * 3.2f).toInt().coerceAtLeast(0)
            val bottom = (center + thickness * 1.4f).toInt().coerceAtMost(height - 1)
            for (y in top..bottom) {
                val d = (y - center) / thickness
                // Long fade upwards, short bright edge below: the shape of an aurora curtain.
                val shape = if (d < 0f) gaussian(d / 2.2f) else gaussian(d * 1.6f)
                val a = strength * shape
                val i = y * width + x
                r[i] += cr * a
                g[i] += cg * a
                b[i] += cb * a
            }
        }
    }

    private fun prepareSky(palette: AuroraPalette) {
        for (y in 0 until height) {
            val f = y.toFloat() / (height - 1).coerceAtLeast(1)
            val (from, to, local) = if (f < 0.55f) {
                Triple(palette.skyTop, palette.skyMiddle, f / 0.55f)
            } else {
                Triple(palette.skyMiddle, palette.skyBottom, (f - 0.55f) / 0.45f)
            }
            skyR[y] = mix(from shr 16 and 0xFF, to shr 16 and 0xFF, local)
            skyG[y] = mix(from shr 8 and 0xFF, to shr 8 and 0xFF, local)
            skyB[y] = mix(from and 0xFF, to and 0xFF, local)
        }
        skyFor = palette
    }

    private companion object {
        const val TWO_PI = (2 * PI).toFloat()
        const val LUT_RANGE = 4f
        const val LUT_SIZE = 1024
        val GAUSSIAN = FloatArray(LUT_SIZE + 1) { i ->
            val d = i / LUT_SIZE.toFloat() * LUT_RANGE
            exp(-(d * d).toDouble()).toFloat()
        }

        fun gaussian(d: Float): Float {
            val a = if (d < 0f) -d else d
            if (a >= LUT_RANGE) return 0f
            return GAUSSIAN[(a / LUT_RANGE * LUT_SIZE).toInt()]
        }

        fun wave(x: Float): Float = sin(x * TWO_PI)

        fun mix(a: Int, b: Int, f: Float): Float = a + (b - a) * f

        fun clamp(v: Float): Int = v.roundToInt().coerceIn(0, 255)
    }
}

/** The MesOS wallpapers, built around the user's accent colour. */
object AuroraPalettes {

    fun aurora(accent: Int) = AuroraPalette(
        skyTop = 0x050914,
        skyMiddle = 0x0A1834,
        skyBottom = 0x0C2A3C,
        ribbons = listOf(
            Ribbon(accent, base = 0.30f, amplitude = 0.05f, thickness = 0.085f, intensity = 0.85f, speed = 1f, phase = 0.1f),
            Ribbon(0x2DD4BF, base = 0.42f, amplitude = 0.045f, thickness = 0.07f, intensity = 0.62f, speed = 1.3f, phase = 0.55f),
            Ribbon(0x34D399, base = 0.22f, amplitude = 0.035f, thickness = 0.05f, intensity = 0.35f, speed = 0.8f, phase = 0.8f),
        ),
        mountainBack = 0x081326,
        mountainFront = 0x040A16,
    )

    fun dawn(accent: Int) = AuroraPalette(
        skyTop = 0x140B2E,
        skyMiddle = 0x4A1D52,
        skyBottom = 0xB4536A,
        ribbons = listOf(
            Ribbon(0xFB923C, base = 0.40f, amplitude = 0.04f, thickness = 0.09f, intensity = 0.55f, speed = 0.7f, phase = 0.2f),
            Ribbon(accent, base = 0.28f, amplitude = 0.05f, thickness = 0.07f, intensity = 0.45f, speed = 0.9f, phase = 0.6f),
        ),
        mountainBack = 0x2A1540,
        mountainFront = 0x150A24,
    )

    fun ocean(accent: Int) = AuroraPalette(
        skyTop = 0x031525,
        skyMiddle = 0x06324A,
        skyBottom = 0x0B5563,
        ribbons = listOf(
            Ribbon(0x22D3EE, base = 0.34f, amplitude = 0.05f, thickness = 0.08f, intensity = 0.55f, speed = 0.9f, phase = 0.3f),
            Ribbon(accent, base = 0.46f, amplitude = 0.04f, thickness = 0.06f, intensity = 0.4f, speed = 1.1f, phase = 0.7f),
        ),
        mountainBack = 0x04202E,
        mountainFront = 0x021219,
        stars = false,
    )

    fun graphite(accent: Int) = AuroraPalette(
        skyTop = 0x0B0D12,
        skyMiddle = 0x151922,
        skyBottom = 0x1E2330,
        ribbons = listOf(
            Ribbon(accent, base = 0.34f, amplitude = 0.04f, thickness = 0.07f, intensity = 0.28f, speed = 0.8f, phase = 0.4f),
        ),
        mountainBack = 0x12151C,
        mountainFront = 0x0A0C10,
        stars = false,
    )
}
