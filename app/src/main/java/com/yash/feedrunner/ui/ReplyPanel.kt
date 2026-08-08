package com.yash.feedrunner.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PanelShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

@Composable
fun ReplyPanel(
    state: PanelState,
    onDraftCopy: (Draft) -> Unit,
    onRefine: (Draft, Refinement) -> Unit,
    onSelectResult: (savedAtMillis: Long) -> Unit,
    onSendChat: (String) -> Unit,
    onCopyText: (String) -> Unit,
    onChatFocusChanged: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Path of the capture currently blown up full-screen, if any.
    var viewerPath by remember { mutableStateOf<String?>(null) }

    // Published by the body so the jump-to-bottom button can scroll it.
    var bodyScroll by remember { mutableStateOf<ScrollState?>(null) }
    val scope = rememberCoroutineScope()

    // Scrim: tapping outside the sheet dismisses.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = PanelShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .imePadding()
                // Swallow taps on the sheet so they don't reach the scrim.
                .clickable(enabled = false) {},
        ) {
            Box {
              Column(modifier = Modifier.padding(16.dp)) {
                PanelHeader(onDismiss = onDismiss)

                when (state) {
                    is PanelState.Loading -> LoadingBody()
                    is PanelState.Error -> ErrorBody(state.message, onRetry)
                    is PanelState.Ready -> ReadyBody(
                        state = state,
                        onDraftCopy = onDraftCopy,
                        onRefine = onRefine,
                        onSelectResult = onSelectResult,
                        onViewCapture = { viewerPath = it },
                        onSendChat = onSendChat,
                        onCopyText = onCopyText,
                        onChatFocusChanged = onChatFocusChanged,
                        onScrollState = { bodyScroll = it },
                    )
                }
              }

              bodyScroll?.let { scroll ->
                  JumpToBottom(
                      visible = scroll.maxValue > 0 &&
                          scroll.value < scroll.maxValue - JUMP_VISIBLE_SLOP,
                      onClick = { scope.launch { scroll.animateScrollTo(scroll.maxValue) } },
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

@Composable
private fun PanelHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Feed Runner",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) { Text("Close") }
    }
}

@Composable
private fun LoadingBody() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            text = "Reading the post…",
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ErrorBody(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 24.dp)) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
            Text("Retry")
        }
    }
}

@Composable
private fun ReadyBody(
    state: PanelState.Ready,
    onDraftCopy: (Draft) -> Unit,
    onRefine: (Draft, Refinement) -> Unit,
    onSelectResult: (savedAtMillis: Long) -> Unit,
    onViewCapture: (path: String) -> Unit,
    onSendChat: (String) -> Unit,
    onCopyText: (String) -> Unit,
    onChatFocusChanged: (Boolean) -> Unit,
    onScrollState: (ScrollState) -> Unit,
) {
    val refinements = state.postContext.refinements
    val scrollState = rememberScrollState()

    // Start every new result at the top: the context header and the strongest
    // draft are what you want to see first. Keyed so that a refinement, which
    // changes only the draft text, does not yank you back up mid-edit.
    val resultKey = (state.source as? ResultSource.Cached)?.savedAtMillis
    LaunchedEffect(resultKey, state.postContext.postText) {
        scrollState.scrollTo(0)
    }

    // Follow the conversation only as it *grows*. Baselining against the size at
    // open time matters: otherwise reopening a result that already has chat
    // history would land at the bottom, hiding the drafts and the post context.
    var lastChatSize by remember(resultKey) { mutableIntStateOf(state.chat.size) }
    LaunchedEffect(state.chat.size) {
        if (state.chat.size > lastChatSize) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
        lastChatSize = state.chat.size
    }

    onScrollState(scrollState)

    Column(
        modifier = Modifier.verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        (state.source as? ResultSource.Cached)?.let { cached ->
            HistoryStrip(
                history = state.history,
                selectedId = cached.savedAtMillis,
                onSelect = onSelectResult,
            )
        }
        PostContextBlock(
            context = state.postContext,
            thumbnailPath = state.thumbnailPath,
            capturePath = state.capturePath,
            onViewCapture = onViewCapture,
        )
        state.drafts.forEach { draft ->
            DraftCard(
                draft = draft,
                refinements = refinements,
                onCopy = { onDraftCopy(draft) },
                onRefine = { refinement -> onRefine(draft, refinement) },
            )
        }
        Text(
            text = "Tap a draft to copy it. The panel stays open.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChatSection(
            chat = state.chat,
            pending = state.chatPending,
            onCopyText = onCopyText,
            onSend = onSendChat,
            onFocusChanged = onChatFocusChanged,
        )
    }
}

/**
 * Conversation about the post, for angles the six drafts missed.
 *
 * Assistant replies are usually ready to paste, so a single tap copies one and a
 * long press switches that bubble into a selection container for grabbing part
 * of it. The two gestures are separated rather than layered so neither steals
 * the other's press.
 */
@Composable
private fun ChatSection(
    chat: List<ChatMessage>,
    pending: Boolean,
    onCopyText: (String) -> Unit,
    onSend: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var selectingIndex by remember { mutableIntStateOf(-1) }
    var copiedIndex by remember { mutableIntStateOf(-1) }

    // The "copied" marker is transient; clear it without the caller having to.
    LaunchedEffect(copiedIndex) {
        if (copiedIndex >= 0) {
            delay(COPIED_HINT_MS)
            copiedIndex = -1
        }
    }

    Column(modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        ) {
            Text(
                text = "Ask for anything else",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (chat.isNotEmpty()) {
                Text(
                    text = "tap to copy · hold to select",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (chat.isEmpty() && !pending) {
            QuickPrompts(enabled = true, onPick = onSend)
        }

        chat.forEachIndexed { index, message ->
            ChatBubble(
                message = message,
                selecting = selectingIndex == index,
                copied = copiedIndex == index,
                onCopy = {
                    onCopyText(message.text)
                    copiedIndex = index
                    selectingIndex = -1
                },
                onLongPress = { selectingIndex = index },
            )
        }

        if (pending) TypingIndicator()

        ChatInput(
            value = input,
            enabled = !pending,
            onValueChange = { input = it },
            onFocusChanged = onFocusChanged,
            onSend = {
                onSend(input.trim())
                input = ""
            },
        )
    }
}

/** One-tap starters, shown only before the conversation begins. */
@Composable
private fun QuickPrompts(enabled: Boolean, onPick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .padding(bottom = 10.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        QUICK_PROMPTS.forEach { prompt ->
            Text(
                text = prompt,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .clickable(enabled = enabled) { onPick(prompt) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
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
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
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
    selecting: Boolean,
    copied: Boolean,
    onCopy: () -> Unit,
    onLongPress: () -> Unit,
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
        Surface(
            shape = shape,
            color = when {
                fromUser -> MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
                selecting -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            },
            border = if (selecting) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            } else {
                null
            },
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            val body = @Composable {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                )
            }

            if (selecting) {
                // Handles need a selection container; taps are disabled here so
                // dragging a handle can't be mistaken for a copy.
                SelectionContainer { body() }
            } else {
                Box(
                    modifier = Modifier.combinedClickable(
                        enabled = !fromUser,
                        onClick = onCopy,
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress()
                        },
                    ),
                ) { body() }
            }
        }

        if (copied || selecting) {
            Text(
                text = if (copied) "copied" else "select, then tap a draft to exit",
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
            shape = RoundedCornerShape(22.dp),
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { onFocusChanged(it.isFocused) },
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = if (canSend) 1f else 0.25f),
                )
                .clickable(enabled = canSend, onClick = onSend),
        ) {
            Text(text = "\u2191", color = Color.White, fontSize = 19.sp)
        }
    }
}

/** Jumps to the newest content from anywhere in the sheet. */
@Composable
private fun JumpToBottom(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
                Text(text = "\u2193", color = Color.White, fontSize = 18.sp)
            }
        }
    }
}

/** Ignore a few stray pixels so the button doesn't flicker at the very bottom. */
private const val JUMP_VISIBLE_SLOP = 24

private const val COPIED_HINT_MS = 1400L

private val QUICK_PROMPTS = listOf(
    "another angle",
    "make it hinglish",
    "shorter one-liner",
    "more technical",
)


/**
 * Shows what Claude actually read off the screen, next to a preview of the
 * capture itself. Tapping the preview blows it up — the fastest way to check
 * whether a capture grabbed the wrong post or clipped the content.
 */
@Composable
private fun PostContextBlock(
    context: PostContext,
    thumbnailPath: String?,
    capturePath: String?,
    onViewCapture: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (context.author.isNotEmpty()) {
                    Text(
                        text = context.author,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                listOfNotNull(
                    context.registerLabel.takeIf { it.isNotEmpty() },
                    context.language.takeIf { it.isNotEmpty() && it != "english" },
                ).forEach { tag ->
                    Text(
                        text = tag,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
            if (context.postText.isNotEmpty()) {
                Text(
                    text = context.postText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        if (capturePath != null) {
            CapturePreview(
                // Draw the small thumbnail; the full capture is only decoded
                // when the viewer actually opens.
                previewPath = thumbnailPath ?: capturePath,
                onClick = { onViewCapture(capturePath) },
            )
        }
    }
}

/** Small tappable preview of the capture, with a hint that it opens. */
@Composable
private fun CapturePreview(previewPath: String, onClick: () -> Unit) {
    val preview = rememberDecoded(previewPath, MAX_PREVIEW_PIXELS) ?: return

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(start = 10.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Image(
            bitmap = preview.asImageBitmap(),
            contentDescription = "View capture",
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier
                .size(width = 44.dp, height = 56.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Text(
            text = "view",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * Saved results, newest first. Doubles as the "this came from disk" marker and
 * as the switcher — tapping a card reopens that result, still with no API call.
 */
@Composable
private fun HistoryStrip(
    history: List<HistoryEntry>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
) {
    Column {
        Text(
            text = if (history.size > 1) {
                "Saved results · tap to switch · no new API call"
            } else {
                "Saved result · no new API call"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .padding(top = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            history.forEach { entry ->
                HistoryCard(
                    entry = entry,
                    selected = entry.savedAtMillis == selectedId,
                    onClick = { onSelect(entry.savedAtMillis) },
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(entry: HistoryEntry, selected: Boolean, onClick: () -> Unit) {
    val thumbnail = rememberDecoded(entry.thumbnailPath, MAX_PREVIEW_PIXELS)
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(enabled = !selected, onClick = onClick)
            .padding(5.dp),
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .size(width = 26.dp, height = 34.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
        }
        Column(modifier = Modifier.padding(start = if (thumbnail != null) 6.dp else 2.dp)) {
            Text(
                text = entry.author.ifEmpty { "unknown" },
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = relativeAge(entry.savedAtMillis),
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DraftCard(
    draft: Draft,
    refinements: List<Refinement>,
    onCopy: () -> Unit,
    onRefine: (Refinement) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(text = draft.angle.label, color = draft.angle.color)
                if (draft.thought.isNotEmpty()) {
                    // The one-line intent, so a draft can be judged before reading it.
                    Text(
                        text = draft.thought,
                        style = MaterialTheme.typography.labelSmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = 7.dp)
                            .weight(1f, fill = false),
                    )
                }
                if (draft.refining) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(14.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }

            Text(
                text = draft.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .clickable(enabled = !draft.refining, onClick = onCopy),
            )

            Row(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                refinements.forEach { refinement ->
                    RefinementChip(
                        label = refinement.label,
                        enabled = !draft.refining,
                        onClick = { onRefine(refinement) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun RefinementChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.4f
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = alpha))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
