package com.yash.feedrunner.capture

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import kotlin.concurrent.thread

/**
 * Drives the auto-scroll long-capture loop:
 * capture → stitch → inject scroll → wait for settle → capture → …
 * Ends when the user stops it, the content stops moving, or MAX_FRAMES.
 */
class AutoScrollCapture(
    private val service: CaptureService,
    statusBarPx: Int,
    screenHeightPx: Int,
    private val hideBubble: (onHidden: () -> Unit) -> Unit,
    private val showBubble: () -> Unit,
    private val onProgress: (frames: Int) -> Unit,
    private val onFinished: (stitched: Bitmap?, frames: Int) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val stitcher = Stitcher(
        firstTopCrop = statusBarPx,
        laterTopCrop = (screenHeightPx * LATER_TOP_CROP_FRACTION).toInt(),
        bottomCrop = (screenHeightPx * BOTTOM_CROP_FRACTION).toInt(),
    )
    private var frames = 0
    private var retries = 0
    private var finished = false

    fun start() = captureNext()

    /** User-requested stop; stitches whatever was captured so far. */
    fun stop() = finish()

    private fun captureNext() {
        if (finished) return
        hideBubble {
            if (finished) {
                showBubble()
                return@hideBubble
            }
            service.capture { bitmap, errorCode ->
                showBubble()
                if (finished) {
                    bitmap?.recycle()
                    return@capture
                }
                when {
                    bitmap == null && errorCode == 3 && retries < MAX_RETRIES -> {
                        // Rate-limited (~1 capture/sec) — back off and retry.
                        retries++
                        handler.postDelayed({ captureNext() }, RATE_LIMIT_RETRY_MS)
                    }
                    bitmap == null -> finish()
                    else -> {
                        retries = 0
                        frames++
                        onProgress(frames)
                        val hasMore = stitcher.add(bitmap)
                        if (!hasMore || frames >= MAX_FRAMES) {
                            finish()
                        } else {
                            service.performScroll { scrolled ->
                                if (!scrolled) {
                                    finish()
                                } else {
                                    handler.postDelayed({ captureNext() }, SETTLE_MS)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun finish() {
        if (finished) return
        finished = true
        val frameCount = frames
        thread {
            val result = runCatching { stitcher.compose() }.getOrNull()
            handler.post { onFinished(result, frameCount) }
        }
    }

    private companion object {
        const val MAX_FRAMES = 8
        const val MAX_RETRIES = 3
        const val SETTLE_MS = 1100L
        const val RATE_LIMIT_RETRY_MS = 700L

        /** Crop fractions for sticky chrome; tune against the X app if seams appear. */
        const val LATER_TOP_CROP_FRACTION = 0.06f
        const val BOTTOM_CROP_FRACTION = 0.10f
    }
}
