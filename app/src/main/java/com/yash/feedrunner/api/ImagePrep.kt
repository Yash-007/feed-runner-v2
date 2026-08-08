package com.yash.feedrunner.api

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Turns a screenshot (or a tall stitched capture) into JPEG segments the API
 * can read.
 *
 * Claude downscales any image whose long edge exceeds [MAX_EDGE_PX]. A phone
 * screenshot is roughly 1:2.2, so downscaling the whole thing shrinks the text
 * to ~65% and costs legibility. Slicing it into shorter, wider segments keeps
 * every slice under the limit at full resolution instead, and Claude reads the
 * ordered slices as one continuous post.
 */
object ImagePrep {

    fun toBase64Segments(bitmap: Bitmap): List<String> =
        slice(bitmap).map { segment ->
            val scaled = downscaleIfNeeded(segment)
            val bytes = ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            }
            if (scaled !== segment) scaled.recycle()
            if (segment !== bitmap) segment.recycle()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }

    private fun slice(bitmap: Bitmap): List<Bitmap> {
        val ratio = bitmap.height.toFloat() / bitmap.width
        if (ratio <= MAX_ASPECT) return listOf(bitmap)

        val count = min(ceil(ratio / TARGET_ASPECT).roundToInt(), MAX_SEGMENTS)
        val sliceHeight = bitmap.height / count
        val overlap = (sliceHeight * OVERLAP_FRACTION).roundToInt()

        return (0 until count).map { i ->
            val top = (i * sliceHeight - if (i > 0) overlap else 0).coerceAtLeast(0)
            val bottom = ((i + 1) * sliceHeight).coerceAtMost(bitmap.height)
            Bitmap.createBitmap(bitmap, 0, top, bitmap.width, bottom - top)
        }
    }

    private fun downscaleIfNeeded(bitmap: Bitmap): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= MAX_EDGE_PX) return bitmap
        val scale = MAX_EDGE_PX.toFloat() / longEdge
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    /** Long-edge limit before Claude downscales the image itself. */
    private const val MAX_EDGE_PX = 1568

    /** Split anything taller than this; aim each slice at [TARGET_ASPECT]. */
    private const val MAX_ASPECT = 1.6f
    private const val TARGET_ASPECT = 1.35f

    /** Slices repeat a little content so nothing is lost at a boundary. */
    private const val OVERLAP_FRACTION = 0.06f

    /** Bounds the token cost of a very long stitched capture. */
    private const val MAX_SEGMENTS = 5

    private const val JPEG_QUALITY = 85
}
