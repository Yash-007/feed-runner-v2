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
    val clientSeedId: String,
    val source: SeedSource,
    val status: SeedStatus,
    val seed: IdeaSeed,
    val note: String = "",
    val postAuthor: String = "",
    val postText: String = "",
    val createdAtMillis: Long = 0L,
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

/** One idea returned by the ideation call. */
data class PostIdea(
    val hook: String,
    val body: String = "",
    val format: String = "",
    val whyNow: String = "",
)
