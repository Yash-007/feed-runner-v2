package com.yash.feedrunner.data

import android.util.Log
import com.yash.feedrunner.ui.Angle
import com.yash.feedrunner.ui.Platform
import com.yash.feedrunner.ui.CaptureContext
import com.yash.feedrunner.ui.ChatMessage
import com.yash.feedrunner.ui.ChatRole
import com.yash.feedrunner.ui.Draft
import com.yash.feedrunner.ui.IdeaSeed
import com.yash.feedrunner.ui.PostContext
import com.yash.feedrunner.ui.PostDraft
import com.yash.feedrunner.ui.PostStyle
import com.yash.feedrunner.ui.Refinement
import com.yash.feedrunner.ui.RepostMode
import com.yash.feedrunner.ui.TextReading
import org.json.JSONArray
import org.json.JSONObject

/** Everything one reply generation returns. */
data class Analysis(
    val postContext: PostContext,
    val drafts: List<Draft>,
    /** Null when the model judged the post not worth banking an idea from. */
    val ideaSeed: IdeaSeed? = null,
)

/** Everything one post or quote generation returns. */
data class RepostAnalysis(
    val capture: CaptureContext,
    val reading: TextReading,
    val drafts: List<PostDraft>,
    val ideaSeed: IdeaSeed? = null,
)

/**
 * Drafting, done by the backend.
 *
 * This replaces the Anthropic SDK that used to run on the phone. The prompts, the
 * tool schemas and the key all live on the server now, so this file is only the
 * shapes going over the wire.
 *
 * The trade is that drafting needs the backend, where before it only needed the
 * network. Nothing here retries or degrades: a failure is shown, because a reply
 * you cannot see is not worth pretending about.
 *
 * Blocking, like the client it replaces. Callers already run it off the main
 * thread.
 */
class CopilotApi(private val transport: IdeaBankApi) {

    /**
     * One drafting call, timed.
     *
     * Logged because drafting moved off the device: the round trip now includes an
     * upload to our own server before the model is even reached, and this is the
     * only place that cost is visible. Payload size is in there because on the
     * image calls it is the part that varies with the network.
     */
    private fun call(path: String, payload: JSONObject): JSONObject {
        val started = System.currentTimeMillis()
        val bytes = payload.toString().length
        try {
            return transport.copilot(path, payload)
        } finally {
            val took = System.currentTimeMillis() - started
            Log.i(TAG, "$path ${took}ms sent=${bytes / 1024}kb")
        }
    }

    fun analyze(
        imageSegments: List<String>,
        extraVoiceRules: String,
        platform: Platform,
    ): Analysis {
        val payload = JSONObject()
            .put("platform", platform.wire)
            .put("images", JSONArray(imageSegments))
            .put("voice_rules", extraVoiceRules)
        return parseAnalysis(call("/copilot/replies", payload))
    }

    fun repliesInAngle(
        angle: Angle,
        postContext: PostContext,
        existing: List<Draft>,
        extraVoiceRules: String,
        platform: Platform,
    ): List<Draft> {
        val payload = JSONObject()
            .put("platform", platform.wire)
            .put("angle", angle.name)
            .put("post_context", postContext.toJson())
            .put("existing", JSONArray(existing.map { it.toJson() }))
            .put("voice_rules", extraVoiceRules)

        val body = call("/copilot/replies/angle", payload)
        // The angle was fixed by the request, so it is not read back.
        return readDrafts(body.optJSONArray("drafts")).map { it.copy(angle = angle) }
    }

    fun refine(
        draft: Draft,
        refinement: Refinement,
        postContext: PostContext,
        extraVoiceRules: String,
        platform: Platform,
    ): String {
        val payload = JSONObject()
            .put("platform", platform.wire)
            .put("draft", draft.toJson())
            // The chip labels stay a UI concern; the server takes the wording.
            .put("instruction", refinement.instruction)
            .put("post_context", postContext.toJson())
            .put("voice_rules", extraVoiceRules)

        return call("/copilot/replies/refine", payload).requireText()
    }

    fun chat(
        postContext: PostContext,
        drafts: List<Draft>,
        history: List<ChatMessage>,
        userMessage: String,
        extraVoiceRules: String,
        platform: Platform,
    ): String {
        val payload = JSONObject()
            .put("platform", platform.wire)
            .put("post_context", postContext.toJson())
            .put("drafts", JSONArray(drafts.map { it.toJson() }))
            .put("history", history.toJson())
            .put("message", userMessage)
            .put("voice_rules", extraVoiceRules)

        return call("/copilot/replies/chat", payload).requireText()
    }

    fun suggestPosts(
        mode: RepostMode,
        imageSegments: List<String>,
        userText: String,
        extraVoiceRules: String,
        platform: Platform,
    ): RepostAnalysis {
        val payload = JSONObject()
            .put("platform", platform.wire)
            .put("mode", mode.wire)
            .put("images", JSONArray(imageSegments))
            .put("user_text", userText)
            .put("voice_rules", extraVoiceRules)

        return parseRepost(call("/copilot/posts", payload))
    }

    fun chatPosts(
        mode: RepostMode,
        capture: CaptureContext,
        drafts: List<PostDraft>,
        history: List<ChatMessage>,
        userMessage: String,
        extraVoiceRules: String,
        platform: Platform,
    ): String {
        val payload = JSONObject()
            .put("platform", platform.wire)
            .put("mode", mode.wire)
            .put("capture_context", capture.toJson())
            .put("drafts", JSONArray(drafts.map { it.toJson() }))
            .put("history", history.toJson())
            .put("message", userMessage)
            .put("voice_rules", extraVoiceRules)

        return call("/copilot/posts/chat", payload).requireText()
    }

    private companion object {
        const val TAG = "Copilot"
    }

    private fun JSONObject.requireText(): String =
        optString("text").trim().ifEmpty {
            throw IdeaBankException("Nothing came back. Tap retry.", reachable = true)
        }

    private fun parseAnalysis(body: JSONObject): Analysis {
        val drafts = readDrafts(body.optJSONArray("drafts"))
        if (drafts.isEmpty()) {
            throw IdeaBankException("No drafts came back. Try again.", reachable = true)
        }
        return Analysis(
            postContext = readPostContext(body.optJSONObject("post_context")),
            drafts = drafts,
            ideaSeed = readSeed(body.optJSONObject("idea_seed")),
        )
    }

    private fun parseRepost(body: JSONObject): RepostAnalysis {
        val array = body.optJSONArray("drafts")
        val drafts = (0 until (array?.length() ?: 0)).mapNotNull { index ->
            val item = array?.optJSONObject(index) ?: return@mapNotNull null
            val style = runCatching {
                PostStyle.valueOf(item.optString("style").trim())
            }.getOrNull() ?: return@mapNotNull null
            val text = item.optString("text").trim().ifEmpty { return@mapNotNull null }
            PostDraft(
                id = index,
                style = style,
                thought = item.optString("thought"),
                text = text,
            )
        }
        if (drafts.isEmpty()) {
            throw IdeaBankException("No drafts came back. Try again.", reachable = true)
        }

        val capture = body.optJSONObject("capture_context")
        val readingWire = body.optString("user_text_read_as")
        return RepostAnalysis(
            capture = CaptureContext(
                contentType = capture?.optString("content_type").orEmpty(),
                summary = capture?.optString("summary").orEmpty(),
                quotedAuthor = capture?.optString("quoted_author")?.takeIf { it.isNotBlank() },
                quotedText = capture?.optString("quoted_text")?.takeIf { it.isNotBlank() },
            ),
            reading = TextReading.entries.firstOrNull { it.wire == readingWire } ?: TextReading.NONE,
            drafts = drafts,
            ideaSeed = readSeed(body.optJSONObject("idea_seed")),
        )
    }

    private fun readDrafts(array: JSONArray?): List<Draft> =
        (0 until (array?.length() ?: 0)).mapNotNull { index ->
            val item = array?.optJSONObject(index) ?: return@mapNotNull null
            val angle = runCatching {
                Angle.valueOf(item.optString("angle").trim())
            }.getOrNull() ?: return@mapNotNull null
            val text = item.optString("text").trim().ifEmpty { return@mapNotNull null }
            Draft(
                id = index,
                angle = angle,
                thought = item.optString("thought"),
                text = text,
            )
        }

    private fun readPostContext(json: JSONObject?) = PostContext(
        author = json?.optString("author").orEmpty().trim(),
        authorType = json?.optString("author_type").orEmpty(),
        postText = json?.optString("post_text").orEmpty().trim(),
        language = json?.optString("post_language").orEmpty(),
        register = json?.optString("post_register").orEmpty(),
    )

    /** Absent is the normal case: the post was not worth banking anything from. */
    private fun readSeed(json: JSONObject?): IdeaSeed? {
        if (json == null) return null
        val tags = json.optJSONArray("theme_tags")
        val seed = IdeaSeed(
            themeTags = (0 until (tags?.length() ?: 0)).mapNotNull {
                tags?.optString(it)?.trim()?.takeIf(String::isNotEmpty)
            },
            tension = json.optString("tension").trim(),
            angleHint = json.optString("your_angle_hint").trim(),
            shelfLife = json.optString("shelf_life").trim(),
        )
        return seed.takeUnless { it.isEmpty }
    }

    private fun PostContext.toJson() = JSONObject()
        .put("author", author)
        .put("author_type", authorType)
        .put("post_text", postText)
        .put("post_language", language)
        .put("post_register", register)

    private fun Draft.toJson() = JSONObject()
        .put("angle", angle.name)
        .put("thought", thought)
        .put("text", text)

    private fun PostDraft.toJson() = JSONObject()
        .put("style", style.name)
        .put("thought", thought)
        .put("text", text)

    private fun CaptureContext.toJson() = JSONObject()
        .put("content_type", contentType)
        .put("summary", summary)
        .put("quoted_author", quotedAuthor.orEmpty())
        .put("quoted_text", quotedText.orEmpty())

    private fun List<ChatMessage>.toJson() = JSONArray(
        map { message ->
            JSONObject()
                .put("role", if (message.role == ChatRole.USER) "user" else "assistant")
                .put("text", message.text)
        },
    )
}
