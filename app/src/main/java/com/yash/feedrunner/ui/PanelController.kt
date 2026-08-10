package com.yash.feedrunner.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yash.feedrunner.BuildConfig
import com.yash.feedrunner.api.ClaudeClient
import com.yash.feedrunner.api.ClaudeException
import com.yash.feedrunner.data.ReadState
import com.yash.feedrunner.data.ResultStore
import com.yash.feedrunner.work.AnalysisManager
import com.yash.feedrunner.data.VoiceRulesStore
import java.util.concurrent.Executors
import com.yash.feedrunner.ui.theme.FeedRunnerTheme

/**
 * Owns the reply panel overlay: showing it, running the API calls that fill it,
 * persisting the result, and copying drafts.
 *
 * Network work runs on a single background thread; every state write is posted
 * back to the main thread, since Compose state must be mutated there.
 */
class PanelController(
    private val context: Context,
    windowManager: WindowManager,
    private val resultStore: ResultStore,
    private val analysisManager: AnalysisManager,
    private val onVisibilityChanged: (visible: Boolean) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val window = OverlayWindow(context, windowManager)
    private val voiceRulesStore = VoiceRulesStore(context)
    private val readState = ReadState(context)

    private val claude: ClaudeClient? by lazy {
        BuildConfig.ANTHROPIC_API_KEY
            .takeIf { it.isNotBlank() }
            ?.let { ClaudeClient(it) }
    }

    private var state by mutableStateOf<PanelState>(PanelState.Loading)

    /**
     * The analysis job this panel is currently displaying, if any. The job keeps
     * running after dismissal; this only records whether we still care.
     */
    private var watchedJobId: Long? = null

    /** Context for the drafts currently on screen; reused by refinements. */
    private var postContext: PostContext? = null

    /** Which stored result is on screen, so refinements update the right one. */
    private var currentResultId: Long? = null

    /** Kept so a failed chat turn can be retried without retyping it. */
    private var lastChatMessage: String? = null

    /** Bumped on every dismiss so a late response can't write into a closed panel. */
    private var generation = 0

    val isShowing: Boolean get() = window.isShowing

    /**
     * Hands [screenshot] to the background runner and opens the panel on its
     * loading state. Dismissing the panel does not cancel the job.
     */
    fun analyze(screenshot: Bitmap?) {
        if (screenshot == null) return
        val jobId = analysisManager.submit(screenshot)
        openWindow()
        watchedJobId = jobId
        state = PanelState.Loading
    }

    /** True when the open panel is waiting on this particular job. */
    fun isWatching(jobId: Long): Boolean = isShowing && watchedJobId == jobId

    /** Shows a just-finished analysis, without the "saved result" framing. */
    fun showFinished(resultId: Long) {
        val stored = resultStore.load(resultId) ?: return
        watchedJobId = null
        readState.markViewed(stored.savedAtMillis)
        postContext = stored.postContext
        currentResultId = stored.savedAtMillis
        state = PanelState.Ready(
            postContext = stored.postContext,
            drafts = stored.drafts,
            thumbnailPath = stored.thumbnailPath,
            capturePath = stored.capturePath,
            chat = stored.chat,
        )
    }

    fun showFailure(message: String) {
        watchedJobId = null
        state = PanelState.Error(message)
    }

    /** Reopens the most recent stored result — no capture, no API call. */
    fun showLastResult() {
        val all = resultStore.loadAll()
        val newest = all.firstOrNull()
        if (newest == null) {
            Toast.makeText(context, "No saved results yet", Toast.LENGTH_SHORT).show()
            return
        }
        openWindow()
        showStored(newest, all)
    }

    /** Switches the open panel to another stored result. Still no API call. */
    private fun selectResult(savedAtMillis: Long) {
        val all = resultStore.loadAll()
        val target = all.firstOrNull { it.savedAtMillis == savedAtMillis } ?: return
        showStored(target, all)
    }

    private fun showStored(target: StoredResult, all: List<StoredResult>) {
        readState.markViewed(target.savedAtMillis)
        postContext = target.postContext
        currentResultId = target.savedAtMillis
        state = PanelState.Ready(
            postContext = target.postContext,
            drafts = target.drafts,
            source = ResultSource.Cached(target.savedAtMillis),
            history = all.map {
                HistoryEntry(it.savedAtMillis, it.postContext.author, it.thumbnailPath)
            },
            chat = target.chat,
            thumbnailPath = target.thumbnailPath,
            capturePath = target.capturePath,
        )
    }

    fun dismiss() {
        // Bumping the generation only cancels panel-scoped work (refine, chat).
        // The analysis job runs in AnalysisManager and is deliberately unaffected.
        generation++
        watchedJobId = null
        // Hand the keyboard and back button back to the app underneath.
        window.setFocusable(false)
        handler.removeCallbacksAndMessages(null)
        window.dismiss()
        onVisibilityChanged(false)
    }

    fun shutdown() {
        dismiss()
        worker.shutdownNow()
    }

    private fun openWindow() {
        generation++
        state = PanelState.Loading
        window.show(gravity = Gravity.BOTTOM) {
            FeedRunnerTheme {
                ReplyPanel(
                    state = state,
                    onDraftCopy = ::copyDraft,
                    onToggleUsed = ::toggleUsed,
                    onRefine = ::refineDraft,
                    onSelectResult = ::selectResult,
                    onSendChat = ::sendChat,
                    onRetryChat = ::retryChat,
                    onCopyText = ::copyText,
                    onChatFocusChanged = window::setFocusable,
                    onRetry = ::dismiss,
                    onDismiss = ::dismiss,
                )
            }
        }
        onVisibilityChanged(true)
    }

    private fun refineDraft(draft: Draft, refinement: Refinement) {
        val client = claude ?: return
        // Not named `context`: that would shadow the constructor's Context.
        val activeContext = postContext ?: return
        updateDraft(draft.id) { it.copy(refining = true) }

        val requestGeneration = generation
        val voiceRules = voiceRulesStore.rules

        worker.execute {
            val result = runCatching {
                client.refine(
                    draft = draft,
                    refinement = refinement,
                    postContext = activeContext,
                    extraVoiceRules = voiceRules,
                )
            }

            handler.post {
                if (requestGeneration != generation) return@post
                result
                    .onSuccess { rewritten ->
                        updateDraft(draft.id) { it.copy(text = rewritten, refining = false) }
                        val ready = state as? PanelState.Ready
                        val resultId = currentResultId
                        if (ready != null && resultId != null) {
                            resultStore.updateDrafts(resultId, ready.drafts)
                        }
                    }
                    .onFailure { error ->
                        Log.w(TAG, "Refinement failed", error)
                        updateDraft(draft.id) { it.copy(refining = false) }
                        Toast.makeText(context, userMessage(error), Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    /** Sends a chat turn about the post on screen, and persists the exchange. */
    private fun sendChat(message: String) {
        val client = claude ?: return
        val activeContext = postContext ?: return
        val ready = state as? PanelState.Ready ?: return

        val history = ready.chat
        val withUserTurn = history + ChatMessage(ChatRole.USER, message)
        state = ready.copy(chat = withUserTurn, chatPending = true, chatError = null)

        // Captured now: the panel can be closed and a different result selected
        // before this lands, and the reply belongs to the result it was asked about.
        val resultId = currentResultId
        resultId?.let { resultStore.updateChat(it, withUserTurn) }
        lastChatMessage = message

        val voiceRules = voiceRulesStore.rules
        val drafts = ready.drafts

        worker.execute {
            val result = runCatching {
                client.chat(
                    postContext = activeContext,
                    drafts = drafts,
                    history = history,
                    userMessage = message,
                    extraVoiceRules = voiceRules,
                )
            }

            handler.post {
                result
                    .onSuccess { reply ->
                        val withReply = withUserTurn + ChatMessage(ChatRole.ASSISTANT, reply)
                        // Written through first, unconditionally. Gating this on the
                        // panel still being open lost answers whenever the sheet was
                        // closed mid-request, and lost them permanently.
                        resultId?.let { resultStore.updateChat(it, withReply) }

                        val current = state as? PanelState.Ready
                        if (current != null && currentResultId == resultId) {
                            state = current.copy(chat = withReply, chatPending = false)
                        }
                    }
                    .onFailure { error ->
                        Log.w(TAG, "Chat failed", error)
                        val current = state as? PanelState.Ready
                        if (current != null && currentResultId == resultId) {
                            // Shown in the thread rather than as a toast: a toast
                            // over someone else's app is easy to miss entirely,
                            // which read as the answer never arriving.
                            state = current.copy(
                                chatPending = false,
                                chatError = userMessage(error),
                            )
                        }
                    }
            }
        }
    }

    /** Re-sends the last chat turn after a failure, dropping the failed user turn. */
    private fun retryChat() {
        val message = lastChatMessage ?: return
        val ready = state as? PanelState.Ready ?: return
        // Drop the user turn the failed attempt added, so the retry does not
        // duplicate it in the history sent to the model.
        val trimmed = ready.chat.dropLastWhile { it.role == ChatRole.USER }
        state = ready.copy(chat = trimmed, chatError = null)
        sendChat(message)
    }

    private fun userMessage(error: Throwable): String = when (error) {
        is ClaudeException -> error.message ?: "Something went wrong."
        else -> error.message?.takeIf { it.isNotBlank() }?.let { "Request failed: $it" }
            ?: "Request failed. Check your connection and retry."
    }

    private fun updateDraft(draftId: Int, transform: (Draft) -> Draft) {
        val ready = state as? PanelState.Ready ?: return
        state = ready.copy(
            drafts = ready.drafts.map { if (it.id == draftId) transform(it) else it },
        )
    }

    /** Copies without a toast: the chat bubble shows its own inline confirmation. */
    private fun copyText(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("reply", text))
    }

    private fun copyDraft(draft: Draft) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("reply", draft.text))
        // Copying is the closest thing to "I sent this" that the app can observe,
        // so it doubles as the used marker.
        setUsed(draft, used = true)
        // Deliberately does not dismiss: copying one draft is often followed by
        // copying another, or by carrying on the chat.
    }

    /** Flips the used marker and writes it through, so a reopen still shows it. */
    private fun setUsed(draft: Draft, used: Boolean) {
        val ready = state as? PanelState.Ready ?: return
        if (ready.drafts.none { it.id == draft.id }) return

        val updated = ready.drafts.map { if (it.id == draft.id) it.copy(used = used) else it }
        state = ready.copy(drafts = updated)
        currentResultId?.let { resultStore.updateDrafts(it, updated) }
    }

    private fun toggleUsed(draft: Draft) = setUsed(draft, used = !draft.used)

    private companion object {
        const val TAG = "PanelController"
    }
}
