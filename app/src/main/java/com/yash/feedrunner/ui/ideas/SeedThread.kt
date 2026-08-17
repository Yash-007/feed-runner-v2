package com.yash.feedrunner.ui.ideas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yash.feedrunner.ui.ChatRole
import com.yash.feedrunner.ui.SeedIdea
import com.yash.feedrunner.ui.SeedStatus
import com.yash.feedrunner.ui.StoredSeed

/**
 * One seed's ideation conversation.
 *
 * Generating is a turn in the thread rather than a separate button: send nothing to
 * just generate, or type an instruction to steer the round. Ideas arrive as cards in
 * the same timeline as the instructions that produced them, which is what makes the
 * history readable weeks later.
 */
@Composable
internal fun SeedThreadScreen(
    seed: StoredSeed,
    generating: Boolean,
    error: String?,
    onBack: () -> Unit,
    onGenerate: (String) -> Unit,
    onDeleteIdea: (SeedIdea) -> Unit,
    onCopy: (String) -> Unit,
    onSetStatus: (SeedStatus) -> Unit,
    onDeleteSeed: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    // Earlier rounds fold away so the newest generation is what you see.
    var showEarlier by remember(seed.remoteId) { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val latestRound = seed.ideas.maxOfOrNull { it.round } ?: 0
    val earlierCount = seed.ideas.count { it.round < latestRound }
    val entries = remember(seed.chat, seed.ideas, showEarlier, latestRound) {
        buildThread(seed, showEarlier, latestRound)
    }

    // Follow the conversation as it grows, which after a generation is the point.
    LaunchedEffect(entries.size, generating) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ThreadHeader(
            seed = seed,
            onBack = onBack,
            onSetStatus = onSetStatus,
            onDeleteSeed = onDeleteSeed,
        )

        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // The weight belongs to a plain Box. Given to the selection container
        // directly, the list sized itself to its content and pushed the composer
        // off the bottom of the screen, so there was no way to generate at all.
        //
        // The composer stays outside the container: a text field inside one fights
        // the field's own selection.
        Box(modifier = Modifier.weight(1f)) {
            SelectionContainer {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (seed.ideas.isEmpty() && seed.chat.isEmpty()) {
                    item { EmptyThreadHint() }
                }

                if (earlierCount > 0) {
                    item {
                        Text(
                            text = if (showEarlier) {
                                "hide $earlierCount earlier"
                            } else {
                                "$earlierCount earlier ideas"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showEarlier = !showEarlier }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }

                items(entries, key = { it.key }) { entry ->
                    when (entry) {
                        is ThreadEntry.Instruction -> InstructionBubble(entry.text)
                        is ThreadEntry.Answer -> AnswerBubble(entry.text)
                        is ThreadEntry.Idea -> IdeaCard(
                            idea = entry.idea,
                            onCopy = onCopy,
                            onDelete = { onDeleteIdea(entry.idea) },
                        )
                    }
                }

                if (generating) {
                    item { GeneratingRow() }
                }
            }
            }
        }

        Composer(
            value = input,
            enabled = !generating,
            hasIdeas = seed.ideas.isNotEmpty(),
            onValueChange = { input = it },
            onSend = {
                onGenerate(input.trim())
                input = ""
            },
        )
    }
}

/** Merged timeline. Messages and ideas share a clock, so they sort together. */
private fun buildThread(
    seed: StoredSeed,
    showEarlier: Boolean,
    latestRound: Int,
): List<ThreadEntry> {
    val messages = seed.chat.mapIndexed { index, message ->
        if (message.role == ChatRole.USER) {
            ThreadEntry.Instruction(message.text, message.atMillis, index)
        } else {
            ThreadEntry.Answer(message.text, message.atMillis, index)
        }
    }
    val ideas = seed.ideas
        .filter { showEarlier || it.round >= latestRound }
        .map { ThreadEntry.Idea(it) }

    // Instruction before the ideas it produced when both carry the same stamp.
    return (messages + ideas).sortedWith(
        compareBy({ it.atMillis }, { if (it is ThreadEntry.Idea) 1 else 0 }),
    )
}

private sealed interface ThreadEntry {
    val atMillis: Long
    val key: String

    data class Instruction(val text: String, override val atMillis: Long, val index: Int) :
        ThreadEntry {
        override val key: String get() = "msg-$index"
    }

    data class Answer(val text: String, override val atMillis: Long, val index: Int) :
        ThreadEntry {
        override val key: String get() = "msg-$index"
    }

    data class Idea(val idea: SeedIdea) : ThreadEntry {
        override val atMillis: Long get() = idea.atMillis
        override val key: String get() = "idea-${idea.id}"
    }
}

@Composable
private fun ThreadHeader(
    seed: StoredSeed,
    onBack: () -> Unit,
    onSetStatus: (SeedStatus) -> Unit,
    onDeleteSeed: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(tonalElevation = 2.dp) {
        Column(modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "‹",
                    fontSize = 26.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onBack)
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                )
                Text(
                    text = seed.seed.themeTags.firstOrNull() ?: seed.source.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    Text(
                        text = "⋯",
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { menuOpen = true }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        SeedStatus.entries.filter { it != seed.status }.forEach { status ->
                            DropdownMenuItem(
                                text = { Text("mark ${status.label}") },
                                onClick = {
                                    menuOpen = false
                                    onSetStatus(status)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "delete seed",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onDeleteSeed()
                            },
                        )
                    }
                }
            }

            SelectionContainer {
                Column {
                    Text(
                        text = seed.headline,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 2.dp),
                    )
                    if (seed.seed.angleHint.isNotBlank()) {
                        Text(
                            text = seed.seed.angleHint,
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 4.dp),
                        )
                    }
                }
            }
            if (seed.lanes.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .padding(start = 12.dp, top = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                ) {
                    seed.lanes.forEach { lane ->
                        Text(
                            text = lane,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyThreadHint() {
    Text(
        text = "Send to generate posts from this seed. Add an instruction first if you " +
            "want a particular angle.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 20.dp, horizontal = 4.dp),
    )
}

@Composable
private fun InstructionBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 16.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun AnswerBubble(text: String) {
    Surface(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.widthIn(max = 320.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
        )
    }
}

/** A generated post. Tap copies it, the corner action removes it for good. */
@Composable
private fun IdeaCard(idea: SeedIdea, onCopy: (String) -> Unit, onDelete: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var copied by remember(idea.id) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1400)
            copied = false
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (idea.register.isNotBlank()) {
                    Text(
                        text = idea.registerLabel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(registerColor(idea.register))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (idea.play.isNotBlank()) {
                    Text(
                        text = idea.playLabel,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                if (copied) {
                    Text(
                        text = "copied",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Box(modifier = Modifier.weight(1f))
                Text(
                    text = "⌫",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }

            if (idea.thought.isNotBlank()) {
                Text(
                    text = idea.thought,
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }

            Text(
                text = idea.postText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCopy(idea.postText)
                        copied = true
                    },
            )

            if (idea.lane.isNotBlank() || idea.whyNow.isNotBlank()) {
                Text(
                    text = listOf(idea.lane, idea.whyNow)
                        .filter(String::isNotBlank)
                        .joinToString(" · "),
                    fontSize = 10.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun GeneratingRow() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 1.5.dp)
        Text(
            text = "writing posts",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun Composer(
    value: String,
    enabled: Boolean,
    hasIdeas: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                placeholder = {
                    Text(
                        text = if (hasIdeas) "refine, or send to generate more" else "instruction, optional",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(22.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.weight(1f),
            )
            // Always enabled: an empty send is a plain generate, which is the most
            // common thing you want here.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.3f),
                    )
                    .clickable(enabled = enabled, onClick = onSend),
            ) {
                Text(
                    text = "↑",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 19.sp,
                )
            }
        }
    }
}

/** Register colours, so the tonal mix of a round is visible at a glance. */
internal fun registerColor(register: String): Color = when (register.lowercase()) {
    "shitpost" -> Color(0xFFF5A623)
    "war_story" -> Color(0xFFFF6B9D)
    "thought" -> Color(0xFF00BA7C)
    else -> Color(0xFF1D9BF0)
}
