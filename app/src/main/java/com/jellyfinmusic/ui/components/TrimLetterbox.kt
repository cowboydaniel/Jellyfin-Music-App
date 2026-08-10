package com.jellyfinmusic.ui.components

import android.graphics.Bitmap
import androidx.core.graphics.get
import coil.size.Size
import coil.transform.Transformation
import kotlin.math.abs

/**
 * Crops uniform borders off artwork.
 *
 * Music ripped from video sources often carries a 16:9 frame padded into a
 * square, with the black bars baked into the image file itself. The server
 * cannot help here and ContentScale.Crop has nothing to remove, since the
 * padded image really is square — so the bars are detected and cut client-side.
 *
 * Deliberately conservative: it only trims borders that are near-uniform and
 * gives up if the crop would remove most of the picture, so ordinary artwork
 * with a dark edge is left alone.
 */
class TrimLetterboxTransformation : Transformation {

    override val cacheKey: String = "${javaClass.name}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = input.width
        val height = input.height
        if (width < 16 || height < 16) return input

        val top = scan(input, height) { y -> isUniformRow(input, y, width) }
        val bottom = scan(input, height) { offset -> isUniformRow(input, height - 1 - offset, width) }
        val left = scan(input, width) { x -> isUniformColumn(input, x, height) }
        val right = scan(input, width) { offset -> isUniformColumn(input, width - 1 - offset, height) }

        if (top == 0 && bottom == 0 && left == 0 && right == 0) return input

        val newWidth = width - left - right
        val newHeight = height - top - bottom
        // Refuse implausible crops — a nearly blank image would otherwise
        // collapse to a few pixels.
        if (newWidth < width / 4 || newHeight < height / 4) return input

        return Bitmap.createBitmap(input, left, top, newWidth, newHeight)
    }

    /** Counts how many leading rows or columns satisfy [uniform], capped at half. */
    private inline fun scan(bitmap: Bitmap, extent: Int, uniform: (Int) -> Boolean): Int {
        val limit = extent / 2
        var count = 0
        while (count < limit && uniform(count)) count++
        return count
    }

    private fun isUniformRow(bitmap: Bitmap, y: Int, width: Int): Boolean {
        val step = (width / SAMPLES).coerceAtLeast(1)
        val reference = bitmap[0, y]
        var x = 0
        while (x < width) {
            if (!isBarPixel(bitmap[x, y], reference)) return false
            x += step
        }
        return true
    }

    private fun isUniformColumn(bitmap: Bitmap, x: Int, height: Int): Boolean {
        val step = (height / SAMPLES).coerceAtLeast(1)
        val reference = bitmap[x, 0]
        var y = 0
        while (y < height) {
            if (!isBarPixel(bitmap[x, y], reference)) return false
            y += step
        }
        return true
    }

    /**
     * A bar pixel is close to its row's first pixel and dark overall; bright
     * uniform edges are usually part of the artwork rather than padding.
     */
    private fun isBarPixel(pixel: Int, reference: Int): Boolean {
        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF
        if (red > BAR_MAX_CHANNEL || green > BAR_MAX_CHANNEL || blue > BAR_MAX_CHANNEL) return false

        val refRed = (reference shr 16) and 0xFF
        val refGreen = (reference shr 8) and 0xFF
        val refBlue = reference and 0xFF
        return abs(red - refRed) <= TOLERANCE &&
            abs(green - refGreen) <= TOLERANCE &&
            abs(blue - refBlue) <= TOLERANCE
    }

    override fun equals(other: Any?): Boolean = other is TrimLetterboxTransformation

    override fun hashCode(): Int = javaClass.hashCode()

    private companion object {
        const val SAMPLES = 24
        const val BAR_MAX_CHANNEL = 26
        const val TOLERANCE = 12
    }
}
