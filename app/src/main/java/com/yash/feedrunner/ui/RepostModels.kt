package com.yash.feedrunner.ui

import androidx.compose.ui.graphics.Color

/** Original post about the capture, or a quote post on top of someone else's. */
enum class RepostMode(val wire: String, val label: String) {
    POST("post", "Post"),
    QUOTE("quote", "Quote"),
}

/**
 * The angle a draft takes. Some styles only make sense in one mode (STORY and
 * QUESTION for posts, COSIGN_ADD and COUNTER for quotes), but the model picks,
 * so all eight are renderable either way.
 */
enum class PostStyle(val label: String, val color: Color) {
    TAKE("TAKE", Color(0xFF1D9BF0)),
    OBSERVATION("OBSERVATION", Color(0xFF7856FF)),
    BANTER("BANTER", Color(0xFFF5A623)),
    STORY("STORY", Color(0xFFFF6B9D)),
    QUESTION("QUESTION", Color(0xFF00B8D9)),
    COSIGN_ADD("COSIGN + ADD", Color(0xFF00BA7C)),
    COUNTER("COUNTER", Color(0xFFE0245E)),
    EXTEND("EXTEND", Color(0xFF5E5CE6)),
}

/** How the model interpreted the text you typed. */
enum class TextReading(val wire: String, val label: String) {
    SEED("seed", "built on your thought"),
    INSTRUCTION("instruction", "followed your instruction"),
    NONE("none", ""),
}

/** What the model made of the screenshot; lets you confirm it read the right thing. */
data class CaptureContext(
    val contentType: String,
    val summary: String,
    val quotedAuthor: String?,
    val quotedText: String?,
) {
    val contentLabel: String get() = contentType.replace('_', ' ')
}

data class PostDraft(
    val id: Int,
    val style: PostStyle,
    /** One short line summarising the idea, so a draft can be judged at a glance. */
    val thought: String,
    val text: String,
)

/** A completed generation, kept so closing the sheet does not lose it. */
data class RepostResult(
    val mode: RepostMode,
    val capture: CaptureContext,
    val reading: TextReading,
    val drafts: List<PostDraft>,
    val capturePath: String?,
    val savedAtMillis: Long,
    /** Follow-up conversation about these drafts, persisted with them. */
    val chat: List<ChatMessage> = emptyList(),
)

sealed interface RepostState {
    /** Composer open, waiting for an optional seed or instruction. */
    data object Composing : RepostState

    data object Loading : RepostState

    data class Ready(
        val result: RepostResult,
        val chatPending: Boolean = false,
        /** Set when a chat turn failed, shown in the thread with a retry. */
        val chatError: String? = null,
    ) : RepostState

    data class Error(val message: String) : RepostState
}

/**
 * One-tap steers, split by mode: post steers shape a caption from scratch, quote
 * steers pick a stance towards someone else's post.
 */
internal fun steersFor(mode: RepostMode): List<String> = when (mode) {
    RepostMode.POST -> listOf("make it funny", "hinglish", "one liner", "tell the story", "sharper take")
    RepostMode.QUOTE -> listOf("counter it", "cosign and add", "the joke here", "hinglish", "one liner")
}
