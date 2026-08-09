package com.yash.feedrunner.data

import android.content.Context
import android.util.Log
import com.yash.feedrunner.ui.IdeaSeed
import com.yash.feedrunner.ui.SeedSource
import com.yash.feedrunner.ui.SeedStatus
import com.yash.feedrunner.ui.StoredSeed
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Seeds waiting to reach the backend.
 *
 * A seed is written here first and removed only once the server has it, so a
 * generation that happens with the laptop asleep or off the network is not lost.
 * Entries carry a `client_seed_id` derived from the capture, which makes the
 * upload idempotent: a retry after an ambiguous failure cannot double-insert.
 */
class SeedOutbox(context: Context) {

    private val lock = Any()
    private val file = File(context.filesDir, "seed_outbox.json")

    /** Queues a seed. Returns false when this capture is already queued. */
    fun add(entry: StoredSeed): Boolean = synchronized(lock) {
        val entries = read()
        if (entries.any { it.optString("client_seed_id") == entry.clientSeedId }) return false
        entries.add(entry.toJson())
        write(entries)
        true
    }

    fun pending(): List<StoredSeed> = synchronized(lock) {
        read().mapNotNull { it.toStoredSeed() }
    }

    val size: Int get() = synchronized(lock) { read().size }

    fun remove(clientSeedId: String) {
        synchronized(lock) {
            val kept = read().filter { it.optString("client_seed_id") != clientSeedId }
            write(kept.toMutableList())
        }
    }

    private fun read(): MutableList<JSONObject> {
        if (!file.exists()) return mutableListOf()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapTo(mutableListOf()) { array.getJSONObject(it) }
        }.onFailure { Log.w(TAG, "Failed to read outbox", it) }.getOrDefault(mutableListOf())
    }

    private fun write(entries: List<JSONObject>) {
        runCatching {
            file.writeText(JSONArray().apply { entries.forEach { put(it) } }.toString())
        }.onFailure { Log.w(TAG, "Failed to write outbox", it) }
    }

    private companion object {
        const val TAG = "SeedOutbox"
    }
}

internal fun StoredSeed.toJson(): JSONObject = JSONObject().apply {
    put("client_seed_id", clientSeedId)
    put("source", source.wire)
    put("theme_tags", JSONArray().apply { seed.themeTags.forEach { put(it) } })
    put("tension", seed.tension)
    put("angle_hint", seed.angleHint)
    put("shelf_life", seed.shelfLife)
    put("note", note)
    put("post_author", postAuthor)
    put("post_text", postText)
    put("captured_at_millis", createdAtMillis)
}

internal fun JSONObject.toStoredSeed(): StoredSeed? = runCatching {
    val tagsArray = optJSONArray("theme_tags")
    val tags = (0 until (tagsArray?.length() ?: 0)).mapNotNull { i ->
        tagsArray?.optString(i)?.takeIf { it.isNotEmpty() }
    }
    StoredSeed(
        remoteId = optString("id").takeIf { it.isNotEmpty() && it != "null" },
        clientSeedId = optString("client_seed_id"),
        source = SeedSource.fromWire(optString("source")),
        status = SeedStatus.fromWire(optString("status")),
        seed = IdeaSeed(
            themeTags = tags,
            tension = optString("tension"),
            angleHint = optString("angle_hint"),
            shelfLife = optString("shelf_life"),
        ),
        note = optString("note"),
        postAuthor = optString("post_author"),
        postText = optString("post_text"),
        createdAtMillis = optLong("captured_at_millis"),
    )
}.getOrNull()
