package com.yash.feedrunner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yash.feedrunner.ui.theme.MetaTextStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

/**
 * Composer for posting about the capture, or quote-posting on top of it.
 *
 * The text field is deliberately vague about what to type, because the prompt
 * decides for itself whether what you wrote is a thought to build on or an
 * instruction to follow. Which way it read you comes back in the result and is
 * shown there, so a misread is visible rather than silent.
 */
@Composable
fun RepostPanel(
    state: RepostState,
    mode: RepostMode,
    capturePath: String?,
    userText: String,
    onModeChange: (RepostMode) -> Unit,
    onUserTextChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onCopyText: (String) -> Unit,
    onCopyDraft: (PostDraft) -> Unit,
    onSendChat: (String) -> Unit,
    onRetryChat: () -> Unit,
    onToggleUsed: (PostDraft) -> Unit,
    heldDraftsAge: String?,
    onOpenHeldDrafts: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var viewerPath by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val ready = state as? RepostState.Ready

    // Reopened composer. Keyed on the result so a fresh generation collapses it
    // again rather than leaving the sheet in edit mode.
    var editing by remember(ready?.result?.savedAtMillis) { mutableStateOf(false) }

    // Same corner as the chat send button, so it yields while you are typing.
    var inputFocused by remember { mutableStateOf(false) }
    val trackFocus: (Boolean) -> Unit = { focused ->
        inputFocused = focused
        onFocusChanged(focused)
    }

    // Keep the bottom in view the whole time the keyboard is up.
    //
    // This window reports no insets at all, so imePadding cannot help; what actually
    // happens is that the keyboard resizes the window under us. The content is then
    // taller than the window and the Draft button, being last, ended up cut in half
    // by the window edge. Keyed on maxValue because the resize lands a frame or two
    // after the focus event, so scrolling once on focus used a stale value.
    LaunchedEffect(inputFocused, scrollState.maxValue) {
        if (inputFocused) scrollState.animateScrollTo(scrollState.maxValue)
    }

    // Drop to the drafts as soon as they land; the composer is above them. Keyed
    // on the result rather than the state so a chat turn does not re-trigger it.
    LaunchedEffect(ready?.result?.savedAtMillis) {
        if (ready != null) scrollState.animateScrollTo(scrollState.maxValue)
    }

    FollowChatGrowth(
        scrollState = scrollState,
        chatSize = ready?.result?.chat?.size ?: 0,
        resetKey = ready?.result?.savedAtMillis,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = SheetShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .imePadding()
                .clickable(enabled = false) {},
        ) {
            SelectionActionsHost {
            Box {
                Column(
                    modifier = Modifier
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Compose",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onDismiss) { Text("Close") }
                    }

                    if (heldDraftsAge != null) {
                        HeldDraftsBanner(age = heldDraftsAge, onOpen = onOpenHeldDrafts)
                    }

                    // Once drafts exist the composer has done its job, and leaving
                    // it expanded pushes the drafts you came for below the fold.
                    // It collapses to a brief you can reopen to change and rerun.
                    if (ready == null || editing) {
                        ModeToggle(
                            mode = mode,
                            enabled = state !is RepostState.Loading,
                            onModeChange = onModeChange,
                        )

                        CapturedRow(capturePath = capturePath, onView = { viewerPath = it })

                        Composer(
                            mode = mode,
                            userText = userText,
                            enabled = state !is RepostState.Loading,
                            autoFocus = state is RepostState.Composing,
                            onUserTextChange = onUserTextChange,
                            onFocusChanged = trackFocus,
                            onGenerate = onGenerate,
                        )
                    } else {
                        ComposeBrief(
                            mode = ready.result.mode,
                            userText = userText,
                            reading = ready.result.reading,
                            capturePath = capturePath,
                            onView = { viewerPath = it },
                            onEdit = { editing = true },
                        )
                    }

                    when (state) {
                        is RepostState.Composing -> Unit
                        is RepostState.Loading -> GeneratingRow(mode)
                        is RepostState.Error -> Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        is RepostState.Ready -> {
                            Results(
                                result = state.result,
                                onCopyDraft = onCopyDraft,
                                onToggleUsed = onToggleUsed,
                                chat = state.result.chat,
                                chatPending = state.chatPending,
                                chatError = state.chatError,
                                chatTitle = "Ask for a different " +
                                    state.result.mode.label.lowercase(),
                                onCopyText = onCopyText,
                                onRetryChat = onRetryChat,
                            )
                            ChatComposer(
                                pending = state.chatPending,
                                quickPrompts = POST_QUICK_PROMPTS,
                                onSend = onSendChat,
                                onFocusChanged = trackFocus,
                            )
                        }
                    }
                }

                JumpToBottom(
                    visible = !inputFocused &&
                        scrollState.value < scrollState.maxValue - JUMP_VISIBLE_SLOP,
                    onClick = { scope.launch { scrollState.animateScrollTo(scrollState.maxValue) } },
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
            }
        }

        viewerPath?.let { path ->
            CaptureViewer(path = path, onDismiss = { viewerPath = null })
        }
    }
}

/**
 * Way back to the last generation, shown only on a fresh capture.
 *
 * Repost used to reopen these automatically, which meant a new capture was
 * impossible once anything was stored. Offering them here keeps them one tap away
 * without hijacking the button.
 */
@Composable
private fun HeldDraftsBanner(age: String, onOpen: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .clickable(onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = "Last drafts from $age",
            style = MetaTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "open",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Marks a post draft as the one you used. Copying sets it, and the backend mirrors
 * it, so tapping here to unmark removes the stored pick as well.
 */
@Composable
private fun PostUsedMarker(used: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            text = "✓",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (used) {
                UsedGreen
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            },
        )
        if (used) {
            Text(
                text = " used",
                style = MetaTextStyle,
                fontWeight = FontWeight.SemiBold,
                color = UsedGreen,
            )
        }
    }
}

/** Post vs Quote. The single most consequential choice here, so it leads. */
@Composable
private fun ModeToggle(
    mode: RepostMode,
    enabled: Boolean,
    onModeChange: (RepostMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(3.dp),
    ) {
        RepostMode.entries.forEach { option ->
            val selected = option == mode
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(19.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    )
                    .clickable(enabled = enabled && !selected) { onModeChange(option) }
                    .padding(vertical = 9.dp),
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
    Text(
        text = when (mode) {
            RepostMode.POST -> "Original post. The capture goes with it as the image."
            RepostMode.QUOTE -> "Your line sits above the quoted post."
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun CapturedRow(capturePath: String?, onView: (String) -> Unit) {
    val preview = rememberDecoded(capturePath, MAX_PREVIEW_PIXELS)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 14.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(8.dp),
    ) {
        if (preview != null && capturePath != null) {
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = "View capture",
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .size(width = 50.dp, height = 64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onView(capturePath) },
            )
        }
        Column(modifier = Modifier.padding(start = if (preview != null) 10.dp else 2.dp)) {
            Text(
                text = "Your capture",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (capturePath != null) "tap to check it" else "attached",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * What the drafts were generated from, in one row: the capture, the mode, and what
 * you typed. Everything here is a summary of a decision already made, so it stays
 * small, but the capture is bigger than in the composer because checking it is the
 * main reason to look back at this.
 */
@Composable
private fun ComposeBrief(
    mode: RepostMode,
    userText: String,
    reading: TextReading,
    capturePath: String?,
    onView: (String) -> Unit,
    onEdit: () -> Unit,
) {
    val preview = rememberDecoded(capturePath, MAX_PREVIEW_PIXELS)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(9.dp),
    ) {
        if (preview != null && capturePath != null) {
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = "View capture",
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .size(width = 54.dp, height = 68.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onView(capturePath) },
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (preview != null) 10.dp else 2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = mode.label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                if (reading != TextReading.NONE) {
                    Text(
                        text = reading.label,
                        style = MetaTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            Text(
                text = userText.trim().ifBlank { "from the image alone" },
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = if (userText.isBlank()) FontStyle.Italic else FontStyle.Normal,
                color = if (userText.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        Text(
            text = "edit",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .clickable(onClick = onEdit)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun Composer(
    mode: RepostMode,
    userText: String,
    enabled: Boolean,
    autoFocus: Boolean,
    onUserTextChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onGenerate: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    // Only raise the keyboard for a fresh capture. Reopening finished drafts has
    // nothing to type, and the keyboard would cover the drafts you came back for.
    LaunchedEffect(autoFocus) {
        if (autoFocus) runCatching { focusRequester.requestFocus() }
    }

    Column {
        Text(
            text = "Your thought, or an instruction",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Optional. A half-formed take gets built on; a directive gets followed.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )

        OutlinedTextField(
            value = userText,
            onValueChange = onUserTextChange,
            enabled = enabled,
            placeholder = {
                Text(
                    text = when (mode) {
                        RepostMode.POST -> "e.g. this is actually good for serious players"
                        RepostMode.QUOTE -> "e.g. angle on the compliance part"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(18.dp),
            maxLines = 5,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChanged(it.isFocused) },
        )

        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            steersFor(mode).forEach { option ->
                Text(
                    text = option,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .clickable(enabled = enabled) {
                            onUserTextChange(
                                if (userText.isBlank()) option else "$userText, $option",
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onGenerate),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp)) {
                Text(
                    text = when (mode) {
                        RepostMode.POST -> "Draft 6 posts"
                        RepostMode.QUOTE -> "Draft 6 quote posts"
                    },

                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun GeneratingRow(mode: RepostMode) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            text = when (mode) {
                RepostMode.POST -> "Writing posts…"
                RepostMode.QUOTE -> "Writing quote posts…"
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun Results(
    result: RepostResult,
    onCopyDraft: (PostDraft) -> Unit,
    onToggleUsed: (PostDraft) -> Unit,
    chat: List<ChatMessage>,
    chatPending: Boolean,
    chatError: String?,
    chatTitle: String,
    onCopyText: (String) -> Unit,
    onRetryChat: () -> Unit,
) {
    var selectingId by remember { mutableIntStateOf(-1) }
    var copiedId by remember { mutableIntStateOf(-1) }

    LaunchedEffect(copiedId) {
        if (copiedId >= 0) {
            delay(1400)
            copiedId = -1
        }
    }

    Column(modifier = Modifier.padding(top = 16.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // The capture summary and the drafts in one container: long press selects
        // straight away, the way it does in the chat, rather than needing a first
        // press just to arm selection.
        SelectionContainer {
            Column {
                CaptureSummary(result)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                ) {
                    Text(
                        text = "Drafts",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "tap to copy · long press to select",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                result.drafts.forEach { draft ->
                    PostDraftCard(
                        draft = draft,
                        copied = copiedId == draft.id,
                        onCopy = {
                            onCopyDraft(draft)
                            copiedId = draft.id
                        },
                        onToggleUsed = { onToggleUsed(draft) },
                    )
                }

                // Inside the same container as the drafts: one selection scope for
                // all the read-only text in this sheet.
                ChatHistory(
                    chat = chat,
                    pending = chatPending,
                    title = chatTitle,
                    error = chatError,
                    onCopyText = onCopyText,
                    onRetry = onRetryChat,
                )
            }
        }
    }
}

/** What the model read off the capture, plus how it took your text. */
@Composable
private fun CaptureSummary(result: RepostResult) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = result.capture.contentLabel,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
            if (result.reading != TextReading.NONE) {
                Text(
                    text = result.reading.label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }
        if (result.capture.summary.isNotEmpty()) {
            Text(
                text = result.capture.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        result.capture.quotedAuthor?.let { author ->
            Text(
                text = "quoting $author",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostDraftCard(
    draft: PostDraft,
    copied: Boolean,
    onCopy: () -> Unit,
    onToggleUsed: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val body = @Composable {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = draft.style.label,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(draft.style.color)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                        if (draft.thought.isNotEmpty()) {
                            Text(
                                text = draft.thought,
                                style = MaterialTheme.typography.labelSmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(start = 7.dp)
                                    .weight(1f),
                            )
                        } else {
                            Box(modifier = Modifier.weight(1f))
                        }
                        PostUsedMarker(used = draft.used, onClick = onToggleUsed)
                    }
                    Text(
                        text = draft.text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            Box(
                modifier = Modifier.clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCopy()
                },
            ) { body() }
        }

        AnimatedVisibility(
            visible = copied,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
        ) {
            Text(
                text = "copied",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 3.dp, start = 4.dp),
            )
        }
    }
}
