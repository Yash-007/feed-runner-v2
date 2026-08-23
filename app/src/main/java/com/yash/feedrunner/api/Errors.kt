package com.yash.feedrunner.api

/**
 * A drafting call that failed with something worth showing.
 *
 * Kept after the copilot moved to the backend because the callers still speak in
 * these terms, and one of them still raises it locally when a capture cannot be
 * read off disk.
 */
class ClaudeException(message: String) : Exception(message)

/**
 * What to show a person when a call fails.
 *
 * Offline is by far the most common failure and deserves to say so. The SDK used
 * to report a dead network as the bare string "Request failed", which each caller
 * then prefixed with its own wording, so the panel read "Request failed: Request
 * failed".
 */
internal fun humanMessage(error: Throwable): String {
    if (error is ClaudeException) return error.message ?: FALLBACK_MESSAGE
    if (error.looksLikeNoNetwork()) return "No connection. Check your network and retry."
    val detail = error.message
        ?.takeIf { it.isNotBlank() && !it.equals("Request failed", ignoreCase = true) }
    return detail ?: FALLBACK_MESSAGE
}

/** Anything with an IO failure underneath it is the network, whatever wrapped it. */
private fun Throwable.looksLikeNoNetwork(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is java.io.IOException) return true
        current = current.cause.takeIf { it !== current }
    }
    return false
}

private const val FALLBACK_MESSAGE = "Something went wrong. Tap retry."
