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
import com.yash.feedrunner.api.humanMessage
import com.yash.feedrunner.api.ImagePrep
import com.yash.feedrunner.data.DraftPick
import com.yash.feedrunner.data.IdeaBankRepository
import com.yash.feedrunner.data.RepostStore
import com.yash.feedrunner.data.VoiceRulesStore
import java.io.File
import java.util.concurrent.Executors
import com.yash.feedrunner.ui.theme.FeedRunnerTheme

/**
 * Owns the compose overlay: capture in, optional thought or instruction, six
 * post or quote drafts out.
 *
 * Generation runs on a background executor and is deliberately not cancelled by
 * dismissing the sheet, which was the complaint about the reply flow. If it
 * lands while the sheet is closed you get a toast, and the result is held so
 * reopening Repost shows it instead of a blank composer.
 */
class RepostController(
    private val context: Context,
    windowManager: WindowManager,
    private val onVisibilityChanged: (visible: Boolean) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val window = OverlayWindow(context, windowManager)
    private val voiceRulesStore = VoiceRulesStore(context)
    private val store = RepostStore(context)
    private val ideaBank = IdeaBankRepository(context)
    private val captureDir = File(context.filesDir, "repost").apply { mkdirs() }

    private val claude: ClaudeClient? by lazy {
        BuildConfig.ANTHROPIC_API_KEY.takeIf { it.isNotBlank() }?.let { ClaudeClient(it) }
    }

    private var state by mutableStateOf<RepostState>(RepostState.Composing)
    private var mode by mutableStateOf(RepostMode.POST)
    private var userText by mutableStateOf("")
    private var capturePath by mutableStateOf<String?>(null)

    /**
     * The last finished generation, whether it landed while the sheet was closed
     * or in an earlier run of the process. Seeded from disk so a restart does not
     * throw away drafts and the conversation about them.
     */
    private var heldResult: RepostResult? = store.load()

    /**
     * True only for a result that landed while the sheet was closed and has not
     * been looked at yet.
     *
     * Without this the stored result made Repost permanently mean "reopen": every
     * tap showed the old drafts and a new capture became impossible. Capture is the
     * primary action, so anything older than an unseen hand-off is reachable from
     * the composer instead.
     */
    private var heldUnseen = false

    /** Kept so a failed chat turn can be retried without retyping it. */
    private var lastChatMessage: String? = null

    val isShowing: Boolean get() = window.isShowing

    /** Only an unseen hand-off should pre-empt a fresh capture. */
    val hasUnseenResult: Boolean get() = heldUnseen && heldResult != null

    /** How long ago the held drafts were generated, for the menu subtitle. */
    val heldResultAge: String? get() = heldResult?.let { relativeAge(it.savedAtMillis) }

    /** Opens the composer for a fresh capture, with the keyboard already up. */
    fun start(screenshot: Bitmap) {
        capturePath = writeCapture(screenshot)
        screenshot.recycle()
        state = RepostState.Composing
        userText = ""
        open()
    }

    /** Reopens drafts that finished while the sheet was closed. No new API call. */
    fun showHeldResult() {
        val held = heldResult ?: return
        heldUnseen = false
        mode = held.mode
        capturePath = held.capturePath
        state = RepostState.Ready(held)
        open()
    }

    fun dismiss() {
        window.setFocusable(false)
        window.dismiss()
        onVisibilityChanged(false)
    }

    fun shutdown() {
        dismiss()
        worker.shutdownNow()
        handler.removeCallbacksAndMessages(null)
    }

    private fun open() {
        window.show(gravity = Gravity.BOTTOM) {
            FeedRunnerTheme {
                RepostPanel(
                    state = state,
                    mode = mode,
                    capturePath = capturePath,
                    userText = userText,
                    onModeChange = { mode = it },
                    onUserTextChange = { userText = it },
                    onGenerate = ::generate,
                    onCopyText = ::copyText,
                    onCopyDraft = ::copyDraft,
                    onSendChat = ::sendChat,
                    onRetryChat = ::retryChat,
                    onToggleUsed = ::toggleUsed,
                    heldDraftsAge = heldResult
                        ?.takeIf { state is RepostState.Composing }
                        ?.let { relativeAge(it.savedAtMillis) },
                    onOpenHeldDrafts = ::showHeldResult,
                    onFocusChanged = window::setFocusable,
                    onDismiss = ::dismiss,
                )
            }
        }
        // Focusable up front so the composer's auto-focus can raise the keyboard.
        window.setFocusable(true)
        onVisibilityChanged(true)
    }

    private fun generate() {
        val client = claude
        if (client == null) {
            state = RepostState.Error(
                "No API key. Add anthropic.apiKey to local.properties and rebuild.",
            )
            return
        }
        val path = capturePath
        if (path == null) {
            state = RepostState.Error("Capture was lost. Try again.")
            return
        }

        state = RepostState.Loading
        val requestedMode = mode
        val text = userText.trim()
        val voiceRules = voiceRulesStore.rules

        worker.execute {
            val outcome = runCatching {
                val bitmap = decodeScaledForApi(path)
                    ?: throw ClaudeException("Could not read the capture. Try again.")
                val segments = ImagePrep.toBase64Segments(bitmap)
                client.suggestPosts(requestedMode, segments, text, voiceRules)
            }

            handler.post {
                outcome
                    .onSuccess { analysis ->
                        val result = RepostResult(
                            mode = requestedMode,
                            capture = analysis.capture,
                            reading = analysis.reading,
                            drafts = analysis.drafts,
                            capturePath = path,
                            savedAtMillis = System.currentTimeMillis(),
                        )
                        store.save(result)
                        // First generation for this capture only; refinements and
                        // chat never come through here. Quote mode only: in post
                        // mode the capture is already Yash's own output, so the
                        // prompt returns null and a seed would mean it misread.
                        ideaBank.record(
                            seed = analysis.ideaSeed.takeIf { requestedMode == RepostMode.QUOTE },
                            source = SeedSource.QUOTE,
                            clientSeedId = "${requestedMode.wire}-${result.savedAtMillis}",
                            postAuthor = analysis.capture.quotedAuthor.orEmpty(),
                            postText = analysis.capture.summary,
                            capturedAtMillis = result.savedAtMillis,
                        )
                        heldResult = result
                        if (window.isShowing) {
                            state = RepostState.Ready(result)
                        } else {
                            // Closed mid-flight: hold it rather than throw it away.
                            heldUnseen = true
                            Toast.makeText(
                                context,
                                "${requestedMode.label} drafts ready, tap Repost to see them",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                    .onFailure { error ->
                        Log.w(TAG, "Post generation failed", error)
                        val message = humanMessage(error)
                        if (window.isShowing) {
                            state = RepostState.Error(message)
                        } else {
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    }
            }
        }
    }

    /**
     * Follow-up turn about the drafts on screen. Like [generate] this is not
     * cancelled by dismissal, and the conversation is written through to disk on
     * both the send and the answer so nothing is lost either way.
     */
    private fun sendChat(message: String) {
        val client = claude ?: return
        val ready = state as? RepostState.Ready ?: return
        if (message.isBlank()) return

        val result = ready.result
        val history = result.chat
        val withUserTurn = history + ChatMessage(ChatRole.USER, message)
        lastChatMessage = message
        state = RepostState.Ready(result.copy(chat = withUserTurn), chatPending = true)
        heldResult = result.copy(chat = withUserTurn)
        store.updateChat(result.savedAtMillis, withUserTurn)

        val voiceRules = voiceRulesStore.rules

        worker.execute {
            val outcome = runCatching {
                client.chatPosts(
                    mode = result.mode,
                    capture = result.capture,
                    drafts = result.drafts,
                    history = history,
                    userMessage = message,
                    extraVoiceRules = voiceRules,
                )
            }

            handler.post {
                outcome
                    .onSuccess { reply ->
                        val withReply = withUserTurn + ChatMessage(ChatRole.ASSISTANT, reply)
                        val updated = result.copy(chat = withReply)
                        heldResult = updated
                        store.updateChat(result.savedAtMillis, withReply)
                        // Only paint if this result is still the one on screen.
                        val current = state as? RepostState.Ready
                        if (current?.result?.savedAtMillis == result.savedAtMillis) {
                            state = RepostState.Ready(updated, chatPending = false)
                        }
                    }
                    .onFailure { error ->
                        Log.w(TAG, "Post chat failed", error)
                        val current = state as? RepostState.Ready
                        if (current?.result?.savedAtMillis == result.savedAtMillis) {
                            // In the thread, not a toast: a toast over another app
                            // is easy to miss, which reads as no answer arriving.
                            state = current.copy(
                                chatPending = false,
                                chatError = humanMessage(error),
                            )
                        }
                    }
            }
        }
    }

    /** Re-sends the last chat turn after a failure, dropping the failed user turn. */
    private fun retryChat() {
        val message = lastChatMessage ?: return
        val ready = state as? RepostState.Ready ?: return
        val trimmed = ready.result.chat.dropLastWhile { it.role == ChatRole.USER }
        state = RepostState.Ready(ready.result.copy(chat = trimmed), chatError = null)
        sendChat(message)
    }

    /** Chat and other loose text. Not a draft, so it is not a pick. */
    private fun copyText(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("post", text))
    }

    /** Copying a draft is the choice, so it marks it used and records the pick. */
    private fun copyDraft(draft: PostDraft) {
        copyText(draft.text)
        setUsed(draft, used = true)
    }

    private fun toggleUsed(draft: PostDraft) = setUsed(draft, used = !draft.used)

    /** Flips the marker, persists it, and mirrors it to the backend as a pick. */
    private fun setUsed(draft: PostDraft, used: Boolean) {
        val ready = state as? RepostState.Ready ?: return
        val result = ready.result
        if (result.drafts.none { it.id == draft.id }) return

        val updated = result.drafts.map { if (it.id == draft.id) it.copy(used = used) else it }
        val nextResult = result.copy(drafts = updated)
        state = ready.copy(result = nextResult)
        heldResult = nextResult
        store.save(nextResult)

        val pickId = "${result.mode.wire}-${result.savedAtMillis}-${draft.id}"
        if (used) {
            val current = updated.first { it.id == draft.id }
            ideaBank.recordPick(
                DraftPick(
                    clientPickId = pickId,
                    source = result.mode.wire,
                    variant = current.style.name,
                    thought = current.thought,
                    text = current.text,
                    postAuthor = result.capture.quotedAuthor.orEmpty(),
                    postText = result.capture.summary,
                    pickedAtMillis = System.currentTimeMillis(),
                ),
            )
        } else {
            ideaBank.removePick(pickId)
        }
    }

    /**
     * Reads the capture back from disk rather than holding the bitmap, so a
     * generation that outlives the sheet cannot be holding megabytes of it.
     */
    private fun decodeScaledForApi(path: String): Bitmap? =
        android.graphics.BitmapFactory.decodeFile(path)

    private fun writeCapture(source: Bitmap): String? = runCatching {
        val file = File(captureDir, "capture_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        // Only the newest capture is ever needed; drop the rest.
        captureDir.listFiles()?.forEach { if (it != file) it.delete() }
        file.absolutePath
    }.getOrNull()

    private companion object {
        const val TAG = "RepostController"
    }
}
