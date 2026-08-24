package com.yash.feedrunner.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.view.Display
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service whose only job is taking screenshots on demand
 * (bubble tap). It ignores all accessibility events.
 *
 * Must be enabled by the user in Settings > Accessibility > Feed Runner.
 */
class CaptureService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    /**
     * The service already receives window-state events; the package name on them
     * is how the bubble knows whether it is floating over X or LinkedIn. Our own
     * overlays are ignored so opening a panel does not clobber the answer.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return
        lastForegroundPackage = pkg
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /**
     * Captures the default display. Calls back on the main thread with a
     * software bitmap, or (null, errorCode) on failure.
     *
     * Known error codes from AccessibilityService:
     * 1 = internal error, 2 = no accessibility access, 3 = called too fast
     * (rate limit ~1/sec), 4 = invalid display.
     */
    fun capture(onResult: (Bitmap?, Int) -> Unit) {
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val hardwareBitmap = Bitmap.wrapHardwareBuffer(
                        screenshot.hardwareBuffer,
                        screenshot.colorSpace,
                    )
                    // Hardware bitmaps can't be compressed/cropped — copy to software.
                    val softwareBitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                    screenshot.hardwareBuffer.close()
                    if (softwareBitmap != null) {
                        onResult(softwareBitmap, 0)
                    } else {
                        onResult(null, -1)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    onResult(null, errorCode)
                }
            },
        )
    }

    /**
     * Injects a slow upward swipe (scrolls content down ~40% of the screen).
     * Slow duration keeps fling momentum minimal so frames overlap predictably;
     * the stitcher's template match absorbs the residual drift.
     */
    fun performScroll(onDone: (Boolean) -> Unit) {
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        val path = Path().apply {
            moveTo(width / 2f, height * 0.70f)
            lineTo(width / 2f, height * 0.30f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, SCROLL_DURATION_MS))
            .build()
        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) = onDone(true)
                override fun onCancelled(gestureDescription: GestureDescription?) = onDone(false)
            },
            null,
        )
        if (!dispatched) onDone(false)
    }

    companion object {
        /** Package of the app most recently in the foreground. */
        @Volatile
        var lastForegroundPackage: String? = null

        private const val SCROLL_DURATION_MS = 700L

        @Volatile
        var instance: CaptureService? = null
            private set
    }
}
