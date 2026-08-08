package com.yash.feedrunner.api

import com.anthropic.client.AnthropicClient
import android.util.Log
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigDisabled
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolChoice
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.ToolChoiceNone
import com.anthropic.models.messages.ToolChoiceTool
import com.yash.feedrunner.ui.Angle
import com.yash.feedrunner.ui.ChatMessage
import com.yash.feedrunner.ui.ChatRole
import com.yash.feedrunner.ui.Draft
import com.yash.feedrunner.ui.PostContext
import com.yash.feedrunner.ui.Refinement

/** Everything one analysis returns. */
data class Analysis(
    val postContext: PostContext,
    val drafts: List<Draft>,
)

/**
 * Calls Claude for reply drafts and rewrites.
 *
 * Both calls are blocking — callers run them off the main thread.
 *
 * The analysis uses a forced tool call rather than a text response. The prompt
 * originally asked for bare JSON; a tool call gets the same structure with no
 * risk of markdown fences or a preamble, and no text parsing.
 */
class ClaudeClient(apiKey: String) {

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .build()

    fun analyze(imageSegments: List<String>, extraVoiceRules: String): Analysis {
        val content = buildList {
            imageSegments.forEach { base64 ->
                add(
                    ContentBlockParam.ofImage(
                        ImageBlockParam.builder()
                            .source(
                                Base64ImageSource.builder()
                                    .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                                    .data(base64)
                                    .build(),
                            )
                            .build(),
                    ),
                )
            }
            add(
                ContentBlockParam.ofText(
                    TextBlockParam.builder().text(ANALYZE_INSTRUCTION).build(),
                ),
            )
        }

        val params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(MAX_TOKENS)
            .thinking(ThinkingConfigDisabled.builder().build())
            .systemOfTextBlockParams(systemBlocks(extraVoiceRules))
            .addTool(draftsTool())
            .toolChoice(
                ToolChoice.ofTool(ToolChoiceTool.builder().name(TOOL_NAME).build()),
            )
            .addUserMessageOfBlockParams(content)
            .build()

        val response = client.messages().create(params)
        logUsage("analyze", response)
        requireNotRefused(response.stopReason().map { it.toString() }.orElse(""))

        val toolInput = response.content()
            .firstNotNullOfOrNull { block -> block.toolUse().orElse(null) }
            ?._input()
            ?: throw ClaudeException("Claude returned no drafts. Try again.")

        // convert() deserializes through Jackson. Do not use toString(): that
        // renders a Java map literal ({key=value}), which is not valid JSON.
        @Suppress("UNCHECKED_CAST")
        val fields = runCatching { toolInput.convert(Map::class.java) as Map<String, Any?> }
            .getOrNull()
            ?: throw ClaudeException("Could not read Claude's reply. Try again.")

        return parseAnalysis(fields)
    }

    fun refine(
        draft: Draft,
        refinement: Refinement,
        postContext: PostContext,
        extraVoiceRules: String,
    ): String {
        // Built by concatenation rather than a trimIndent block: draft text can
        // be multi-line, which would otherwise throw off the common-indent maths.
        val instruction = buildString {
            append("The post being replied to, by ").append(postContext.author)
            append(" (").append(postContext.authorType)
            append(", ").append(postContext.registerLabel)
            append(", ").append(postContext.language).append("):\n")
            append('"').append(postContext.postText).append("\"\n\n")
            append("My current draft (angle: ").append(draft.angle.label).append("):\n")
            append(draft.text).append("\n\n")
            append("Rewrite it ").append(refinement.instruction).append(".\n")
            append("Keep the same angle and the same core point. ")
            append("Same voice rules as always.\n\n")
            append("Output only the rewritten reply. Nothing else: no preamble, ")
            append("no quotes, no explanation, no alternatives.")
        }

        val params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(REFINE_MAX_TOKENS)
            .thinking(ThinkingConfigDisabled.builder().build())
            .systemOfTextBlockParams(systemBlocks(extraVoiceRules))
            // The tool is declared but disabled. Tools render ahead of the system
            // prompt in the cached prefix, so omitting them here would give this
            // request a different prefix from analyze() and force a fresh cache
            // write. tool_choice only invalidates the messages tier, so declaring
            // the same tool and forbidding its use lets this read that cache.
            .addTool(draftsTool())
            .toolChoice(ToolChoice.ofNone(ToolChoiceNone.builder().build()))
            .addUserMessage(instruction)
            .build()

        val response = client.messages().create(params)
        logUsage("refine", response)
        requireNotRefused(response.stopReason().map { it.toString() }.orElse(""))

        val text = response.content()
            .firstNotNullOfOrNull { block -> block.text().orElse(null) }
            ?.text()
            ?.trim()
            ?.trim('"')

        if (text.isNullOrBlank()) throw ClaudeException("Empty rewrite. Try again.")
        return scrubTells(text)
    }

    /**
     * Free-form conversation about one post: a different angle, a constraint the
     * drafts missed, a follow-up question.
     *
     * The post and the current drafts are folded into the first user turn so the
     * model knows what has already been suggested and does not repeat it. The
     * screenshot is not resent; the extracted post text stands in for it.
     */
    fun chat(
        postContext: PostContext,
        drafts: List<Draft>,
        history: List<ChatMessage>,
        userMessage: String,
        extraVoiceRules: String,
    ): String {
        val opening = buildString {
            append("We are talking about this X post by ").append(postContext.author)
            append(" (").append(postContext.authorType)
            append(", ").append(postContext.registerLabel)
            append(", ").append(postContext.language).append("):\n")
            append('"').append(postContext.postText).append("\"\n\n")
            append("Drafts already suggested, do not repeat these:\n")
            drafts.forEach { draft ->
                append("- [").append(draft.angle.label).append("] ").append(draft.text).append('\n')
            }
            append("\nI'll now ask for changes or new angles. Answer in the same voice ")
            append("and rules as always. When I ask for a reply, give the reply itself, ")
            append("ready to paste, with no preamble and no numbered list unless I ask ")
            append("for several. Keep any explanation to one short line.")
        }

        val messages = buildList {
            // The first turn carries the context; the rest is the real conversation.
            val firstUser = history.firstOrNull { it.role == ChatRole.USER }?.text
            add(
                MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .content(
                        if (firstUser != null) "$opening\n\n$firstUser" else "$opening\n\n$userMessage",
                    )
                    .build(),
            )

            if (firstUser != null) {
                var seenFirstUser = false
                history.forEach { message ->
                    if (!seenFirstUser && message.role == ChatRole.USER) {
                        seenFirstUser = true
                        return@forEach
                    }
                    add(
                        MessageParam.builder()
                            .role(
                                if (message.role == ChatRole.USER) {
                                    MessageParam.Role.USER
                                } else {
                                    MessageParam.Role.ASSISTANT
                                },
                            )
                            .content(message.text)
                            .build(),
                    )
                }
                add(
                    MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .content(userMessage)
                        .build(),
                )
            }
        }

        val params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(CHAT_MAX_TOKENS)
            .thinking(ThinkingConfigDisabled.builder().build())
            .systemOfTextBlockParams(systemBlocks(extraVoiceRules))
            // Same reason as refine(): keeps the cached prefix identical.
            .addTool(draftsTool())
            .toolChoice(ToolChoice.ofNone(ToolChoiceNone.builder().build()))
            .messages(messages)
            .build()

        val response = client.messages().create(params)
        logUsage("chat", response)
        requireNotRefused(response.stopReason().map { it.toString() }.orElse(""))

        val text = response.content()
            .firstNotNullOfOrNull { block -> block.text().orElse(null) }
            ?.text()
            ?.trim()

        if (text.isNullOrBlank()) throw ClaudeException("Empty reply. Try again.")
        return scrubTells(text)
    }

    /**
     * The long prompt is its own cached block so it isn't re-billed on every
     * call; the user's editable additions go in a second, uncached block after
     * it, so editing them doesn't invalidate the cached prefix.
     */
    private fun systemBlocks(extraVoiceRules: String): List<TextBlockParam> = buildList {
        add(
            TextBlockParam.builder()
                .text(REPLY_SYSTEM_PROMPT)
                .cacheControl(
                    CacheControlEphemeral.builder()
                        .ttl(CacheControlEphemeral.Ttl.TTL_1H)
                        .build(),
                )
                .build(),
        )
        extraVoiceRules.trim().takeIf { it.isNotEmpty() }?.let { extra ->
            add(
                TextBlockParam.builder()
                    .text("## ADDITIONAL RULES FROM YASH (these override the above)\n\n$extra")
                    .build(),
            )
        }
    }

    /**
     * Logs token usage so cache behaviour is observable. A healthy refinement
     * shows cacheRead in the thousands and input in the low hundreds; cacheWrite
     * repeatedly in the thousands means the prefix is not being reused.
     */
    private fun logUsage(call: String, response: Message) {
        val usage = response.usage()
        Log.i(
            TAG,
            "$call usage: input=${usage.inputTokens()} output=${usage.outputTokens()} " +
                "cacheWrite=${usage.cacheCreationInputTokens().orElse(0L)} " +
                "cacheRead=${usage.cacheReadInputTokens().orElse(0L)}",
        )
    }

    private fun requireNotRefused(stopReason: String) {
        if (stopReason == "refusal") {
            throw ClaudeException("Claude declined this one. Try a different post.")
        }
    }

    /**
     * The prompt bans em-dashes outright (they are a well-known AI tell on X),
     * but the model still emits them occasionally. Enforcing it here makes the
     * rule a guarantee rather than a hope. Spaced dashes become commas; tight
     * ones become commas without doubling the spacing.
     */
    private fun scrubTells(text: String): String = text
        .replace(" \u2014 ", ", ")
        .replace("\u2014 ", ", ")
        .replace(" \u2014", ",")
        .replace("\u2014", ",")
        .replace(Regex(",\\s*,"), ",")
        .trim()

    private fun parseAnalysis(fields: Map<String, Any?>): Analysis {
        val context = fields["post_context"] as? Map<*, *>
        val postContext = PostContext(
            author = (context?.get("author") as? String)?.trim().orEmpty(),
            authorType = (context?.get("author_type") as? String).orEmpty(),
            postText = (context?.get("post_text") as? String)?.trim().orEmpty(),
            language = (context?.get("post_language") as? String).orEmpty(),
            register = (context?.get("post_register") as? String).orEmpty(),
        )

        val rawDrafts = fields["drafts"] as? List<*> ?: emptyList<Any?>()
        val drafts = rawDrafts.mapIndexedNotNull { index, item ->
            val draft = item as? Map<*, *> ?: return@mapIndexedNotNull null
            val angle = (draft["angle"] as? String)
                ?.let { runCatching { Angle.valueOf(it.trim()) }.getOrNull() }
                ?: return@mapIndexedNotNull null
            val text = (draft["text"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapIndexedNotNull null
            Draft(
                id = index,
                angle = angle,
                thought = (draft["thought"] as? String)?.trim().orEmpty(),
                text = scrubTells(text),
            )
        }
        if (drafts.isEmpty()) throw ClaudeException("Claude returned no drafts. Try again.")

        return Analysis(postContext = postContext, drafts = drafts)
    }

    private fun draftsTool(): Tool {
        val postContextSchema = JsonValue.from(
            mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "author" to mapOf(
                        "type" to "string",
                        "description" to "The author's @handle, including the @.",
                    ),
                    "author_type" to mapOf(
                        "type" to "string",
                        "enum" to listOf("indian", "international", "unknown"),
                    ),
                    "post_text" to mapOf(
                        "type" to "string",
                        "description" to "Verbatim text of the post.",
                    ),
                    "post_language" to mapOf(
                        "type" to "string",
                        "enum" to listOf("english", "hinglish", "hindi"),
                    ),
                    "post_register" to mapOf(
                        "type" to "string",
                        "enum" to listOf(
                            "technical", "opinion", "banter", "life_post", "announcement",
                            "personal_story", "growth_product_take", "news", "shitpost",
                        ),
                    ),
                    "existing_reply_angles" to mapOf(
                        "type" to "array",
                        "description" to "One line per distinct angle already visible in the " +
                            "replies. Empty array if none are visible. Do not duplicate these.",
                        "items" to mapOf("type" to "string"),
                    ),
                ),
                "required" to listOf(
                    "author", "author_type", "post_text", "post_language",
                    "post_register", "existing_reply_angles",
                ),
            ),
        )

        val draftSchema = JsonValue.from(
            mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "angle" to mapOf(
                        "type" to "string",
                        "enum" to Angle.entries.map { it.name },
                    ),
                    "thought" to mapOf(
                        "type" to "string",
                        "description" to "Summary of the take or joke, MAX 8 WORDS, so it " +
                            "can be judged in two seconds. Not the reply itself.",
                    ),
                    "text" to mapOf(
                        "type" to "string",
                        "description" to "The reply, ready to paste. Usually one line, " +
                            "5-20 words, lowercase.",
                    ),
                ),
                "required" to listOf("angle", "thought", "text"),
            ),
        )

        return Tool.builder()
            .name(TOOL_NAME)
            .description("Return the extracted post context and exactly six reply drafts.")
            .inputSchema(
                Tool.InputSchema.builder()
                    .properties(
                        Tool.InputSchema.Properties.builder()
                            .putAdditionalProperty("post_context", postContextSchema)
                            .putAdditionalProperty(
                                "drafts",
                                JsonValue.from(
                                    mapOf(
                                        "type" to "array",
                                        "description" to "Exactly six drafts, strongest first. At " +
                                            "least 2 must be funny, and at least 1 must " +
                                            "be Hinglish when the author is Indian or " +
                                            "the topic is desi-context.",
                                        "minItems" to 6,
                                        "maxItems" to 6,
                                        "items" to draftSchema,
                                    ),
                                ),
                            )
                            .build(),
                    )
                    .required(listOf("post_context", "drafts"))
                    .build(),
            )
            .build()
    }

    private companion object {
        const val TAG = "ClaudeClient"
        const val MODEL = "claude-sonnet-4-6"
        const val MAX_TOKENS = 4096L
        const val REFINE_MAX_TOKENS = 1024L
        const val CHAT_MAX_TOKENS = 1500L
        const val TOOL_NAME = "deliver_drafts"

        const val ANALYZE_INSTRUCTION =
            "This is a screenshot of one X post, and possibly some of its visible replies. " +
                "Consecutive images are ordered top to bottom and may overlap slightly. " +
                "Extract the post and draft six replies. " +
                "Ignore the surrounding UI chrome, the status bar, and any unrelated posts."
    }
}

class ClaudeException(message: String) : Exception(message)
