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
import com.yash.feedrunner.ui.theme.MetaTextStyle

private val PanelShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

@Composable
fun ReplyPanel(
    state: PanelState,
    onDraftCopy: (Draft) -> Unit,
    onToggleUsed: (Draft) -> Unit,
    onRefine: (Draft, Refinement) -> Unit,
    onSelectResult: (savedAtMillis: Long) -> Unit,
    onSendChat: (String) -> Unit,
    onRetryChat: () -> Unit,
    onAngleBatch: (Angle) -> Unit,
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

    // The jump button and the chat send button both live in the bottom-right
    // corner, so the jump button has to get out of the way while you are typing.
    var chatFocused by remember { mutableStateOf(false) }

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
                        onToggleUsed = onToggleUsed,
                        onRefine = onRefine,
                        onSelectResult = onSelectResult,
                        onViewCapture = { viewerPath = it },
                        onSendChat = onSendChat,
                        onRetryChat = onRetryChat,
                        onAngleBatch = onAngleBatch,
                        onCopyText = onCopyText,
                        onChatFocusChanged = { focused ->
                            chatFocused = focused
                            onChatFocusChanged(focused)
                        },
                        onScrollState = { bodyScroll = it },
                    )
                }
              }

              bodyScroll?.let { scroll ->
                  JumpToBottom(
                      visible = !chatFocused && scroll.maxValue > 0 &&
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
    onToggleUsed: (Draft) -> Unit,
    onRefine: (Draft, Refinement) -> Unit,
    onSelectResult: (savedAtMillis: Long) -> Unit,
    onViewCapture: (path: String) -> Unit,
    onSendChat: (String) -> Unit,
    onRetryChat: () -> Unit,
    onAngleBatch: (Angle) -> Unit,
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

    FollowChatGrowth(scrollState, state.chat.size, resultKey)

    onScrollState(scrollState)

    // Which draft last got copied, so the confirmation lands on that card rather
    // than as a toast that covers the next one.
    var copiedId by remember(resultKey) { mutableIntStateOf(-1) }
    LaunchedEffect(copiedId) {
        if (copiedId >= 0) {
            delay(COPIED_HINT_MS)
            copiedId = -1
        }
    }

    val scope = rememberCoroutineScope()

    Box {
        Column(
            modifier = Modifier.verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    copied = copiedId == draft.id,
                    onCopy = {
                        onDraftCopy(draft)
                        copiedId = draft.id
                    },
                    onToggleUsed = { onToggleUsed(draft) },
                    onRefine = { refinement -> onRefine(draft, refinement) },
                )
            }

            Text(
                text = "Tap a draft to copy it. The panel stays open.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChatThread(
                chat = state.chat,
                pending = state.chatPending,
                quickPrompts = REPLY_QUICK_PROMPTS,
                title = "More replies, or ask for anything",
                error = state.chatError,
                onCopyText = onCopyText,
                onSend = onSendChat,
                onRetry = onRetryChat,
                onFocusChanged = onChatFocusChanged,
                angles = BATCH_ANGLES,
                onAngleBatch = onAngleBatch,
            )
        }

        // Once the post scrolls away there is nothing on screen saying who you are
        // replying to, which is easy to lose track of with several results saved.
        AnimatedVisibility(
            visible = scrollState.value > STICKY_HEADER_AFTER_PX,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            StickyContextBar(
                context = state.postContext,
                onTap = { scope.launch { scrollState.animateScrollTo(0) } },
            )
        }
    }
}

/**
 * Condensed stand-in for the post context, overlaid while the real one is scrolled
 * out of view. Tapping it returns to the top rather than being decoration.
 */
@Composable
private fun StickyContextBar(context: PostContext, onTap: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 3.dp,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            Text(
                text = context.author,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = " · ${context.registerLabel}",
                style = MetaTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                text = "↑ top",
                style = MetaTextStyle,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}



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
    copied: Boolean,
    onCopy: () -> Unit,
    onToggleUsed: () -> Unit,
    onRefine: (Refinement) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    // Per card, so opening the chips on one draft does not shuffle the others.
    var showRefinements by remember { mutableStateOf(false) }
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
                            .weight(1f),
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
                UsedMarker(used = draft.used, onClick = onToggleUsed)
            }

            Text(
                text = draft.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .clickable(enabled = !draft.refining) {
                        // A tap that puts something on the clipboard should be felt,
                        // since the visual change is small and easy to miss.
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCopy()
                    },
            )

            AnimatedVisibility(visible = copied) {
                Text(
                    text = "copied, paste in X",
                    style = MetaTextStyle,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Text(
                text = if (showRefinements) "refine ▴" else "refine ▾",
                style = MetaTextStyle,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showRefinements = !showRefinements }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )

            AnimatedVisibility(visible = showRefinements || draft.refining) {
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp)
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
}

/**
 * Marks a draft as the one you sent. Copying sets it automatically, so the tap here
 * is mostly for undoing that, or for a draft you sent without copying.
 */
@Composable
private fun UsedMarker(used: Boolean, onClick: () -> Unit) {
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

/** Roughly the height of the history strip plus the post block. */
private const val STICKY_HEADER_AFTER_PX = 220

/** Same green as a posted seed, so "done with this" looks the same everywhere. */
internal val UsedGreen = Color(0xFF00BA7C)
