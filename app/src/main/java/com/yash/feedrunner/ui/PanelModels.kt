package com.yash.feedrunner.ui

import androidx.compose.ui.graphics.Color

/**
 * The angle a suggested reply takes. Duplicates across the four drafts are
 * expected — a good banter post can earn three different jokes.
 */
enum class Angle(val label: String, val color: Color) {
    ADD("ADD", Color(0xFF1D9BF0)),
    PUSH_BACK("PUSH BACK", Color(0xFFE0245E)),
    EXTEND("EXTEND", Color(0xFF7856FF)),
    BANTER("BANTER", Color(0xFFF5A623)),
    RELATE("RELATE", Color(0xFF00BA7C)),
    ASK("ASK", Color(0xFF00B8D9)),
    APPRECIATE("APPRECIATE", Color(0xFFFF6B9D)),
    HUMAN("HUMAN", Color(0xFF8B98A5)),
}

/**
 * One-tap rewrites. [instruction] is what actually reaches the model, so the
 * chip label can stay short while the instruction carries the real intent.
 */
enum class Refinement(val label: String, val instruction: String) {
    SHORTER(
        "shorter",
        "shorter. cut it to the fewest words that still land. a fragment is fine",
    ),
    FUNNIER(
        "funnier",
        "funnier, by making one concrete detail more specific. deadpan, not loud",
    ),
    SPECIFIC(
        "specific",
        "more specific: a real number, system name, failure mode, or observed behaviour",
    ),
    SPICIER(
        "spicier",
        "sharper. take a clearer side and be more willing to disagree",
    ),
    HINGLISH(
        "hinglish",
        "in natural Hinglish. code-switch mid-sentence, Hindi carries the emotion and " +
            "English carries the information. romanized only, never Devanagari",
    ),
}

/** What Claude read off the screenshot — lets you confirm it got the right post. */
data class PostContext(
    val author: String,
    val authorType: String,
    val postText: String,
    val language: String,
    val register: String,
) {
    /** Hinglish is only offered where it would actually land. */
    val desiContext: Boolean
        get() = authorType.equals("indian", ignoreCase = true) ||
            language.equals("hinglish", ignoreCase = true) ||
            language.equals("hindi", ignoreCase = true)

    val registerLabel: String get() = register.replace('_', ' ')

    /** Chips offered under each draft for this post. */
    val refinements: List<Refinement>
        get() = buildList {
            add(Refinement.SHORTER)
            add(Refinement.FUNNIER)
            add(Refinement.SPECIFIC)
            add(Refinement.SPICIER)
            if (desiContext) add(Refinement.HINGLISH)
        }
}

data class Draft(
    val id: Int,
    val angle: Angle,
    /** One-line summary of the take, so a draft can be judged at a glance. */
    val thought: String,
    val text: String,
    /** True while a refinement request for this draft is in flight. */
    val refining: Boolean = false,
    /**
     * Set once you copy a draft, and persisted, so reopening a result shows which
     * one you actually sent. Copying is the only signal available: the app never
     * sees the post go out.
     */
    val used: Boolean = false,
)

enum class ChatRole { USER, ASSISTANT }

/** One turn in the per-post chat. */
data class ChatMessage(val role: ChatRole, val text: String)

/** The most recent analysis, persisted so it can be reopened for free. */
data class StoredResult(
    val postContext: PostContext,
    val drafts: List<Draft>,
    val thumbnailPath: String?,
    /** Full-size capture, for the tap-to-view viewer. */
    val capturePath: String?,
    val savedAtMillis: Long,
    /** Free-form conversation about this post, oldest first. */
    val chat: List<ChatMessage> = emptyList(),
)

/** One saved result, reduced to what the history strip needs to render it. */
data class HistoryEntry(
    val savedAtMillis: Long,
    val author: String,
    val thumbnailPath: String?,
)

/** Whether the drafts on screen were just generated or restored from disk. */
sealed interface ResultSource {
    data object Fresh : ResultSource

    data class Cached(val savedAtMillis: Long) : ResultSource
}

sealed interface PanelState {
    data object Loading : PanelState

    data class Ready(
        val postContext: PostContext,
        val drafts: List<Draft>,
        val source: ResultSource = ResultSource.Fresh,
        /** Saved results, newest first. Drives the switcher strip. */
        val history: List<HistoryEntry> = emptyList(),
        /** Small preview of the capture; used for the inline thumbnail. */
        val thumbnailPath: String? = null,
        /** Full-size capture behind these drafts, shown on tap. */
        val capturePath: String? = null,
        /** Conversation about this post, oldest first. */
        val chat: List<ChatMessage> = emptyList(),
        /** True while a chat reply is in flight. */
        val chatPending: Boolean = false,
    ) : PanelState

    data class Error(val message: String) : PanelState
}

/** "just now" / "4 min ago" / "2 h ago" / "3 d ago" */
fun relativeAge(savedAtMillis: Long, now: Long = System.currentTimeMillis()): String {
    val seconds = ((now - savedAtMillis) / 1000).coerceAtLeast(0)
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60} min ago"
        seconds < 86_400 -> "${seconds / 3600} h ago"
        else -> "${seconds / 86_400} d ago"
    }
}
