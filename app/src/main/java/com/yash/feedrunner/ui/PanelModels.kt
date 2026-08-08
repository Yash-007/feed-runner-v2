package com.yash.feedrunner.ui

import androidx.compose.ui.graphics.Color

/** The angle a suggested reply takes relative to the original post. */
enum class Angle(val label: String, val color: Color) {
    ADD("ADD", Color(0xFF1D9BF0)),
    PUSH_BACK("PUSH BACK", Color(0xFFE0245E)),
    EXTEND("EXTEND", Color(0xFF7856FF)),
}

/** One-tap refinements offered under each draft. */
enum class Refinement(val label: String) {
    SHORTER("shorter"),
    SPICIER("spicier"),
    MORE_TECHNICAL("more technical"),
    SOFTER("softer"),
}

data class Draft(
    val id: Int,
    val angle: Angle,
    val text: String,
    /** True while a refinement request for this draft is in flight. */
    val refining: Boolean = false,
)

data class Verdict(
    val worthReplying: Boolean,
    val reason: String,
)

/** The most recent analysis, persisted so it can be reopened for free. */
data class StoredResult(
    val verdict: Verdict,
    val drafts: List<Draft>,
    val thumbnailPath: String?,
    val savedAtMillis: Long,
)

/** Whether the drafts on screen were just generated or restored from disk. */
sealed interface ResultSource {
    data object Fresh : ResultSource

    data class Cached(
        val savedAtMillis: Long,
        val thumbnailPath: String?,
    ) : ResultSource
}

sealed interface PanelState {
    data object Loading : PanelState

    data class Ready(
        val verdict: Verdict,
        val drafts: List<Draft>,
        val source: ResultSource = ResultSource.Fresh,
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

/** Stand-in content so the whole UI flow can be exercised before the API is wired up. */
object MockData {
    fun ready() = PanelState.Ready(
        verdict = Verdict(
            worthReplying = true,
            reason = "Strong opinion from a big account, low reply count so far — good window to be early with a concrete counterpoint.",
        ),
        drafts = listOf(
            Draft(
                id = 0,
                angle = Angle.ADD,
                text = "This matches what we saw migrating 40 services: the bottleneck was never the framework, it was that nobody owned the schema. Tooling just made the ownership gap louder.",
            ),
            Draft(
                id = 1,
                angle = Angle.PUSH_BACK,
                text = "Half agree. The cost curve you're describing flattens once you're past ~10 services — below that the abstraction overhead genuinely does cost more than it saves.",
            ),
            Draft(
                id = 2,
                angle = Angle.EXTEND,
                text = "The part nobody talks about: this same failure mode shows up in data pipelines two years later, except by then the blast radius includes finance reporting.",
            ),
        ),
    )

    /** Fake refinement so chip taps visibly change the draft before the API exists. */
    fun refine(draft: Draft, refinement: Refinement): Draft {
        val text = when (refinement) {
            Refinement.SHORTER ->
                draft.text.split(". ").first().trimEnd('.') + "."
            Refinement.SPICIER ->
                "Hot take: " + draft.text.replaceFirstChar { it.lowercase() }
            Refinement.MORE_TECHNICAL ->
                draft.text + " Concretely: p99 went from 340ms to 90ms once we moved the join server-side."
            Refinement.SOFTER ->
                "Genuinely curious about your take here — " + draft.text.replaceFirstChar { it.lowercase() }
        }
        return draft.copy(text = text, refining = false)
    }
}
