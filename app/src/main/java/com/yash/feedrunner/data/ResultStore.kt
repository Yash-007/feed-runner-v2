package com.yash.feedrunner.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.yash.feedrunner.ui.Angle
import com.yash.feedrunner.ui.ChatMessage
import com.yash.feedrunner.ui.ChatRole
import com.yash.feedrunner.ui.Draft
import com.yash.feedrunner.ui.PostContext
import com.yash.feedrunner.ui.StoredResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Keeps the last [MAX_RESULTS] analyses so they can be reopened without
 * re-capturing or spending another API call. Newest first; the oldest is
 * dropped (along with its thumbnail) when the list overflows.
 *
 * Uses org.json rather than a serialization library — the shape is small and
 * this keeps the dependency list short.
 */
class ResultStore(private val context: Context) {

    /**
     * Analyses can now finish in parallel, and every mutation is a
     * read-modify-write of one JSON file. Without this lock two jobs completing
     * together would each write a copy of the list that omits the other.
     */
    private val lock = Any()

    private val jsonFile = File(context.filesDir, "results.json")
    private val thumbDir = File(context.filesDir, "thumbs")
    private val captureDir = File(context.filesDir, "captures")

    fun exists(): Boolean = loadAll().isNotEmpty()

    /** What a save produced, so the caller can show the result straight away. */
    data class Saved(val id: Long, val thumbnailPath: String?, val capturePath: String?)

    /** Prepends a new result and prunes to [MAX_RESULTS]. */
    fun save(postContext: PostContext, drafts: List<Draft>, screenshot: Bitmap?): Saved {
        synchronized(lock) {
        val savedAt = nextId()
        val thumbnailPath = screenshot?.let { writeThumbnail(it, savedAt) }
        val capturePath = screenshot?.let { writeCapture(it, savedAt) }

        val entry = JSONObject().apply {
            put("savedAt", savedAt)
            put("thumbnailPath", thumbnailPath ?: JSONObject.NULL)
            put("capturePath", capturePath ?: JSONObject.NULL)
            put("postContext", postContext.toJson())
            put("drafts", drafts.toJson())
        }

        val kept = JSONArray().apply {
            put(entry)
            readArray().take(MAX_RESULTS - 1).forEach { put(it) }
        }
        write(kept)
        pruneOrphanedFiles(kept)
        return Saved(savedAt, thumbnailPath, capturePath)
        }
    }

    /** Replaces the chat of one stored result, leaving everything else intact. */
    fun updateChat(resultId: Long, chat: List<ChatMessage>) {
        synchronized(lock) {
        val entries = readArray()
        val target = entries.firstOrNull { it.optLong("savedAt") == resultId } ?: return
        target.put("chat", chat.toChatJson())
        write(JSONArray().apply { entries.forEach { put(it) } })
        }
    }

    /** Rewrites the drafts of one stored result, leaving everything else intact. */
    fun updateDrafts(resultId: Long, drafts: List<Draft>) {
        synchronized(lock) {
        val entries = readArray()
        val target = entries.firstOrNull { it.optLong("savedAt") == resultId } ?: return
        target.put("drafts", drafts.toJson())
        write(JSONArray().apply { entries.forEach { put(it) } })
        }
    }

    /** Most recent result, or null if nothing is stored. */
    fun load(): StoredResult? = loadAll().firstOrNull()

    fun load(resultId: Long): StoredResult? = loadAll().firstOrNull {
        it.savedAtMillis == resultId
    }

    /** Newest first. */
    fun loadAll(): List<StoredResult> =
        synchronized(lock) { readArray().mapNotNull { it.toStoredResult() } }

    // --- persistence -------------------------------------------------------

    private fun readArray(): List<JSONObject> {
        if (!jsonFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(jsonFile.readText())
            (0 until array.length()).map { array.getJSONObject(it) }
        }.onFailure { Log.w(TAG, "Failed to read results", it) }.getOrDefault(emptyList())
    }

    private fun write(array: JSONArray) {
        runCatching { jsonFile.writeText(array.toString()) }
            .onFailure { Log.w(TAG, "Failed to write results", it) }
    }

    /**
     * Ids double as timestamps and as the key [updateDrafts] targets, so they
     * must be unique. Two captures can't realistically land in the same
     * millisecond, but nudge forward rather than risk a collision.
     */
    private fun nextId(): Long {
        val now = System.currentTimeMillis()
        val newest = readArray().firstOrNull()?.optLong("savedAt") ?: 0L
        return if (now <= newest) newest + 1 else now
    }

    private fun JSONObject.toStoredResult(): StoredResult? = runCatching {
        val draftsArray = getJSONArray("drafts")
        val drafts = (0 until draftsArray.length()).mapNotNull { i ->
            val item = draftsArray.getJSONObject(i)
            val angle = runCatching { Angle.valueOf(item.getString("angle")) }.getOrNull()
                ?: return@mapNotNull null
            Draft(
                id = item.getInt("id"),
                angle = angle,
                thought = item.optString("thought"),
                text = item.getString("text"),
                used = item.optBoolean("used"),
            )
        }
        if (drafts.isEmpty()) return@runCatching null

        StoredResult(
            postContext = getJSONObject("postContext").toPostContext(),
            drafts = drafts,
            thumbnailPath = existingPath("thumbnailPath"),
            capturePath = existingPath("capturePath"),
            savedAtMillis = getLong("savedAt"),
            chat = optJSONArray("chat").toChat(),
        )
    }.getOrNull()

    private fun JSONObject.existingPath(key: String): String? =
        optString(key).takeIf { it.isNotEmpty() && it != "null" && File(it).exists() }

    private fun PostContext.toJson() = JSONObject().apply {
        put("author", author)
        put("authorType", authorType)
        put("postText", postText)
        put("language", language)
        put("register", register)
    }

    private fun JSONObject.toPostContext() = PostContext(
        author = optString("author"),
        authorType = optString("authorType"),
        postText = optString("postText"),
        language = optString("language"),
        register = optString("register"),
    )

    private fun List<ChatMessage>.toChatJson() = JSONArray().apply {
        this@toChatJson.forEach { message ->
            put(
                JSONObject().apply {
                    put("role", message.role.name)
                    put("text", message.text)
                },
            )
        }
    }

    private fun JSONArray?.toChat(): List<ChatMessage> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i ->
            val item = optJSONObject(i) ?: return@mapNotNull null
            val role = runCatching { ChatRole.valueOf(item.getString("role")) }.getOrNull()
                ?: return@mapNotNull null
            ChatMessage(role, item.optString("text"))
        }
    }

    private fun List<Draft>.toJson() = JSONArray().apply {
        this@toJson.forEach { draft ->
            put(
                JSONObject().apply {
                    put("id", draft.id)
                    put("angle", draft.angle.name)
                    put("thought", draft.thought)
                    put("text", draft.text)
                    put("used", draft.used)
                },
            )
        }
    }

    // --- thumbnails --------------------------------------------------------

    /** Downscales to a small preview so results can be told apart at a glance. */
    private fun writeThumbnail(source: Bitmap, savedAt: Long): String? = runCatching {
        thumbDir.mkdirs()
        val scale = THUMB_WIDTH_PX.toFloat() / source.width
        val targetHeight = (source.height * scale).toInt().coerceIn(1, THUMB_MAX_HEIGHT_PX)
        val thumb = Bitmap.createScaledBitmap(source, THUMB_WIDTH_PX, targetHeight, true)
        val file = File(thumbDir, "thumb_$savedAt.jpg")
        file.outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        thumb.recycle()
        file.absolutePath
    }.onFailure { Log.w(TAG, "Failed to write thumbnail", it) }.getOrNull()

    /** Stores the capture at full resolution so it can be inspected on tap. */
    private fun writeCapture(source: Bitmap, savedAt: Long): String? = runCatching {
        captureDir.mkdirs()
        val file = File(captureDir, "capture_$savedAt.jpg")
        file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        file.absolutePath
    }.onFailure { Log.w(TAG, "Failed to write capture", it) }.getOrNull()

    /**
     * Deletes thumbnails and captures no longer referenced by a stored result.
     * Without this, both directories would grow without bound.
     */
    private fun pruneOrphanedFiles(kept: JSONArray) {
        val referenced = (0 until kept.length()).flatMap { i ->
            val entry = kept.getJSONObject(i)
            listOf("thumbnailPath", "capturePath")
                .mapNotNull { entry.optString(it).takeIf(String::isNotEmpty) }
        }.toSet()
        runCatching {
            listOf(thumbDir, captureDir).forEach { dir ->
                dir.listFiles()?.forEach { file ->
                    if (file.absolutePath !in referenced) file.delete()
                }
            }
            // Clean up the single-result files used before history existed.
            File(context.filesDir, "last_thumb.jpg").delete()
            File(context.filesDir, "last_result.json").delete()
        }.onFailure { Log.w(TAG, "Prune failed", it) }
    }

    private companion object {
        const val TAG = "ResultStore"
        const val MAX_RESULTS = 5
        const val THUMB_WIDTH_PX = 220
        const val THUMB_MAX_HEIGHT_PX = 400
    }
}
