package com.yash.feedrunner.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import kotlin.math.abs

/**
 * Incrementally stitches overlapping scroll frames into one tall bitmap.
 *
 * Each frame is cropped (status bar / sticky top chrome, bottom nav / reply
 * bar), then aligned by locating the previous frame's bottom reference strip
 * inside the new frame via a grayscale template match at reduced scale.
 * Only the rows below the match point are appended, so overlap between
 * frames is deduplicated regardless of the exact scroll distance.
 */
class Stitcher(
    private val firstTopCrop: Int,
    private val laterTopCrop: Int,
    private val bottomCrop: Int,
) {
    private class Gray(val pixels: IntArray, val w: Int, val h: Int)

    private val segments = mutableListOf<Bitmap>()
    private var prev: Gray? = null

    /**
     * Adds a frame; takes ownership of it (recycled before returning).
     * Returns false when the frame brought (almost) no new content —
     * scrolling has stopped or the end of the page was reached.
     */
    fun add(frame: Bitmap): Boolean {
        val topCrop = if (segments.isEmpty()) firstTopCrop else laterTopCrop
        val contentBottom = frame.height - bottomCrop
        val contentHeight = contentBottom - topCrop
        if (contentHeight <= 0) {
            frame.recycle()
            return false
        }

        val gray = toGray(frame, topCrop, contentHeight)

        if (segments.isEmpty()) {
            segments += copyRegion(frame, topCrop, contentBottom)
            prev = gray
            frame.recycle()
            return true
        }

        val previous = prev!!
        val scaleY = contentHeight.toFloat() / gray.h

        val newStartSmall = findNewContentStart(previous, gray)
        val newStartFull = if (newStartSmall != null) {
            topCrop + (newStartSmall * scaleY).toInt()
        } else {
            // Weak match (animated content, images) — assume nominal scroll distance.
            val assumedScroll = (frame.height * FALLBACK_SCROLL_FRACTION).toInt()
            (contentBottom - assumedScroll).coerceIn(topCrop, contentBottom)
        }

        prev = gray

        val newContent = contentBottom - newStartFull
        if (newContent < MIN_NEW_CONTENT_PX) {
            frame.recycle()
            return false
        }

        segments += copyRegion(frame, newStartFull, contentBottom)
        frame.recycle()
        return true
    }

    /** Stacks all appended segments into one tall bitmap. Call off the main thread. */
    fun compose(): Bitmap? {
        if (segments.isEmpty()) return null
        val width = segments.first().width
        val totalHeight = segments.sumOf { it.height }.coerceAtMost(MAX_OUTPUT_HEIGHT)
        val out = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        var y = 0
        for (segment in segments) {
            if (y >= totalHeight) break
            canvas.drawBitmap(segment, 0f, y.toFloat(), null)
            y += segment.height
        }
        segments.forEach { it.recycle() }
        segments.clear()
        return out
    }

    private fun copyRegion(frame: Bitmap, top: Int, bottom: Int): Bitmap =
        Bitmap.createBitmap(frame, 0, top, frame.width, bottom - top)

    /** Downscaled grayscale of the frame's content region (middle 80% width, avoids the bubble at screen edges). */
    private fun toGray(frame: Bitmap, top: Int, height: Int): Gray {
        val marginX = frame.width / 10
        val region = Bitmap.createBitmap(frame, marginX, top, frame.width - 2 * marginX, height)
        val w = MATCH_WIDTH
        val h = (height * w.toFloat() / region.width).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(region, w, h, true)
        region.recycle()
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        small.recycle()
        for (i in px.indices) {
            val c = px[i]
            px[i] = (299 * (c shr 16 and 0xFF) + 587 * (c shr 8 and 0xFF) + 114 * (c and 0xFF)) / 1000
        }
        return Gray(px, w, h)
    }

    /**
     * Returns the small-scale row in [cur] where fresh (unseen) content
     * begins, or null when no confident match was found.
     */
    private fun findNewContentStart(prev: Gray, cur: Gray): Int? {
        val strip = STRIP_ROWS
        val refTop = prev.h - strip - STRIP_BOTTOM_MARGIN
        if (refTop < 0 || cur.h - strip < 0) return null

        var bestRow = -1
        var bestSad = Long.MAX_VALUE
        for (m in 0..(cur.h - strip)) {
            var sad = 0L
            var y = 0
            while (y < strip) {
                val prevRowOffset = (refTop + y) * prev.w
                val curRowOffset = (m + y) * cur.w
                var x = 0
                while (x < prev.w) {
                    sad += abs(prev.pixels[prevRowOffset + x] - cur.pixels[curRowOffset + x])
                    x += 2
                }
                y += 2
            }
            if (sad < bestSad) {
                bestSad = sad
                bestRow = m
            }
        }

        val samples = ((strip + 1) / 2) * ((prev.w + 1) / 2)
        val avgDiffPerPixel = bestSad / samples
        if (avgDiffPerPixel > MATCH_THRESHOLD) return null

        // prev's bottom row (prev.h) corresponds to cur row bestRow + (prev.h - refTop)
        return bestRow + (prev.h - refTop)
    }

    private companion object {
        const val MATCH_WIDTH = 180
        const val STRIP_ROWS = 48
        const val STRIP_BOTTOM_MARGIN = 12
        const val MATCH_THRESHOLD = 16L
        const val MIN_NEW_CONTENT_PX = 90
        const val FALLBACK_SCROLL_FRACTION = 0.40f
        const val MAX_OUTPUT_HEIGHT = 16000
    }
}
