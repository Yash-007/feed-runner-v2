package com.yash.feedrunner.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.yash.feedrunner.ui.Angle
import com.yash.feedrunner.ui.Draft
import com.yash.feedrunner.ui.StoredResult
import com.yash.feedrunner.ui.Verdict
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists the most recent analysis so "Last result" can reopen it without
 * re-capturing or spending another API call. Exactly one result is kept;
 * each new analysis overwrites it.
 *
 * Uses org.json rather than a serialization library — the shape is small and
 * this keeps the dependency list short.
 */
class ResultStore(context: Context) {

    private val jsonFile = File(context.filesDir, "last_result.json")
    private val thumbFile = File(context.filesDir, "last_thumb.jpg")

    fun exists(): Boolean = jsonFile.exists()

    fun save(verdict: Verdict, drafts: List<Draft>, screenshot: Bitmap?) {
        val thumbnailPath = screenshot?.let { writeThumbnail(it) }
        val json = JSONObject().apply {
            put("savedAt", System.currentTimeMillis())
            put("worthReplying", verdict.worthReplying)
            put("reason", verdict.reason)
            put("thumbnailPath", thumbnailPath ?: JSONObject.NULL)
            put(
                "drafts",
                JSONArray().apply {
                    drafts.forEach { draft ->
                        put(
                            JSONObject().apply {
                                put("id", draft.id)
                                put("angle", draft.angle.name)
                                put("text", draft.text)
                            },
                        )
                    }
                },
            )
        }
        runCatching { jsonFile.writeText(json.toString()) }
            .onFailure { Log.w(TAG, "Failed to save result", it) }
    }

    /** Rewrites the drafts of the stored result, keeping its verdict/thumbnail/timestamp. */
    fun updateDrafts(drafts: List<Draft>) {
        val existing = load() ?: return
        val json = runCatching { JSONObject(jsonFile.readText()) }.getOrNull() ?: return
        json.put(
            "drafts",
            JSONArray().apply {
                drafts.forEach { draft ->
                    put(
                        JSONObject().apply {
                            put("id", draft.id)
                            put("angle", draft.angle.name)
                            put("text", draft.text)
                        },
                    )
                }
            },
        )
        // Keep the original capture time so the age label stays honest.
        json.put("savedAt", existing.savedAtMillis)
        runCatching { jsonFile.writeText(json.toString()) }
            .onFailure { Log.w(TAG, "Failed to update drafts", it) }
    }

    fun load(): StoredResult? {
        if (!jsonFile.exists()) return null
        return runCatching {
            val json = JSONObject(jsonFile.readText())
            val draftsArray = json.getJSONArray("drafts")
            val drafts = (0 until draftsArray.length()).map { i ->
                val item = draftsArray.getJSONObject(i)
                Draft(
                    id = item.getInt("id"),
                    angle = Angle.valueOf(item.getString("angle")),
                    text = item.getString("text"),
                )
            }
            StoredResult(
                verdict = Verdict(
                    worthReplying = json.getBoolean("worthReplying"),
                    reason = json.getString("reason"),
                ),
                drafts = drafts,
                thumbnailPath = json.optString("thumbnailPath").takeIf {
                    it.isNotEmpty() && it != "null" && File(it).exists()
                },
                savedAtMillis = json.getLong("savedAt"),
            )
        }.onFailure { Log.w(TAG, "Failed to load result", it) }.getOrNull()
    }

    /** Downscales to a small preview so the panel header can show which post this was. */
    private fun writeThumbnail(source: Bitmap): String? = runCatching {
        val targetWidth = THUMB_WIDTH_PX
        val scale = targetWidth.toFloat() / source.width
        val targetHeight = (source.height * scale).toInt()
            .coerceIn(1, THUMB_MAX_HEIGHT_PX)
        val thumb = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        thumbFile.outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        thumb.recycle()
        thumbFile.absolutePath
    }.onFailure { Log.w(TAG, "Failed to write thumbnail", it) }.getOrNull()

    private companion object {
        const val TAG = "ResultStore"
        const val THUMB_WIDTH_PX = 220
        const val THUMB_MAX_HEIGHT_PX = 400
    }
}
