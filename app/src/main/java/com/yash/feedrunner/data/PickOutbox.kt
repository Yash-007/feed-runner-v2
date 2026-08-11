package com.yash.feedrunner.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A draft you chose, as sent to the backend. */
data class DraftPick(
    /** Derived from the result and the draft, so re-copying updates one row. */
    val clientPickId: String,
    /** reply, post or quote. */
    val source: String,
    /** The ANGLE for a reply, the STYLE for a post or quote. */
    val variant: String,
    val thought: String,
    val text: String,
    val postAuthor: String,
    val postText: String,
    val pickedAtMillis: Long,
)

/**
 * Pending pick changes, kept in order and collapsed per draft.
 *
 * A pick mirrors the used marker rather than logging events, so what has to reach
 * the backend is the *latest* state of each draft. Marking then unmarking while
 * offline must end as a single delete, not a mark followed by a delete, otherwise
 * a failed flush could leave the server holding a pick you had already undone.
 */
class PickOutbox(context: Context) {

    private val lock = Any()
    private val file = File(context.filesDir, "pick_outbox.json")

    /** One queued change: either the pick to store, or an id to remove. */
    data class Op(val clientPickId: String, val pick: DraftPick?) {
        val isDelete: Boolean get() = pick == null
    }

    fun put(pick: DraftPick) = enqueue(Op(pick.clientPickId, pick))

    fun delete(clientPickId: String) = enqueue(Op(clientPickId, null))

    fun pending(): List<Op> = synchronized(lock) { read().mapNotNull { it.toOp() } }

    val size: Int get() = synchronized(lock) { read().size }

    fun remove(clientPickId: String) {
        synchronized(lock) {
            write(read().filter { it.optString("client_pick_id") != clientPickId })
        }
    }

    private fun enqueue(op: Op) {
        synchronized(lock) {
            // Drop any earlier change for the same draft: only the latest counts.
            val kept = read().filterTo(mutableListOf()) {
                it.optString("client_pick_id") != op.clientPickId
            }
            kept.add(op.toJson())
            write(kept)
        }
    }

    private fun read(): MutableList<JSONObject> {
        if (!file.exists()) return mutableListOf()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapTo(mutableListOf()) { array.getJSONObject(it) }
        }.onFailure { Log.w(TAG, "Failed to read pick outbox", it) }
            .getOrDefault(mutableListOf())
    }

    private fun write(ops: List<JSONObject>) {
        runCatching {
            file.writeText(JSONArray().apply { ops.forEach { put(it) } }.toString())
        }.onFailure { Log.w(TAG, "Failed to write pick outbox", it) }
    }

    private fun Op.toJson(): JSONObject = JSONObject().apply {
        put("client_pick_id", clientPickId)
        put("delete", isDelete)
        pick?.let {
            put("source", it.source)
            put("variant", it.variant)
            put("thought", it.thought)
            put("text", it.text)
            put("post_author", it.postAuthor)
            put("post_text", it.postText)
            put("picked_at_millis", it.pickedAtMillis)
        }
    }

    private fun JSONObject.toOp(): Op? = runCatching {
        val id = optString("client_pick_id").takeIf { it.isNotEmpty() } ?: return null
        if (optBoolean("delete")) {
            Op(id, null)
        } else {
            Op(
                clientPickId = id,
                pick = DraftPick(
                    clientPickId = id,
                    source = optString("source"),
                    variant = optString("variant"),
                    thought = optString("thought"),
                    text = optString("text"),
                    postAuthor = optString("post_author"),
                    postText = optString("post_text"),
                    pickedAtMillis = optLong("picked_at_millis"),
                ),
            )
        }
    }.getOrNull()

    private companion object {
        const val TAG = "PickOutbox"
    }
}
