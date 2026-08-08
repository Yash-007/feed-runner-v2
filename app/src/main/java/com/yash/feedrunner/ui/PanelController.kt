package com.yash.feedrunner.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yash.feedrunner.data.ResultStore

/**
 * Owns the reply panel overlay: showing it, driving its state, persisting the
 * result, and copying drafts.
 *
 * Milestone 3 replaces the mock delays in [runAnalysis] and [refineDraft] with
 * real API calls; nothing else here or in the UI needs to change.
 */
class PanelController(
    private val context: Context,
    windowManager: WindowManager,
    private val resultStore: ResultStore,
    private val onVisibilityChanged: (visible: Boolean) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val window = OverlayWindow(context, windowManager)

    private var state by mutableStateOf<PanelState>(PanelState.Loading)

    /** Held only until the analysis finishes, so the result can store a thumbnail. */
    private var pendingScreenshot: Bitmap? = null

    val isShowing: Boolean get() = window.isShowing

    /** Runs a fresh analysis on [screenshot] and shows the result. Costs an API call. */
    fun analyze(screenshot: Bitmap?) {
        pendingScreenshot = screenshot
        openWindow()
        runAnalysis()
    }

    /** Reopens the stored result — no capture, no API call. */
    fun showLastResult() {
        val stored = resultStore.load()
        if (stored == null) {
            Toast.makeText(context, "No saved result yet", Toast.LENGTH_SHORT).show()
            return
        }
        openWindow()
        state = PanelState.Ready(
            verdict = stored.verdict,
            drafts = stored.drafts,
            source = ResultSource.Cached(stored.savedAtMillis, stored.thumbnailPath),
        )
    }

    fun dismiss() {
        handler.removeCallbacksAndMessages(null)
        releasePendingScreenshot()
        window.dismiss()
        onVisibilityChanged(false)
    }

    private fun openWindow() {
        state = PanelState.Loading
        window.show(gravity = Gravity.BOTTOM) {
            MaterialTheme {
                ReplyPanel(
                    state = state,
                    onDraftCopy = ::copyDraft,
                    onRefine = ::refineDraft,
                    onRetry = ::runAnalysis,
                    onDismiss = ::dismiss,
                )
            }
        }
        onVisibilityChanged(true)
    }

    /** MOCK: stands in for the screenshot -> Claude round trip. */
    private fun runAnalysis() {
        state = PanelState.Loading
        handler.postDelayed({
            val ready = MockData.ready()
            state = ready
            resultStore.save(ready.verdict, ready.drafts, pendingScreenshot)
            releasePendingScreenshot()
        }, MOCK_ANALYSIS_MS)
    }

    /** MOCK: stands in for the follow-up refinement call. */
    private fun refineDraft(draft: Draft, refinement: Refinement) {
        updateDraft(draft.id) { it.copy(refining = true) }
        handler.postDelayed({
            updateDraft(draft.id) { MockData.refine(it, refinement) }
            (state as? PanelState.Ready)?.let { resultStore.updateDrafts(it.drafts) }
        }, MOCK_REFINE_MS)
    }

    private fun updateDraft(draftId: Int, transform: (Draft) -> Draft) {
        val ready = state as? PanelState.Ready ?: return
        state = ready.copy(
            drafts = ready.drafts.map { if (it.id == draftId) transform(it) else it },
        )
    }

    private fun copyDraft(draft: Draft) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("reply", draft.text))
        Toast.makeText(context, "Copied — paste in X", Toast.LENGTH_SHORT).show()
        dismiss()
    }

    private fun releasePendingScreenshot() {
        pendingScreenshot?.recycle()
        pendingScreenshot = null
    }

    private companion object {
        const val MOCK_ANALYSIS_MS = 1400L
        const val MOCK_REFINE_MS = 900L
    }
}
