package com.yash.feedrunner.ui.ideas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yash.feedrunner.ui.ChatThread
import com.yash.feedrunner.ui.SEED_QUICK_PROMPTS
import com.yash.feedrunner.ui.SeedStatus
import com.yash.feedrunner.ui.StoredSeed
import com.yash.feedrunner.ui.relativeAge

/**
 * One banked seed.
 *
 * Read first: the card body is a tap target for expanding, not for selecting, and
 * selection has its own checkbox. Tapping to read something and tapping to tick it
 * for generation are different intentions, and sharing one gesture between them
 * meant every glance risked a selection.
 *
 * Collapsed shows only what identifies the seed. The angle hint, the originating
 * post, the destructive actions and the conversation all live behind the expand,
 * because a list of fully expanded seeds is unreadable at ten items.
 */
@Composable
internal fun SeedCard(
    seed: StoredSeed,
    selected: Boolean,
    expanded: Boolean,
    chatOpen: Boolean,
    chatPending: Boolean,
    actions: IdeasActions,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(start = 6.dp, top = 10.dp, end = 12.dp),
            ) {
                SelectBox(
                    selected = selected,
                    enabled = !seed.isPending,
                    onClick = { actions.onToggleSelect(seed) },
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { actions.onToggleExpand(seed) }
                        .padding(bottom = 12.dp, end = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChipRow(seed = seed, modifier = Modifier.weight(1f))
                        Text(
                            text = if (seed.isPending) {
                                "syncing"
                            } else {
                                relativeAge(seed.createdAtMillis)
                            },
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Text(
                        text = seed.headline,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )

                    if (seed.seed.themeTags.isNotEmpty()) {
                        Text(
                            text = seed.seed.themeTags.joinToString(" · "),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = if (expanded) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }

                    AnimatedVisibility(visible = expanded) {
                        Column {
                            // The angle hint is the part worth acting on, so it gets
                            // a label rather than sitting as anonymous italics.
                            if (seed.seed.angleHint.isNotBlank()) {
                                Text(
                                    text = "your angle",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                                Text(
                                    text = seed.seed.angleHint,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = FontStyle.Italic,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            if (seed.postAuthor.isNotBlank() || seed.postText.isNotBlank()) {
                                Text(
                                    text = buildString {
                                        append("from ")
                                        append(seed.postAuthor.ifBlank { "a post" })
                                        if (seed.postText.isNotBlank()) {
                                            append(": \"")
                                            append(seed.postText.take(160))
                                            append('"')
                                        }
                                    },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 10.dp),
                                )
                            }
                        }
                    }
                }

            }

            AnimatedVisibility(visible = expanded && !seed.isPending) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 14.dp, bottom = 6.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        StatusMenu(
                            current = seed.status,
                            onPick = { actions.onSetStatus(seed, it) },
                        )
                        TextAction(
                            label = if (chatOpen) "hide chat" else {
                                if (seed.chat.isEmpty()) "chat" else "chat (${seed.chat.size})"
                            },
                            emphasised = seed.chat.isNotEmpty(),
                            onClick = { actions.onToggleChat(seed) },
                        )
                        Box(modifier = Modifier.weight(1f))
                        TextAction(
                            label = "delete",
                            tint = MaterialTheme.colorScheme.error,
                            onClick = { actions.onAskDelete(seed) },
                        )
                    }

                    if (chatOpen) {
                        ChatThread(
                            chat = seed.chat,
                            pending = chatPending,
                            quickPrompts = SEED_QUICK_PROMPTS,
                            title = "Develop this idea",
                            onCopyText = actions.onCopy,
                            onSend = { actions.onSendChat(seed, it) },
                            // An activity already holds window focus, unlike the
                            // overlay panels that have to ask for it.
                            onFocusChanged = {},
                        )
                    }
                }
            }
        }
    }
}

/** Big enough to hit deliberately, and clearly a checkbox rather than a dot. */
@Composable
private fun SelectBox(selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(end = 4.dp)
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Surface(
            shape = RoundedCornerShape(7.dp),
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.Transparent
            },
            border = BorderStroke(
                width = 1.5.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.7f else 0.25f)
                },
            ),
            modifier = Modifier.size(21.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (selected) {
                    Text(
                        text = "✓",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * Source, status and shelf life.
 *
 * Filled chips carry a paired content colour rather than a hardcoded white: in the
 * dark theme the container colours are light, and white on light violet was
 * unreadable.
 */
@Composable
private fun ChipRow(seed: StoredSeed, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        Chip(
            text = seed.source.label,
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Chip(
            text = seed.status.label,
            container = seed.status.color.copy(alpha = 0.18f),
            content = seed.status.color,
        )
        if (seed.seed.shelfLife.isNotBlank()) {
            // Outlined rather than filled: it is the least important of the three,
            // and three filled chips in a row fights for attention.
            Chip(
                text = seed.seed.shelfLife,
                container = Color.Transparent,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
                border = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
private fun Chip(text: String, container: Color, content: Color, border: Color? = null) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = container,
        border = border?.let { BorderStroke(1.dp, it) },
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = content,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

/** Status as a menu, so three states cost one control instead of two buttons. */
@Composable
private fun StatusMenu(current: SeedStatus, onPick: (SeedStatus) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextAction(label = "status: ${current.label} ▾", onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SeedStatus.entries.forEach { status ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = status.label,
                            fontWeight = if (status == current) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                        )
                    },
                    onClick = {
                        open = false
                        if (status != current) onPick(status)
                    },
                )
            }
        }
    }
}

@Composable
private fun TextAction(
    label: String,
    emphasised: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = if (emphasised) FontWeight.SemiBold else FontWeight.Medium,
        color = tint,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    )
}
