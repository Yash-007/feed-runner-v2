package com.yash.feedrunner.data

import android.content.Context
import android.util.Log
import com.yash.feedrunner.ui.CaptureContext
import com.yash.feedrunner.ui.ChatMessage
import com.yash.feedrunner.ui.ChatRole
import com.yash.feedrunner.ui.PostDraft
import com.yash.feedrunner.ui.PostStyle
import com.yash.feedrunner.ui.RepostMode
import com.yash.feedrunner.ui.RepostResult
import com.yash.feedrunner.ui.TextReading
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Keeps the most recent post or quote generation on disk, with its chat.
 *
 * Only the latest is kept: unlike replies, which you fire at many posts while
 * scrolling, composing is something you do once and then act on. What this buys
 * is that the drafts and the conversation about them survive the process being
 * killed, so reopening Repost is never a wasted call.
 */
class RepostStore(context: Context) {

    private val lock = Any()
    private val jsonFile = File(context.filesDir, "repost_result.json")

    fun save(result: RepostResult) {
        synchronized(lock) {
            runCatching { jsonFile.writeText(result.toJson().toString()) }
                .onFailure { Log.w(TAG, "Failed to save repost result", it) }
        }
    }

    /** Replaces the chat of the stored result, if it is still the same one. */
    fun updateChat(savedAtMillis: Long, chat: List<ChatMessage>) {
        synchronized(lock) {
            val stored = read() ?: return
            if (stored.optLong("savedAt") != savedAtMillis) return
            stored.put("chat", chat.toChatJson())
            runCatching { jsonFile.writeText(stored.toString()) }
                .onFailure { Log.w(TAG, "Failed to save repost chat", it) }
        }
    }

    fun load(): RepostResult? = synchronized(lock) { read()?.toResult() }

    private fun read(): JSONObject? {
        if (!jsonFile.exists()) return null
        return runCatching { JSONObject(jsonFile.readText()) }
            .onFailure { Log.w(TAG, "Failed to read repost result", it) }
            .getOrNull()
    }

    private fun RepostResult.toJson() = JSONObject().apply {
        put("savedAt", savedAtMillis)
        put("mode", mode.name)
        put("reading", reading.name)
        put("capturePath", capturePath ?: JSONObject.NULL)
        put(
            "capture",
            JSONObject().apply {
                put("contentType", capture.contentType)
                put("summary", capture.summary)
                put("quotedAuthor", capture.quotedAuthor ?: JSONObject.NULL)
                put("quotedText", capture.quotedText ?: JSONObject.NULL)
            },
        )
        put(
            "drafts",
            JSONArray().apply {
                drafts.forEach { draft ->
                    put(
                        JSONObject().apply {
                            put("id", draft.id)
                            put("style", draft.style.name)
                            put("thought", draft.thought)
                            put("text", draft.text)
                            put("used", draft.used)
                        },
                    )
                }
            },
        )
        put("chat", chat.toChatJson())
    }

    private fun JSONObject.toResult(): RepostResult? = runCatching {
        val draftsArray = getJSONArray("drafts")
        val drafts = (0 until draftsArray.length()).mapNotNull { i ->
            val item = draftsArray.getJSONObject(i)
            val style = runCatching { PostStyle.valueOf(item.getString("style")) }.getOrNull()
                ?: return@mapNotNull null
            PostDraft(
                id = item.getInt("id"),
                style = style,
                thought = item.optString("thought"),
                text = item.getString("text"),
                used = item.optBoolean("used"),
            )
        }
        if (drafts.isEmpty()) return@runCatching null

        val captureJson = getJSONObject("capture")
        RepostResult(
            mode = RepostMode.valueOf(getString("mode")),
            capture = CaptureContext(
                contentType = captureJson.optString("contentType"),
                summary = captureJson.optString("summary"),
                quotedAuthor = captureJson.nullableString("quotedAuthor"),
                quotedText = captureJson.nullableString("quotedText"),
            ),
            reading = runCatching { TextReading.valueOf(getString("reading")) }
                .getOrDefault(TextReading.NONE),
            drafts = drafts,
            // A capture whose file is gone would render as a broken preview.
            capturePath = nullableString("capturePath")?.takeIf { File(it).exists() },
            savedAtMillis = getLong("savedAt"),
            chat = optJSONArray("chat").toChat(),
        )
    }.onFailure { Log.w(TAG, "Failed to parse repost result", it) }.getOrNull()

    private fun JSONObject.nullableString(key: String): String? =
        optString(key).takeIf { it.isNotEmpty() && it != "null" }

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

    private companion object {
        const val TAG = "RepostStore"
    }
}
