package com.yash.feedrunner.data

import android.util.Log
import com.yash.feedrunner.BuildConfig
import com.yash.feedrunner.ui.ChatMessage
import com.yash.feedrunner.ui.ChatRole
import com.yash.feedrunner.ui.IdeaSeed
import com.yash.feedrunner.ui.PostIdea
import com.yash.feedrunner.ui.DayCount
import com.yash.feedrunner.ui.SeedIdea
import com.yash.feedrunner.ui.Streak
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

/**
 * A backend request that failed, with a message fit to show on screen.
 *
 * [reachable] separates "the server said no" from "nothing answered", which decides
 * whether trying another address could help.
 */
class IdeaBankException(
    message: String,
    val reachable: Boolean = false,
) : Exception(message)

/**
 * Thin HTTP client for the Idea Bank backend.
 *
 * Uses HttpURLConnection rather than adding a networking dependency: the surface
 * is six small endpoints. Every call blocks, so callers run them off the main
 * thread.
 */
class IdeaBankApi(
    private val config: BackendConfig,
    /** Optional so tests and one-off calls need not carry storage. */
    private val cache: SeedCache? = null,
) {

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
        cache?.save(status, array.toString())
        return array.toSeeds()
    }

    /** The last answer for this filter, for when nothing answers now. */
    fun cachedSeeds(status: SeedStatus? = null): List<StoredSeed> {
        val raw = cache?.load(status) ?: return emptyList()
        return runCatching { JSONArray(raw).toSeeds() }.getOrDefault(emptyList())
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

    fun streak(): Streak {
        val body = request("GET", "/streak", null)
        val array = body.optJSONArray("days")
        return Streak(
            today = body.optInt("today"),
            current = body.optInt("current_streak"),
            longest = body.optInt("longest_streak"),
            total = body.optInt("total"),
            days = (0 until (array?.length() ?: 0)).mapNotNull { i ->
                val item = array?.optJSONObject(i) ?: return@mapNotNull null
                DayCount(item.optString("date"), item.optInt("count"))
            },
        )
    }

    /** Generates from one seed. Empty instruction means "just generate". */
    fun generateForSeed(remoteId: String, instruction: String): StoredSeed {
        val payload = JSONObject().put("instruction", instruction)
        val body = request("POST", "/seeds/$remoteId/ideas", payload)
        return body.optJSONObject("seed")?.toRemoteSeed()
            ?: throw IdeaBankException("Server did not return the thread")
    }

    /** Removes one generated post. The server keeps a tally of what was cleared. */
    fun deleteIdea(remoteId: String, ideaId: String): StoredSeed {
        val body = request("DELETE", "/seeds/$remoteId/ideas/$ideaId", null)
        return body.optJSONObject("seed")?.toRemoteSeed()
            ?: throw IdeaBankException("Server did not return the thread")
    }

    /** Idempotent: re-copying the same draft updates the one row. */
    fun savePick(pick: DraftPick) {
        val payload = JSONObject().apply {
            put("client_pick_id", pick.clientPickId)
            put("source", pick.source)
            put("variant", pick.variant)
            put("thought", pick.thought)
            put("text", pick.text)
            put("post_author", pick.postAuthor)
            put("post_text", pick.postText)
            put("picked_at_millis", pick.pickedAtMillis)
        }
        request("PUT", "/picks", payload)
    }

    /** Unmarking "used". Succeeds even if the server never had the pick. */
    fun deletePick(clientPickId: String) {
        request("DELETE", "/picks/$clientPickId", null)
    }

    /** Ideas plus the themes the model saw recurring across the bank. */
    data class Ideation(val ideas: List<PostIdea>, val emergingLanes: List<String>)

    fun generateIdeas(remoteIds: List<String>, steer: String): Ideation {
        val payload = JSONObject().apply {
            put("seed_ids", JSONArray().apply { remoteIds.forEach { put(it) } })
            put("steer", steer)
        }
        val body = request("POST", "/ideas/generate", payload)
        val array = body.optJSONArray("ideas")
            ?: throw IdeaBankException("Server returned no ideas")

        val ideas = (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val text = item.optString("post_text").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val refs = item.optJSONArray("seed_refs")
            PostIdea(
                postText = text,
                play = item.optString("play"),
                register = item.optString("register"),
                lane = item.optString("lane"),
                thought = item.optString("thought"),
                whyNow = item.optString("why_now"),
                seedRefs = (0 until (refs?.length() ?: 0)).mapNotNull { i ->
                    refs?.optString(i)?.takeIf { it.isNotEmpty() }
                },
            )
        }
        val lanes = body.optJSONArray("emerging_lanes")
        return Ideation(
            ideas = ideas,
            emergingLanes = (0 until (lanes?.length() ?: 0)).mapNotNull { i ->
                lanes?.optString(i)?.takeIf { it.isNotEmpty() }
            },
        )
    }

    // --- transport ---------------------------------------------------------

    /**
     * Tries the configured address, then the USB tunnel.
     *
     * The backend lives on a laptop whose LAN address changes with the network, and
     * the two are not always on the same subnet. Falling back to the loopback
     * address means `adb reverse tcp:8080 tcp:8080` keeps everything working over
     * the cable without editing the address by hand.
     */
    private fun request(method: String, path: String, payload: JSONObject?): JSONObject {
        val base = config.baseUrl
        if (base.isEmpty()) throw IdeaBankException("Set the backend address first")

        // Whichever answered last goes first; the other is the fallback.
        val candidates = buildList {
            val preferred = config.lastWorking.takeIf { it.isNotEmpty() }
            if (preferred != null) add(preferred)
            add(base)
            // The cable only helps when the backend is a laptop. Pointed at a
            // deployed host it is a guaranteed extra failure on every call that
            // the real address has already failed.
            if (!base.startsWith("https://")) add(USB_TUNNEL)
        }.distinct()

        var failure: IdeaBankException? = null
        for (candidate in candidates) {
            val outcome = runCatching { requestAt(candidate, method, path, payload) }
            outcome.getOrNull()?.let { body ->
                if (config.lastWorking != candidate) config.lastWorking = candidate
                return body
            }
            val error = outcome.exceptionOrNull()
            if (error is IdeaBankException) {
                // A server that answered with an error is reachable, so stop here
                // rather than retrying the same request against another address.
                if (error.reachable) throw error
                failure = error
            } else if (error != null) {
                throw error
            }
        }
        throw failure ?: IdeaBankException("Cannot reach the backend. Is it running?")
    }

    private fun requestAt(
        base: String,
        method: String,
        path: String,
        payload: JSONObject?,
    ): JSONObject {

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
            // The deployed backend rejects everything but /healthz without this.
            // Empty when the backend is a laptop with no token set, and sending
            // an empty bearer would just be a header saying nothing.
            if (BuildConfig.IDEA_BANK_TOKEN.isNotBlank()) {
                connection.setRequestProperty(
                    "Authorization",
                    "Bearer ${BuildConfig.IDEA_BANK_TOKEN}",
                )
            }

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
                // 401 is a mismatched token, which is a setting to fix rather than
                // something to retry, so it says so instead of echoing "unauthorized".
                val message = when {
                    code == 401 -> "Backend rejected the token. Check ideaBank.token."
                    else -> body.optString("error").takeIf { it.isNotBlank() }
                        ?: "Server returned $code"
                }
                throw IdeaBankException(message, reachable = true)
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

        /** Reachable whenever `adb reverse tcp:8080 tcp:8080` is set up. */
        const val USB_TUNNEL = "http://127.0.0.1:8080"
        const val CONNECT_TIMEOUT_MS = 4000
        const val READ_TIMEOUT_MS = 120_000
    }
}

/** Parses a seed document as the server returns it, including its assigned id. */
private fun JSONArray.toSeeds(): List<StoredSeed> =
    (0 until length()).mapNotNull { optJSONObject(it)?.toRemoteSeed() }

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
        ideas = optJSONArray("ideas").toSeedIdeas(),
        lanes = optJSONArray("lanes").toStrings(),
        rounds = optInt("rounds"),
    )
}.getOrNull()

private fun JSONArray?.toSeedIdeas(): List<SeedIdea> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i ->
        val item = optJSONObject(i) ?: return@mapNotNull null
        val text = item.optString("post_text").takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        SeedIdea(
            id = item.optString("id"),
            postText = text,
            play = item.optString("play"),
            register = item.optString("register"),
            lane = item.optString("lane"),
            thought = item.optString("thought"),
            whyNow = item.optString("why_now"),
            round = item.optInt("round"),
            atMillis = parseTimestamp(item.optString("at")),
        )
    }
}

private fun JSONArray?.toStrings(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotEmpty() } }
}

private fun JSONArray?.toChatMessages(): List<ChatMessage> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i ->
        val item = optJSONObject(i) ?: return@mapNotNull null
        val role = if (item.optString("role") == "assistant") {
            ChatRole.ASSISTANT
        } else {
            ChatRole.USER
        }
        ChatMessage(role, item.optString("text"), parseTimestamp(item.optString("at")))
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
