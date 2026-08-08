package com.yash.feedrunner.ui

/**
 * One suggested caption for reposting or quoting a post.
 *
 * [note] is the short "why this one" line, the same idea as [Draft.thought].
 * The real response shape is still to be defined, so this stays deliberately
 * thin: whatever the prompt returns should map onto text plus a short label.
 */
data class CaptionSuggestion(
    val id: Int,
    val text: String,
    val note: String = "",
)

sealed interface RepostState {
    /** Composer open, waiting for an optional steer before suggesting. */
    data object Composing : RepostState

    data object Loading : RepostState

    data class Ready(val captions: List<CaptionSuggestion>) : RepostState

    data class Error(val message: String) : RepostState
}

/** One-tap steers offered under the composer field. */
internal val REPOST_STEERS = listOf(
    "hot take",
    "add my experience",
    "one liner",
    "hinglish",
    "disagree with it",
)
