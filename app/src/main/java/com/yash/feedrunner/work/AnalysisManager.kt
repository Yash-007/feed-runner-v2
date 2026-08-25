package com.yash.feedrunner.work

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.yash.feedrunner.BuildConfig
import com.yash.feedrunner.data.BackendConfig
import com.yash.feedrunner.data.CopilotApi
import com.yash.feedrunner.data.IdeaBankApi
import com.yash.feedrunner.api.humanMessage
import com.yash.feedrunner.api.ImagePrep
import com.yash.feedrunner.data.IdeaBankRepository
import com.yash.feedrunner.data.ResultStore
import com.yash.feedrunner.data.VoiceRulesStore
import com.yash.feedrunner.data.WordLimitStore
import com.yash.feedrunner.ui.Platform
import com.yash.feedrunner.ui.SeedSource
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
    private val ideaBank: IdeaBankRepository,
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
    private val wordLimits = WordLimitStore(context)
    private val running = AtomicInteger(0)
    private var nextJobId = 1L

    // Drafting happens on the backend now, so there is no key here to be missing
    // and nothing to degrade to.
    private val claude: CopilotApi by lazy { CopilotApi(IdeaBankApi(BackendConfig(context))) }

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
    fun submit(screenshot: Bitmap, platform: Platform): Long {
        val jobId = nextJobId++
        val client = claude


        running.incrementAndGet()
        notifyCount()

        pool.execute {
            val voiceRules = voiceRulesStore.rules
            val outcome = runCatching {
                val segments = ImagePrep.toBase64Segments(screenshot)
                val analysis = client.analyze(segments, voiceRules, platform, wordLimits.replyLimit)
                val saved = resultStore.save(
                    postContext = analysis.postContext,
                    drafts = analysis.drafts,
                    screenshot = screenshot,
                    platform = platform,
                )
                // Banked off the first generation only, keyed on the saved result
                // so a reopen or a refinement can never bank the same post twice.
                ideaBank.record(
                    seed = analysis.ideaSeed,
                    source = SeedSource.REPLY,
                    clientSeedId = "reply-${saved.id}",
                    postAuthor = analysis.postContext.author,
                    postText = analysis.postContext.postText,
                    capturedAtMillis = saved.id,
                    platform = platform,
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

    private fun userMessage(error: Throwable): String = humanMessage(error)

    private companion object {
        const val TAG = "AnalysisManager"

        /** Enough for a burst of captures without hammering the API or the network. */
        const val MAX_PARALLEL = 3
    }
}
