package com.yash.feedrunner.ui

import androidx.compose.ui.graphics.Color

/**
 * The idea_seed the model may attach to a generation: the reusable thought behind
 * a post, kept even when the reply itself is thrown away.
 *
 * Every field is optional because the model returns the seed only when a post is
 * actually worth building on, and may fill in only part of it.
 */
data class IdeaSeed(
    val themeTags: List<String> = emptyList(),
    val tension: String = "",
    val angleHint: String = "",
    val shelfLife: String = "",
) {
    /** A seed with nothing in it is not worth storing. */
    val isEmpty: Boolean
        get() = themeTags.isEmpty() && tension.isBlank() &&
            angleHint.isBlank() && shelfLife.isBlank()
}

/** Where a stored seed came from. */
enum class SeedSource(val wire: String, val label: String) {
    REPLY("reply", "reply"),
    POST("post", "post"),
    QUOTE("quote", "quote"),
    MANUAL("manual", "mine"),
    ;

    companion object {
        fun fromWire(wire: String): SeedSource =
            entries.firstOrNull { it.wire == wire } ?: MANUAL
    }
}

enum class SeedStatus(val wire: String, val label: String, val color: Color) {
    NEW("new", "new", Color(0xFF1D9BF0)),
    POSTED("posted", "posted", Color(0xFF00BA7C)),
    SKIPPED("skipped", "skipped", Color(0xFF8B98A5)),
    ;

    companion object {
        fun fromWire(wire: String): SeedStatus =
            entries.firstOrNull { it.wire == wire } ?: NEW
    }
}

/**
 * A seed as the Ideas screen shows it. [remoteId] is null while the seed is still
 * in the outbox, which is also the only state where status cannot be changed.
 */
data class StoredSeed(
    val remoteId: String?,
    val platform: Platform = Platform.X,
    val clientSeedId: String,
    val source: SeedSource,
    val status: SeedStatus,
    val seed: IdeaSeed,
    val note: String = "",
    val postAuthor: String = "",
    val postText: String = "",
    val createdAtMillis: Long = 0L,
    /** Conversation about this seed, stored server-side so it follows the seed. */
    val chat: List<ChatMessage> = emptyList(),
    /** Posts generated in that conversation. */
    val ideas: List<SeedIdea> = emptyList(),
    /** Latest emerging-lane read for this seed's themes. */
    val lanes: List<String> = emptyList(),
    /** How many generations have run, so a new round can be grouped. */
    val rounds: Int = 0,
) {
    val isPending: Boolean get() = remoteId == null

    /** The line that identifies this seed in a list. */
    val headline: String
        get() = when {
            note.isNotBlank() -> note
            seed.tension.isNotBlank() -> seed.tension
            seed.angleHint.isNotBlank() -> seed.angleHint
            else -> seed.themeTags.joinToString(", ")
        }
}

/** A generated post, stored on the seed it came from. */
data class SeedIdea(
    val id: String,
    val postText: String,
    val play: String = "",
    val register: String = "",
    val lane: String = "",
    val thought: String = "",
    val whyNow: String = "",
    /** Which generation produced it, so earlier rounds can be folded away. */
    val round: Int = 0,
    val atMillis: Long = 0L,
) {
    val playLabel: String get() = play.lowercase().replace('_', ' ')
    val registerLabel: String get() = register.lowercase().replace('_', ' ')
}

/** One post idea returned by the ideation call. */
data class PostIdea(
    val postText: String,
    /** Which ideation play produced it: REPLY_PROMOTION, CLUSTER, SINGLE_SEED, TIMELY. */
    val play: String = "",
    /** The kind of post: take, shitpost, war_story, thought. */
    val register: String = "",
    /** Short theme label, e.g. "exchange infra reality". */
    val lane: String = "",
    /** What the post says, in at most ten words. */
    val thought: String = "",
    val whyNow: String = "",
    /** Seeds it drew on, so an idea can be traced back to the bank. */
    val seedRefs: List<String> = emptyList(),
) {
    /** Human label for the play, which is shouted in the wire format. */
    val playLabel: String get() = play.lowercase().replace('_', ' ')
    val registerLabel: String get() = register.lowercase().replace('_', ' ')
}

/** One day's reply tally in the streak strip. */
data class DayCount(val date: String, val count: Int)

/**
 * The daily reply habit, derived from picks: copying a draft is the only moment
 * the app can observe a reply actually being used.
 */
data class Streak(
    val today: Int = 0,
    val current: Int = 0,
    val longest: Int = 0,
    val total: Int = 0,
    val days: List<DayCount> = emptyList(),
) {
    val busiestDay: Int get() = days.maxOfOrNull { it.count } ?: 0
}
