package com.yash.feedrunner.data

import android.content.Context
import android.util.Log
import com.yash.feedrunner.ui.IdeaSeed
import com.yash.feedrunner.ui.PostIdea
import com.yash.feedrunner.ui.SeedSource
import com.yash.feedrunner.ui.SeedStatus
import com.yash.feedrunner.ui.StoredSeed
import java.util.concurrent.Executors

/**
 * The Idea Bank as the rest of the app sees it.
 *
 * Recording a seed never blocks or fails a generation: it queues locally and then
 * tries to flush in the background. Browsing reads the server, falling back to
 * whatever is still queued so the screen is never empty just because the laptop
 * is off.
 */
class IdeaBankRepository(context: Context) {

    private val outbox = SeedOutbox(context)
    private val config = BackendConfig(context)
    private val api = IdeaBankApi(config)

    // Single thread: uploads are ordered and none of this is latency-sensitive.
    private val worker = Executors.newSingleThreadExecutor()

    val backendConfig: BackendConfig get() = config

    val pendingCount: Int get() = outbox.size

    /**
     * Banks a seed from a generation. Silent by design: the reply flow must not
     * change shape because the idea bank is unreachable, so every failure here
     * ends in the outbox and a log line.
     *
     * [clientSeedId] must be derived from the capture, so calling this twice for
     * the same post is a no-op rather than a duplicate.
     */
    fun record(
        seed: IdeaSeed?,
        source: SeedSource,
        clientSeedId: String,
        postAuthor: String = "",
        postText: String = "",
        capturedAtMillis: Long = System.currentTimeMillis(),
    ) {
        if (seed == null || seed.isEmpty) return

        val entry = StoredSeed(
            remoteId = null,
            clientSeedId = clientSeedId,
            source = source,
            status = SeedStatus.NEW,
            seed = seed,
            postAuthor = postAuthor,
            postText = postText,
            createdAtMillis = capturedAtMillis,
        )
        if (!outbox.add(entry)) return

        Log.i(TAG, "banked seed $clientSeedId (${seed.themeTags.joinToString(",")})")
        flushAsync()
    }

    /** Queues a hand-typed idea. Same path as a model seed, so it also survives offline. */
    fun addManual(note: String) {
        val trimmed = note.trim()
        if (trimmed.isEmpty()) return
        val now = System.currentTimeMillis()
        outbox.add(
            StoredSeed(
                remoteId = null,
                clientSeedId = "manual-$now",
                source = SeedSource.MANUAL,
                status = SeedStatus.NEW,
                seed = IdeaSeed(),
                note = trimmed,
                createdAtMillis = now,
            ),
        )
        flushAsync()
    }

    fun flushAsync() {
        if (!config.isConfigured) return
        worker.execute { flush() }
    }

    /** Uploads everything queued. Returns how many made it. */
    fun flush(): Int {
        if (!config.isConfigured) return 0
        var sent = 0
        for (entry in outbox.pending()) {
            val uploaded = runCatching { api.createSeed(entry) }
            if (uploaded.isSuccess) {
                // The server treats a repeat client_seed_id as the same seed, so
                // retiring the entry is safe even if this was a retry.
                outbox.remove(entry.clientSeedId)
                sent++
            } else {
                Log.w(TAG, "seed upload failed, keeping it queued", uploaded.exceptionOrNull())
                // Stop on the first failure: the rest will fail the same way.
                break
            }
        }
        return sent
    }

    /**
     * Everything to show in the Ideas screen, newest first. Flushes first so a
     * seed banked while offline appears as soon as the backend comes back.
     */
    fun loadSeeds(filter: SeedStatus?): Result<List<StoredSeed>> {
        flush()
        return runCatching { api.listSeeds(filter) }
            .map { remote ->
                // Queued seeds are shown alongside so nothing is invisible.
                val pending = outbox.pending()
                    .filter { filter == null || filter == SeedStatus.NEW }
                (pending + remote).sortedByDescending { it.createdAtMillis }
            }
    }

    /** Used when the server cannot be reached, so the screen still shows something. */
    fun queuedSeeds(): List<StoredSeed> = outbox.pending().sortedByDescending { it.createdAtMillis }

    fun setStatus(seed: StoredSeed, status: SeedStatus): Result<StoredSeed> {
        val id = seed.remoteId
            ?: return Result.failure(IdeaBankException("Not synced yet, needs the backend"))
        return runCatching { api.setStatus(id, status) }
    }

    fun delete(seed: StoredSeed): Result<Unit> {
        val id = seed.remoteId ?: return Result.success(Unit).also {
            outbox.remove(seed.clientSeedId)
        }
        return runCatching { api.deleteSeed(id) }
    }

    fun generateIdeas(seeds: List<StoredSeed>, steer: String): Result<List<PostIdea>> {
        val ids = seeds.mapNotNull { it.remoteId }
        if (ids.isEmpty()) {
            return Result.failure(
                IdeaBankException("Those seeds are not on the server yet. Check the address."),
            )
        }
        return runCatching { api.generateIdeas(ids, steer) }
    }

    fun checkHealth(): Boolean = api.health()

    private companion object {
        const val TAG = "IdeaBank"
    }
}
