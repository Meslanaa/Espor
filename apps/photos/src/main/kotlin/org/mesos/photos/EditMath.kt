package org.mesos.photos

import kotlin.math.max
import kotlin.math.min

/** Looks the editor offers; applied before the manual adjustments. */
enum class PhotoFilter { ORIGINAL, VIVID, WARM, COOL, FADE, MONO, NOIR, SEPIA }

/** Manual adjustments, each 0 when untouched. Ranges: -1..1. */
data class Adjustments(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
) {
    val isNeutral: Boolean get() = brightness == 0f && contrast == 0f && saturation == 0f && warmth == 0f
}

/**
 * Colour matrices in Android's 4×5 layout (row-major, offsets in 0..255), and the
 * helpers the photo editor combines. Pure Kotlin, unit tested.
 */
object ColorMatrices {

    fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    fun brightness(amount: Float): FloatArray = identity().also { m ->
        val offset = amount * 80f
        m[4] = offset
        m[9] = offset
        m[14] = offset
    }

    fun contrast(amount: Float): FloatArray {
        val scale = 1f + amount
        val offset = 128f * (1f - scale)
        return floatArrayOf(
            scale, 0f, 0f, 0f, offset,
            0f, scale, 0f, 0f, offset,
            0f, 0f, scale, 0f, offset,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** [value] 1 keeps colours, 0 is greyscale, above 1 is more colourful. */
    fun saturation(value: Float): FloatArray {
        val r = 0.2126f * (1 - value)
        val g = 0.7152f * (1 - value)
        val b = 0.0722f * (1 - value)
        return floatArrayOf(
            r + value, g, b, 0f, 0f,
            r, g + value, b, 0f, 0f,
            r, g, b + value, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Positive is warmer (more red, less blue). */
    fun warmth(amount: Float): FloatArray = identity().also { m ->
        m[0] = 1f + 0.14f * amount
        m[12] = 1f - 0.14f * amount
    }

    private fun sepiaTone(): FloatArray = floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** [outer] applied after [inner]. */
    fun concat(outer: FloatArray, inner: FloatArray): FloatArray {
        val result = FloatArray(20)
        for (row in 0 until 4) {
            for (col in 0 until 5) {
                var sum = if (col == 4) outer[row * 5 + 4] else 0f
                for (k in 0 until 4) sum += outer[row * 5 + k] * inner[k * 5 + col]
                result[row * 5 + col] = sum
            }
        }
        return result
    }

    fun filter(filter: PhotoFilter): FloatArray = when (filter) {
        PhotoFilter.ORIGINAL -> identity()
        PhotoFilter.VIVID -> concat(contrast(0.12f), saturation(1.35f))
        PhotoFilter.WARM -> concat(warmth(0.7f), saturation(1.1f))
        PhotoFilter.COOL -> concat(warmth(-0.7f), saturation(1.05f))
        PhotoFilter.FADE -> concat(brightness(0.08f), concat(contrast(-0.22f), saturation(0.75f)))
        PhotoFilter.MONO -> saturation(0f)
        PhotoFilter.NOIR -> concat(contrast(0.35f), saturation(0f))
        PhotoFilter.SEPIA -> sepiaTone()
    }

    /** The full edit: filter, then saturation, warmth, contrast and brightness. */
    fun edit(filter: PhotoFilter, adjustments: Adjustments): FloatArray {
        var m = filter(filter)
        if (adjustments.saturation != 0f) m = concat(saturation(1f + adjustments.saturation), m)
        if (adjustments.warmth != 0f) m = concat(warmth(adjustments.warmth), m)
        if (adjustments.contrast != 0f) m = concat(contrast(adjustments.contrast * 0.6f), m)
        if (adjustments.brightness != 0f) m = concat(brightness(adjustments.brightness), m)
        return m
    }

    /** Applies [m] to one colour (0..255 channels), clamped, for tests and previews. */
    fun apply(m: FloatArray, r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        fun channel(row: Int) = (m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b + m[row * 5 + 4]).coerceIn(0f, 255f)
        return Triple(channel(0), channel(1), channel(2))
    }
}

/** A crop in 0..1 coordinates of the (rotated) photo. */
data class CropRect(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val isFull: Boolean get() = left <= 0f && top <= 0f && right >= 1f && bottom >= 1f
}

/** Crop aspect choices; [ratio] is width / height, null for free. */
enum class CropAspect(val ratio: Float?) { FREE(null), SQUARE(1f), FOUR_THREE(4f / 3f), THREE_FOUR(3f / 4f), SIXTEEN_NINE(16f / 9f), NINE_SIXTEEN(9f / 16f) }

object CropMath {
    /** Smallest crop side, as a fraction of the photo. */
    const val MIN_SIZE = 0.08f

    /**
     * The largest crop of [ratio] (in pixels, width / height) centred in [current],
     * for a photo of [imageAspect] (width / height).
     */
    fun fit(current: CropRect, ratio: Float?, imageAspect: Float): CropRect {
        if (ratio == null) return current
        // In normalised units a crop of pixel ratio r has width / height = r / imageAspect.
        val target = ratio / imageAspect
        var width = current.width
        var height = width / target
        if (height > current.height) {
            height = current.height
            width = height * target
        }
        if (width > 1f) {
            width = 1f
            height = width / target
        }
        if (height > 1f) {
            height = 1f
            width = height * target
        }
        val cx = (current.left + current.right) / 2
        val cy = (current.top + current.bottom) / 2
        return clampInside(CropRect(cx - width / 2, cy - height / 2, cx + width / 2, cy + height / 2))
    }

    /** Moves (not resizes) [rect] so it lies inside 0..1. */
    fun clampInside(rect: CropRect): CropRect {
        val dx = when {
            rect.left < 0f -> -rect.left
            rect.right > 1f -> 1f - rect.right
            else -> 0f
        }
        val dy = when {
            rect.top < 0f -> -rect.top
            rect.bottom > 1f -> 1f - rect.bottom
            else -> 0f
        }
        return CropRect(rect.left + dx, rect.top + dy, rect.right + dx, rect.bottom + dy)
    }

    /** Moves the whole crop by (dx, dy), staying inside the photo. */
    fun move(rect: CropRect, dx: Float, dy: Float): CropRect =
        clampInside(CropRect(rect.left + dx, rect.top + dy, rect.right + dx, rect.bottom + dy))

    /**
     * Drags one corner ([corner]: 0 top-left, 1 top-right, 2 bottom-right, 3 bottom-left)
     * by (dx, dy); keeps [ratio] (normalised by [imageAspect]) when given.
     */
    fun dragCorner(rect: CropRect, corner: Int, dx: Float, dy: Float, ratio: Float?, imageAspect: Float): CropRect {
        var left = rect.left
        var top = rect.top
        var right = rect.right
        var bottom = rect.bottom
        when (corner) {
            0 -> { left += dx; top += dy }
            1 -> { right += dx; top += dy }
            2 -> { right += dx; bottom += dy }
            else -> { left += dx; bottom += dy }
        }
        left = left.coerceIn(0f, rect.right - MIN_SIZE)
        right = right.coerceIn(rect.left + MIN_SIZE, 1f)
        top = top.coerceIn(0f, rect.bottom - MIN_SIZE)
        bottom = bottom.coerceIn(rect.top + MIN_SIZE, 1f)
        if (ratio != null) {
            val target = ratio / imageAspect
            val width = right - left
            var height = width / target
            // Keep the opposite corner fixed and fit the height to the ratio.
            val anchoredTop = corner == 2 || corner == 3
            if (anchoredTop) {
                height = min(height, 1f - top)
                bottom = top + height
            } else {
                height = min(height, bottom)
                top = bottom - height
            }
            val fixedWidth = height * target
            if (corner == 0 || corner == 3) left = right - fixedWidth else right = left + fixedWidth
        }
        return CropRect(max(0f, left), max(0f, top), min(1f, right), min(1f, bottom))
    }

    /** Rotating the photo by 90° clockwise turns the crop with it. */
    fun rotateClockwise(rect: CropRect): CropRect =
        CropRect(left = 1f - rect.bottom, top = rect.left, right = 1f - rect.top, bottom = rect.right)

    fun flipHorizontal(rect: CropRect): CropRect = CropRect(1f - rect.right, rect.top, 1f - rect.left, rect.bottom)
}
