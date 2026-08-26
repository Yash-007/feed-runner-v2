package com.yash.feedrunner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.animation.togetherWith
import com.yash.feedrunner.ui.theme.RoundIconButton
import com.yash.feedrunner.ui.theme.Space
import com.yash.feedrunner.ui.theme.Radius
import com.yash.feedrunner.ui.theme.SoftAccentChip
import com.yash.feedrunner.ui.theme.pressClickable

/**
 * The follow-up conversation, shared by the reply sheet and the compose sheet.
 * Both need the same interaction model: single tap on an answer copies it, a
 * longer press hands it to a selection container for partial copying.
 */
/**
 * The conversation history: bubbles, the typing indicator and any failure.
 *
 * Deliberately does not wrap itself in a SelectionContainer. Two containers under
 * one text toolbar fight each other, because starting a selection in one makes the
 * other clear itself and hide the shared toolbar. Each panel therefore owns a single
 * container covering all of its read-only text, and this composable sits inside it.
 */
@Composable
internal fun ChatHistory(
    chat: List<ChatMessage>,
    pending: Boolean,
    title: String,
    error: String?,
    onCopyText: (String) -> Unit,
    onRetry: () -> Unit,
) {
    var copiedIndex by remember { mutableIntStateOf(-1) }

    // The "copied" marker is transient; clear it without the caller having to.
    LaunchedEffect(copiedIndex) {
        if (copiedIndex >= 0) {
            delay(COPIED_HINT_MS)
            copiedIndex = -1
        }
    }

    Column(modifier = Modifier.padding(top = 6.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (chat.isNotEmpty()) {
                Text(
                    text = "tap to copy · long press to select",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        chat.forEachIndexed { index, message ->
            ChatBubble(
                message = message,
                copied = copiedIndex == index,
                onCopy = {
                    onCopyText(message.text)
                    copiedIndex = index
                },
            )
        }

        if (pending) TypingIndicator()

        error?.let { ChatError(message = it, onRetry = onRetry) }
    }
}

/**
 * Where you type, plus the one-tap steers.
 *
 * Kept out of the selection container above: a text field inside one fights the
 * field's own selection handles.
 */
@Composable
internal fun ChatComposer(
    pending: Boolean,
    quickPrompts: List<String>,
    onSend: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    /** Angles offered as one-tap batches. Empty hides the row. */
    angles: List<Angle> = emptyList(),
    onAngleBatch: (Angle) -> Unit = {},
) {
    var input by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        // Angle chips stay put rather than disappearing once the chat starts: they
        // are the fastest way to ask for a different kind of reply, and wanting one
        // is not limited to the first turn.
        if (angles.isNotEmpty()) {
            AngleChips(angles = angles, enabled = !pending, onPick = onAngleBatch)
        }

        // Offered whenever the field is empty rather than only on the first turn.
        // They used to vanish for good once anything had been sent, which is exactly
        // when you have seen a few replies and want a different cut of them.
        if (quickPrompts.isNotEmpty() && input.isBlank() && !pending) {
            QuickPrompts(prompts = quickPrompts, onPick = onSend)
        }

        ChatInput(
            value = input,
            enabled = !pending,
            busy = pending,
            onValueChange = { input = it },
            onFocusChanged = onFocusChanged,
            onSend = {
                onSend(input.trim())
                input = ""
            },
        )
    }
}

/**
 * A failed turn, in the thread where it happened.
 *
 * This used to be a toast. Over someone else's app a toast is easy to miss, and
 * missing it looks exactly like the answer never arriving.
 */
@Composable
private fun ChatError(message: String, onRetry: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "retry",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * Follows the conversation as it *grows*, without yanking the sheet on open.
 *
 * Baselining the size against open time matters: reopening a result that already
 * has history would otherwise land at the bottom, hiding the drafts. The frame
 * waits matter too, because right after a message is appended [ScrollState.maxValue]
 * still describes the shorter, pre-layout content.
 */
@Composable
internal fun FollowChatGrowth(scrollState: ScrollState, chatSize: Int, resetKey: Any?) {
    var lastSize by remember(resetKey) { mutableIntStateOf(chatSize) }
    LaunchedEffect(chatSize) {
        if (chatSize > lastSize) {
            withFrameNanos {}
            withFrameNanos {}
            scrollState.animateScrollTo(scrollState.maxValue)
        }
        lastSize = chatSize
    }
}

/**
 * One tap per angle, each producing a fresh batch of replies in that angle.
 *
 * Coloured to match the draft cards above, so "another six BANTER" is recognisable
 * without reading the label.
 */
@Composable
private fun AngleChips(angles: List<Angle>, enabled: Boolean, onPick: (Angle) -> Unit) {
    Row(
        modifier = Modifier
            .padding(bottom = 10.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        angles.forEach { angle ->
            val alpha = if (enabled) 1f else 0.4f
            Text(
                text = angle.chipLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = angle.color.copy(alpha = alpha),
                modifier = Modifier
                    .pressClickable(enabled = enabled, pressedScale = 0.94f) { onPick(angle) }
                    .clip(RoundedCornerShape(Radius.chip))
                    .background(angle.color.copy(alpha = 0.16f * alpha))
                    .padding(horizontal = Space.md, vertical = Space.sm),
            )
        }
    }
}

/** One-tap starters, shown only before the conversation begins. */
@Composable
private fun QuickPrompts(prompts: List<String>, onPick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .padding(bottom = 10.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        prompts.forEach { prompt ->
            Text(
                text = prompt,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .pressClickable(pressedScale = 0.94f) { onPick(prompt) }
                    .clip(RoundedCornerShape(Radius.chip))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .padding(horizontal = Space.md, vertical = Space.sm),
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                Text(
                    text = "thinking",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    message: ChatMessage,
    copied: Boolean,
    onCopy: () -> Unit,
) {
    val fromUser = message.role == ChatRole.USER
    val haptics = LocalHapticFeedback.current

    // The squared-off corner points at the speaker, which reads as a tail
    // without drawing one.
    val shape = if (fromUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 16.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start,
    ) {
        // Same soft treatment as the draft cards. This one was missed when the
        // angle pills were toned down, so a batch of replies came back shouting
        // in solid colour next to cards that no longer did.
        message.angle?.takeIf { !fromUser }?.let { angle ->
            SoftAccentChip(
                text = angle.label.lowercase(),
                hue = angle.color,
                modifier = Modifier.padding(bottom = Space.xs),
            )
        }

        Surface(
            shape = shape,
            color = if (fromUser) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            // Tap copies, on your own messages too: rereading something you asked
            // and wanting it back is as common as wanting the answer. Long press is
            // deliberately not handled here so it reaches the selection container.
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCopy()
                    }
                    .padding(horizontal = 13.dp, vertical = 10.dp),
            )
        }

        if (copied) {
            Text(
                text = "copied",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}

@Composable
private fun ChatInput(
    value: String,
    enabled: Boolean,
    busy: Boolean,
    onValueChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSend: () -> Unit,
) {
    val canSend = value.isNotBlank() && enabled
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            placeholder = {
                Text("message", style = MaterialTheme.typography.bodyMedium)
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(Radius.control),
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { onFocusChanged(it.isFocused) },
        )
        SendButton(
            busy = busy,
            enabled = canSend,
            onClick = onSend,
            modifier = Modifier.padding(start = Space.sm),
        )
    }
}

/**
 * The send control wears the request's state: an arrow at rest, a spinner
 * while the model writes. One morphing surface instead of a button that goes
 * dead plus a spinner somewhere else.
 */
@Composable
private fun SendButton(
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = if (enabled || busy) 1f else 0.35f
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = busy,
            transitionSpec = {
                (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith
                    (scaleOut(targetScale = 0.6f) + fadeOut())
            },
            label = "sendMorph",
        ) { writing ->
            if (writing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(
                    text = "↑",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 18.sp,
                )
            }
        }
    }
}

/** Jumps to the newest content from anywhere in the sheet. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun JumpToBottom(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.7f),
        exit = fadeOut() + scaleOut(targetScale = 0.7f),
        modifier = modifier.padding(end = 14.dp, bottom = 14.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(38.dp)
                .clickable(onClick = onClick),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = "↓", color = MaterialTheme.colorScheme.onPrimary, fontSize = 18.sp)
            }
        }
    }
}

/**
 * How close to the bottom counts as "already there".
 *
 * Roughly the height of the composer block, because the composer scrolls with the
 * content into the same corner the button sits in: at a few pixels of slop the
 * button ended up sitting directly on top of the send button. By the time the
 * composer is on screen the newest content is too, so there is nothing to jump to.
 */
internal const val JUMP_VISIBLE_SLOP = 220

/** Shared so every copy confirmation in the app clears at the same pace. */
internal const val COPIED_HINT_MS = 1400L

internal val REPLY_QUICK_PROMPTS = listOf(
    "another angle",
    "make it hinglish",
    "shorter one-liner",
    "more technical",
)

internal val SEED_QUICK_PROMPTS = listOf(
    "turn this into a post",
    "sharper angle",
    "hinglish version",
    "what's the counter take",
)

internal val POST_QUICK_PROMPTS = listOf(
    "another angle",
    "make it hinglish",
    "shorter one-liner",
    "punchier hook",
)
