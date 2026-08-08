package com.yash.feedrunner.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yash.feedrunner.data.ResultStore
import java.io.File

/**
 * Owns the repost composer overlay: capture in, optional steer, captions out.
 *
 * The suggestion call is still a stub. Everything around it (window, keyboard,
 * capture handling, copy, selection) is real, so the flow can be judged before
 * the prompt and response shape are settled.
 */
class RepostController(
    private val context: Context,
    windowManager: WindowManager,
    private val onVisibilityChanged: (visible: Boolean) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val window = OverlayWindow(context, windowManager)
    private val captureDir = File(context.filesDir, "repost").apply { mkdirs() }

    private var state by mutableStateOf<RepostState>(RepostState.Composing)
    private var steer by mutableStateOf("")
    private var capturePath by mutableStateOf<String?>(null)

    val isShowing: Boolean get() = window.isShowing

    /** Opens the composer for [screenshot], with the keyboard already up. */
    fun start(screenshot: Bitmap) {
        state = RepostState.Composing
        steer = ""
        capturePath = writeCapture(screenshot)
        screenshot.recycle()

        window.show(gravity = Gravity.BOTTOM) {
            MaterialTheme {
                RepostPanel(
                    state = state,
                    capturePath = capturePath,
                    steer = steer,
                    onSteerChange = { steer = it },
                    onSuggest = ::suggest,
                    onCopyText = ::copyText,
                    onFocusChanged = window::setFocusable,
                    onDismiss = ::dismiss,
                )
            }
        }
        // Focusable up front so the composer's auto-focus can raise the keyboard.
        window.setFocusable(true)
        onVisibilityChanged(true)
    }

    fun dismiss() {
        handler.removeCallbacksAndMessages(null)
        window.setFocusable(false)
        window.dismiss()
        onVisibilityChanged(false)
    }

    /**
     * STUB. Replace with the real caption call once the prompt and response shape
     * are defined; the state machine and UI above do not need to change.
     */
    private fun suggest() {
        state = RepostState.Loading
        val given = steer.trim()
        handler.postDelayed({
            state = RepostState.Ready(
                listOf(
                    CaptionSuggestion(
                        id = 0,
                        note = "STUB — steer was: ${given.ifEmpty { "(none)" }}",
                        text = "placeholder caption one",
                    ),
                    CaptionSuggestion(id = 1, note = "STUB", text = "placeholder caption two"),
                    CaptionSuggestion(id = 2, note = "STUB", text = "placeholder caption three"),
                ),
            )
        }, STUB_DELAY_MS)
    }

    private fun copyText(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("caption", text))
    }

    /**
     * Repost captures are transient, so one file is reused rather than added to
     * [ResultStore], which keeps a pruned history of reply results.
     */
    private fun writeCapture(source: Bitmap): String? = runCatching {
        val file = File(captureDir, "repost_capture.jpg")
        file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        file.absolutePath
    }.getOrNull()

    private companion object {
        const val STUB_DELAY_MS = 900L
    }
}
