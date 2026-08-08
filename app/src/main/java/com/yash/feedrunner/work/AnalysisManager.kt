package com.yash.feedrunner.work

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.yash.feedrunner.BuildConfig
import com.yash.feedrunner.api.ClaudeClient
import com.yash.feedrunner.api.ClaudeException
import com.yash.feedrunner.api.ImagePrep
import com.yash.feedrunner.data.ResultStore
import com.yash.feedrunner.data.VoiceRulesStore
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs analyses independently of the panel.
 *
 * Analysis used to live in the panel controller, where dismissing the sheet
 * cancelled the request and the work was lost. It lives here instead so that
 * closing the panel only stops you *watching* a job: it keeps running, and the
 * result is saved either way. Several captures can therefore be in flight while
 * you carry on scrolling.
 */
class AnalysisManager(
    context: Context,
    private val resultStore: ResultStore,
) {
    sealed interface Update {
        val jobId: Long

        data class Done(
            override val jobId: Long,
            val resultId: Long,
            val author: String,
        ) : Update

        data class Failed(override val jobId: Long, val message: String) : Update
    }

    private val pool = Executors.newFixedThreadPool(MAX_PARALLEL)
    private val handler = Handler(Looper.getMainLooper())
    private val voiceRulesStore = VoiceRulesStore(context)
    private val running = AtomicInteger(0)
    private var nextJobId = 1L

    private val claude: ClaudeClient? by lazy {
        BuildConfig.ANTHROPIC_API_KEY.takeIf { it.isNotBlank() }?.let { ClaudeClient(it) }
    }

    /** Called on the main thread whenever a job finishes. */
    var onUpdate: ((Update) -> Unit)? = null

    /** Called on the main thread whenever the in-flight count changes. */
    var onActiveCountChanged: ((Int) -> Unit)? = null

    val activeCount: Int get() = running.get()

    val hasApiKey: Boolean get() = claude != null

    /**
     * Queues [screenshot] for analysis and returns the job id to watch.
     * Takes ownership of the bitmap and recycles it when the job ends.
     */
    fun submit(screenshot: Bitmap): Long {
        val jobId = nextJobId++
        val client = claude

        if (client == null) {
            screenshot.recycle()
            handler.post {
                onUpdate?.invoke(
                    Update.Failed(
                        jobId,
                        "No API key. Add anthropic.apiKey to local.properties and rebuild.",
                    ),
                )
            }
            return jobId
        }

        running.incrementAndGet()
        notifyCount()

        pool.execute {
            val voiceRules = voiceRulesStore.rules
            val outcome = runCatching {
                val segments = ImagePrep.toBase64Segments(screenshot)
                val analysis = client.analyze(segments, voiceRules)
                val saved = resultStore.save(
                    postContext = analysis.postContext,
                    drafts = analysis.drafts,
                    screenshot = screenshot,
                )
                saved.id to analysis.postContext.author
            }

            handler.post {
                if (!screenshot.isRecycled) screenshot.recycle()
                running.decrementAndGet()
                notifyCount()
                outcome
                    .onSuccess { (resultId, author) ->
                        onUpdate?.invoke(Update.Done(jobId, resultId, author))
                    }
                    .onFailure { error ->
                        Log.w(TAG, "Analysis failed", error)
                        onUpdate?.invoke(Update.Failed(jobId, userMessage(error)))
                    }
            }
        }

        return jobId
    }

    fun shutdown() {
        pool.shutdownNow()
        handler.removeCallbacksAndMessages(null)
    }

    private fun notifyCount() {
        val count = running.get()
        handler.post { onActiveCountChanged?.invoke(count) }
    }

    private fun userMessage(error: Throwable): String = when (error) {
        is ClaudeException -> error.message ?: "Something went wrong."
        else -> error.message?.takeIf { it.isNotBlank() }?.let { "Request failed: $it" }
            ?: "Request failed. Check your connection."
    }

    private companion object {
        const val TAG = "AnalysisManager"

        /** Enough for a burst of captures without hammering the API or the network. */
        const val MAX_PARALLEL = 3
    }
}
