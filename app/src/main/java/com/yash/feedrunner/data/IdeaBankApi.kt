package com.yash.feedrunner.data

import android.util.Log
import com.yash.feedrunner.ui.ChatMessage
import com.yash.feedrunner.ui.ChatRole
import com.yash.feedrunner.ui.IdeaSeed
import com.yash.feedrunner.ui.PostIdea
import com.yash.feedrunner.ui.SeedSource
import com.yash.feedrunner.ui.SeedStatus
import com.yash.feedrunner.ui.StoredSeed
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** A backend request that failed, with a message fit to show on screen. */
class IdeaBankException(message: String) : Exception(message)

/**
 * Thin HTTP client for the Idea Bank backend.
 *
 * Uses HttpURLConnection rather than adding a networking dependency: the surface
 * is six small endpoints. Every call blocks, so callers run them off the main
 * thread.
 */
class IdeaBankApi(private val config: BackendConfig) {

    fun health(): Boolean = runCatching {
        val body = request("GET", "/healthz", null)
        body.optBoolean("ok")
    }.getOrDefault(false)

    /** Returns the seed as stored, so the caller learns its remote id. */
    fun createSeed(entry: StoredSeed): StoredSeed {
        val body = request("POST", "/seeds", entry.toJson())
        return body.optJSONObject("seed")?.toRemoteSeed()
            ?: throw IdeaBankException("Server did not return the seed")
    }

    fun listSeeds(status: SeedStatus? = null): List<StoredSeed> {
        val path = if (status == null) "/seeds" else "/seeds?status=${status.wire}"
        val array = request("GET", path, null).optJSONArray("seeds") ?: JSONArray()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.toRemoteSeed() }
    }

    fun setStatus(remoteId: String, status: SeedStatus): StoredSeed {
        val payload = JSONObject().put("status", status.wire)
        val body = request("PATCH", "/seeds/$remoteId", payload)
        return body.optJSONObject("seed")?.toRemoteSeed()
            ?: throw IdeaBankException("Server did not return the seed")
    }

    /** Returns the seed with both new turns already appended by the server. */
    fun chat(remoteId: String, message: String): StoredSeed {
        val payload = JSONObject().put("message", message)
        val body = request("POST", "/seeds/$remoteId/chat", payload)
        return body.optJSONObject("seed")?.toRemoteSeed()
            ?: throw IdeaBankException("Server did not return the conversation")
    }

    fun clearChat(remoteId: String): StoredSeed {
        val body = request("DELETE", "/seeds/$remoteId/chat", null)
        return body.optJSONObject("seed")?.toRemoteSeed()
            ?: throw IdeaBankException("Server did not return the seed")
    }

    fun deleteSeed(remoteId: String) {
        request("DELETE", "/seeds/$remoteId", null)
    }

    fun generateIdeas(remoteIds: List<String>, steer: String): List<PostIdea> {
        val payload = JSONObject().apply {
            put("seed_ids", JSONArray().apply { remoteIds.forEach { put(it) } })
            put("steer", steer)
        }
        val array = request("POST", "/ideas/generate", payload).optJSONArray("ideas")
            ?: throw IdeaBankException("Server returned no ideas")
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val hook = item.optString("hook").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            PostIdea(
                hook = hook,
                body = item.optString("body"),
                format = item.optString("format"),
                whyNow = item.optString("why_now"),
            )
        }
    }

    // --- transport ---------------------------------------------------------

    private fun request(method: String, path: String, payload: JSONObject?): JSONObject {
        val base = config.baseUrl
        if (base.isEmpty()) throw IdeaBankException("Set the backend address first")

        val connection = try {
            URL(base + path).openConnection() as HttpURLConnection
        } catch (error: Exception) {
            throw IdeaBankException("Bad backend address: ${error.message}")
        }

        return try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            // Generous: an ideation request waits on the model.
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")

            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(payload.toString().toByteArray()) }
            }

            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()

            val body = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (code !in 200..299) {
                val message = body.optString("error").takeIf { it.isNotBlank() }
                    ?: "Server returned $code"
                throw IdeaBankException(message)
            }
            body
        } catch (error: IOException) {
            Log.w(TAG, "$method $path failed", error)
            throw IdeaBankException("Cannot reach the backend. Is it running?")
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "IdeaBankApi"
        const val CONNECT_TIMEOUT_MS = 4000
        const val READ_TIMEOUT_MS = 120_000
    }
}

/** Parses a seed document as the server returns it, including its assigned id. */
private fun JSONObject.toRemoteSeed(): StoredSeed? = runCatching {
    val tagsArray = optJSONArray("theme_tags")
    val tags = (0 until (tagsArray?.length() ?: 0)).mapNotNull { i ->
        tagsArray?.optString(i)?.takeIf { it.isNotEmpty() }
    }
    StoredSeed(
        remoteId = optString("id").takeIf { it.isNotBlank() },
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
        createdAtMillis = parseTimestamp(optString("created_at")),
        chat = optJSONArray("chat").toChatMessages(),
    )
}.getOrNull()

private fun JSONArray?.toChatMessages(): List<ChatMessage> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i ->
        val item = optJSONObject(i) ?: return@mapNotNull null
        val role = if (item.optString("role") == "assistant") {
            ChatRole.ASSISTANT
        } else {
            ChatRole.USER
        }
        ChatMessage(role, item.optString("text"))
    }
}

/**
 * Mongo timestamps come back as RFC 3339. Parsed here rather than shipped as
 * epoch millis so the stored documents stay readable in mongosh.
 */
private fun parseTimestamp(raw: String): Long {
    if (raw.isBlank()) return 0L
    for (pattern in TIMESTAMP_PATTERNS) {
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(raw)?.time
        }.getOrNull()
        if (parsed != null) return parsed
    }
    return 0L
}

private val TIMESTAMP_PATTERNS = listOf(
    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
    "yyyy-MM-dd'T'HH:mm:ssXXX",
)
