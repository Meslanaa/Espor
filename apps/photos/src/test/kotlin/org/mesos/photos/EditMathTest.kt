package org.mesos.photos

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditMathTest {

    private fun close(expected: Float, actual: Float) = assertEquals(expected, actual, 0.6f)

    @Test
    fun identityAndConcat() {
        val id = ColorMatrices.identity()
        val warm = ColorMatrices.warmth(0.5f)
        assertArrayEquals(warm, ColorMatrices.concat(id, warm), 1e-5f)
        assertArrayEquals(warm, ColorMatrices.concat(warm, id), 1e-5f)
        // Brightness after contrast: offsets add up.
        val both = ColorMatrices.concat(ColorMatrices.brightness(0.5f), ColorMatrices.contrast(0f))
        val (r, _, _) = ColorMatrices.apply(both, 100f, 100f, 100f)
        close(140f, r)
    }

    @Test
    fun monoIsGrey() {
        val (r, g, b) = ColorMatrices.apply(ColorMatrices.filter(PhotoFilter.MONO), 200f, 50f, 10f)
        close(r, g)
        close(g, b)
        close(0.2126f * 200 + 0.7152f * 50 + 0.0722f * 10, r)
    }

    @Test
    fun contrastKeepsMidGrey() {
        val (r, _, _) = ColorMatrices.apply(ColorMatrices.contrast(0.5f), 128f, 128f, 128f)
        close(128f, r)
        val (bright, _, _) = ColorMatrices.apply(ColorMatrices.contrast(0.5f), 200f, 200f, 200f)
        close(236f, bright)
    }

    @Test
    fun neutralEditIsIdentity() {
        assertArrayEquals(ColorMatrices.identity(), ColorMatrices.edit(PhotoFilter.ORIGINAL, Adjustments()), 1e-6f)
        assertTrue(Adjustments().isNeutral)
    }

    @Test
    fun cropFitsAspect() {
        // A 4000×3000 photo, square crop: normalised width = height × 0.75.
        val square = CropMath.fit(CropRect(), 1f, 4f / 3f)
        assertEquals(0.75f, square.width, 1e-4f)
        assertEquals(1f, square.height, 1e-4f)
        assertEquals(0.125f, square.left, 1e-4f)
        // 16:9 on the same photo is limited by the width.
        val wide = CropMath.fit(CropRect(), 16f / 9f, 4f / 3f)
        assertEquals(1f, wide.width, 1e-4f)
        assertEquals(0.75f, wide.height, 1e-4f)
    }

    @Test
    fun cropStaysInside() {
        val moved = CropMath.move(CropRect(0.2f, 0.2f, 0.6f, 0.6f), 0.9f, -0.5f)
        assertEquals(1f, moved.right, 1e-5f)
        assertEquals(0f, moved.top, 1e-5f)
        val dragged = CropMath.dragCorner(CropRect(), 0, 0.95f, 0.95f, null, 1f)
        assertTrue(dragged.width >= CropMath.MIN_SIZE - 1e-5f)
        assertTrue(dragged.height >= CropMath.MIN_SIZE - 1e-5f)
        val square = CropMath.dragCorner(CropRect(), 2, -0.5f, -0.1f, 1f, 1f)
        assertEquals(square.width, square.height, 1e-4f)
    }

    @Test
    fun cropRotatesAndFlips() {
        val rect = CropRect(0.1f, 0.2f, 0.5f, 0.4f)
        assertEquals(CropRect(0.6f, 0.1f, 0.8f, 0.5f), CropMath.rotateClockwise(rect).let { CropRect(round(it.left), round(it.top), round(it.right), round(it.bottom)) })
        assertEquals(CropRect(0.5f, 0.2f, 0.9f, 0.4f), CropMath.flipHorizontal(rect).let { CropRect(round(it.left), round(it.top), round(it.right), round(it.bottom)) })
    }

    private fun round(v: Float) = Math.round(v * 1000f) / 1000f
}
