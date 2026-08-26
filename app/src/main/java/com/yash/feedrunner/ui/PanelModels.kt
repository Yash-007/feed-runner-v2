package com.yash.feedrunner.ui

import androidx.compose.ui.graphics.Color

/**
 * The angle a suggested reply takes. Duplicates across the four drafts are
 * expected — a good banter post can earn three different jokes.
 */
enum class Angle(val label: String, val chipLabel: String, val color: Color) {
    ADD("ADD", "add", Color(0xFF1D9BF0)),
    PUSH_BACK("PUSH BACK", "push back", Color(0xFFE0245E)),
    EXTEND("EXTEND", "extend", Color(0xFF7856FF)),
    BANTER("BANTER", "funnier", Color(0xFFF5A623)),
    RELATE("RELATE", "relate", Color(0xFF00BA7C)),
    ASK("ASK", "ask", Color(0xFF00B8D9)),
    APPRECIATE("APPRECIATE", "appreciate", Color(0xFFFF6B9D)),
    HUMAN("HUMAN", "human", Color(0xFF8B98A5)),
}

/**
 * Angles offered as one-tap batches in the reply chat.
 *
 * BANTER leads as "funnier" because that is what you reach for most; ASK is here
 * because a sharp question is the best chance of a reply from the author.
 */
val BATCH_ANGLES = listOf(
    Angle.BANTER,
    Angle.ADD,
    Angle.EXTEND,
    Angle.PUSH_BACK,
    Angle.APPRECIATE,
    Angle.ASK,
)

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
/**
 * One chat turn. [atMillis] is only set for seed threads, where messages and
 * generated ideas share one timeline and have to be ordered against each other.
 */
data class ChatMessage(
    val role: ChatRole,
    val text: String,
    val atMillis: Long = 0L,
    /**
     * Set when this turn came from tapping an angle chip, so the bubble can show
     * which angle it is. Null for ordinary chat.
     */
    val angle: Angle? = null,
)

/** The most recent analysis, persisted so it can be reopened for free. */
data class StoredResult(
    val platform: Platform = Platform.X,
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
    /** Which network drafted it, so the rail can tell a comment from a reply. */
    val platform: Platform = Platform.X,
)

/** Whether the drafts on screen were just generated or restored from disk. */
sealed interface ResultSource {
    data object Fresh : ResultSource

    data class Cached(val savedAtMillis: Long) : ResultSource
}

sealed interface PanelState {
    data object Loading : PanelState

    data class Ready(
        val platform: Platform = Platform.X,
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
        /** Set when a chat turn failed, shown in the thread with a retry. */
        val chatError: String? = null,
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
