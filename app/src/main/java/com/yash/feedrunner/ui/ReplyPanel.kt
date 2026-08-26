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
import androidx.compose.foundation.border
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
import androidx.compose.material3.surfaceColorAtElevation
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
import com.yash.feedrunner.ui.theme.SoftAccentChip
import com.yash.feedrunner.ui.theme.HairlineCard
import com.yash.feedrunner.ui.theme.Motion
import com.yash.feedrunner.ui.theme.PrimaryButton
import com.yash.feedrunner.ui.theme.WashHeader
import com.yash.feedrunner.ui.theme.Radius
import com.yash.feedrunner.ui.theme.Space
import com.yash.feedrunner.ui.theme.pressClickable
import com.yash.feedrunner.ui.theme.SegmentedControl
import com.yash.feedrunner.ui.theme.shimmer
import com.yash.feedrunner.ui.theme.WordLimitSlider
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.height
import com.yash.feedrunner.ui.theme.VerticalHairline

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
    onSwitchPlatform: (Platform) -> Unit,
    draftLimit: Int,
    onDraftLimit: (Int) -> Unit,
    chatLimit: Int,
    onChatLimit: (Int) -> Unit,
    onRegenerate: () -> Unit,
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

    // The sheet rises rather than appearing: the window itself cannot animate,
    // so the entrance lives on the content.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val scrim by animateFloatAsState(
        targetValue = if (entered) 0.45f else 0f,
        animationSpec = tween(200),
        label = "panelScrim",
    )

    // Scrim: tapping outside the sheet dismisses.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrim))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = entered,
            enter = slideInVertically(
                animationSpec = Motion.enter(),
                initialOffsetY = { it / 3 },
            ) + fadeIn(tween(150)),
        ) {
        Surface(
            shape = PanelShape,
            color = MaterialTheme.colorScheme.surface,
            // Shadow, not tonal elevation. Tonal tints the surface with the accent,
            // which on a white surface reads as a lavender cast over every draft.
            // The sheet does genuinely float over another app, so it earns a shadow
            // even though nothing inside it has one.
            tonalElevation = 0.dp,
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .imePadding()
                // Swallow taps on the sheet so they don't reach the scrim.
                .clickable(enabled = false) {},
        ) {
            // One host for the whole sheet: the drafts, the chat bubbles and the
            // composer all need the same toolbar, and an overlay window gets none
            // from the platform.
            SelectionActionsHost {
              Box {
              Column {
                // A band, not a field. The drafts below stay on plain surface: they
                // are read over whatever X is showing and need all their contrast.
                WashHeader {
                    PanelHeader(
                        platform = (state as? PanelState.Ready)?.platform,
                        savedPlatforms = (state as? PanelState.Ready)?.let { ready ->
                            ready.history.map { it.platform }.toSet() + ready.platform
                        } ?: emptySet(),
                        onSwitchPlatform = onSwitchPlatform,
                        onDismiss = onDismiss,
                    )
                }

                Column(modifier = Modifier.padding(Space.lg)) {
                when (state) {
                    is PanelState.Loading -> LoadingBody()
                    is PanelState.Error -> ErrorBody(state.message, onRetry)
                    is PanelState.Ready -> ReadyBody(
                        state = state,
                        draftLimit = draftLimit,
                        onDraftLimit = onDraftLimit,
                        chatLimit = chatLimit,
                        onChatLimit = onChatLimit,
                        onRegenerate = onRegenerate,
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
              }

              // Same story as the compose sheet: this window reports no insets, the
              // keyboard resizes it instead, and the resize lands after the focus
              // event. Following maxValue keeps the composer you are typing in from
              // being cut off by the new window edge.
              bodyScroll?.let { scroll ->
                  LaunchedEffect(chatFocused, scroll.maxValue) {
                      if (chatFocused) scroll.animateScrollTo(scroll.maxValue)
                  }

                  JumpToBottom(
                      visible = !chatFocused && scroll.maxValue > 0 &&
                          scroll.value < scroll.maxValue - JUMP_VISIBLE_SLOP,
                      onClick = { scope.launch { scroll.animateScrollTo(scroll.maxValue) } },
                      modifier = Modifier.align(Alignment.BottomEnd),
                  )
              }
              }
            }
        }
        }

        viewerPath?.let { path ->
            CaptureViewer(path = path, onDismiss = { viewerPath = null })
        }
    }
}

@Composable
private fun PanelHeader(
    platform: Platform?,
    savedPlatforms: Set<Platform>,
    onSwitchPlatform: (Platform) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
    // The sheet grammar every bottom sheet teaches: this is a surface that rose
    // from the bottom, and the scrim above it is tappable.
    Box(
        modifier = Modifier
            .padding(top = Space.sm)
            .size(width = 36.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
            .align(Alignment.CenterHorizontally),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.lg, end = Space.sm, bottom = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Feed Runner",
            // A notch under headlineSmall: the row now also holds the platform
            // tabs, and the wordmark yields before Close ever clips.
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 19.sp),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Box(modifier = Modifier.weight(1f))
        // The platform tabs, same icons as the bubble menu. Navigation: tapping
        // another network jumps to your newest saved result for it, free, like
        // the rail below. Absent while loading.
        platform?.let { current ->
            SegmentedControl(
                options = Platform.entries.toList(),
                selected = current,
                label = { it.label },
                onSelect = onSwitchPlatform,
                thumbColor = current.hue,
                onThumbColor = Color.White,
                iconRes = {
                    when (it) {
                        Platform.X -> com.yash.feedrunner.R.drawable.ic_brand_x
                        Platform.LINKEDIN -> com.yash.feedrunner.R.drawable.ic_brand_linkedin
                        Platform.GENERAL -> com.yash.feedrunner.R.drawable.ic_brand_general
                    }
                },
                // A network with nothing saved reads disabled; the tap still
                // answers with the toast that says why.
                optionDimmed = { it !in savedPlatforms },
                modifier = Modifier
                    .padding(start = Space.sm)
                    .width(136.dp),
            )
        }
        Text(
            text = "Close",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.chip))
                .clickable(onClick = onDismiss)
                .padding(horizontal = Space.md, vertical = Space.sm),
        )
    }
    }
}

/**
 * Skeleton drafts plus a status line that moves through the actual stages.
 *
 * The shape of what is coming beats a spinner: it promises drafts specifically,
 * and the changing line keeps a slow backend from reading as a hang.
 */
@Composable
private fun LoadingBody() {
    var stage by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1700)
            stage = (stage + 1).coerceAtMost(LOADING_STAGES.lastIndex)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 2.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            androidx.compose.animation.Crossfade(
                targetState = stage,
                animationSpec = tween(300),
                label = "loadingStage",
            ) { index ->
                Text(
                    text = LOADING_STAGES[index],
                    modifier = Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        repeat(2) { SkeletonDraftCard() }

        // True, and worth saying: the job runs in AnalysisManager, not in this
        // sheet, so closing costs nothing — a toast and the bubble's badge
        // announce the drafts when they land.
        Text(
            text = "Safe to close — we'll nudge you when they're ready.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.xs, bottom = Space.sm),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

private val LOADING_STAGES = listOf(
    "Reading the post…",
    "Picking the angles…",
    "Writing drafts…",
    "Almost there…",
)

/** The outline of a draft card, shimmering until the real one lands. */
@Composable
internal fun SkeletonDraftCard() {
    HairlineCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Space.lg)) {
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 18.dp)
                    .clip(RoundedCornerShape(Radius.chip))
                    .shimmer(),
            )
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer(),
            )
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth(0.72f)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer(),
            )
        }
    }
}

@Composable
private fun ErrorBody(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton(
            label = "Try again",
            modifier = Modifier.padding(top = Space.lg),
            onClick = onRetry,
        )
    }
}

@Composable
private fun ReadyBody(
    state: PanelState.Ready,
    draftLimit: Int,
    onDraftLimit: (Int) -> Unit,
    chatLimit: Int,
    onChatLimit: (Int) -> Unit,
    onRegenerate: () -> Unit,
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

            // Everything above the chat in one container: the post you are replying
            // to and the drafts themselves are text you may want part of, not just
            // all of. The chat below keeps its own container, since containers
            // cannot nest.
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

            // This cap owns the draft cards: the next capture, regenerate, and
            // the refine chips. The composer below keeps its own — a tight
            // batch with a looser chat is a real combination.
            Column {
                WordLimitSlider(
                    value = draftLimit,
                    range = 10..60,
                    step = 5,
                    onValueChange = onDraftLimit,
                )
                if (state.capturePath != null) {
                    Text(
                        text = "↻  regenerate drafts",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = Space.xs)
                            .pressClickable(pressedScale = 0.94f, onClick = onRegenerate)
                            .clip(RoundedCornerShape(Radius.chip))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                            .padding(horizontal = Space.md, vertical = Space.sm),
                    )
                }
            }

            Text(
                text = "Tap a draft to copy it, long press to select part of it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

                    ChatHistory(
                        chat = state.chat,
                        pending = state.chatPending,
                        title = "More replies, or ask for anything",
                        error = state.chatError,
                        onCopyText = onCopyText,
                        onRetry = onRetryChat,
                    )
                }
            }

            // The chat's own cap: angle batches and answers, independent of
            // the draft cards' slider above.
            WordLimitSlider(
                value = chatLimit,
                range = 10..60,
                step = 5,
                onValueChange = onChatLimit,
            )

            ChatComposer(
                pending = state.chatPending,
                quickPrompts = REPLY_QUICK_PROMPTS,
                onSend = onSendChat,
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
    // An opaque band behind the pill, matching the sheet. Without it the drafts
    // scrolled visibly through the gap around the pill's rounded corners, which
    // read as a rendering fault rather than a header.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(bottom = 6.dp),
    ) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
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
        // A rail rather than a row of tiles: one bordered track, entries split by
        // vertical hairlines, running off the right edge so it is obvious there is
        // more without needing a scrollbar. Straight out of the reference.
        val railShape = RoundedCornerShape(Radius.card)
        Row(
            modifier = Modifier
                .padding(top = Space.sm)
                .clip(railShape)
                .border(Space.hair, MaterialTheme.colorScheme.outlineVariant, railShape)
                .horizontalScroll(rememberScrollState()),
        ) {
            history.forEachIndexed { index, entry ->
                if (index > 0) VerticalHairline(height = RAIL_HEIGHT)
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
    // Inside the rail the entries have no border of their own, so the current one
    // is marked by a tint rather than by an outline.
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        Color.Transparent
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(RAIL_HEIGHT)
            .background(background)
            .clickable(enabled = !selected, onClick = onClick)
            .padding(horizontal = Space.sm),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The brand mark, in its hue, so a LinkedIn comment and an X
                // reply stop looking interchangeable in the rail.
                Image(
                    painter = androidx.compose.ui.res.painterResource(
                        when (entry.platform) {
                            Platform.X -> com.yash.feedrunner.R.drawable.ic_brand_x
                            Platform.LINKEDIN -> com.yash.feedrunner.R.drawable.ic_brand_linkedin
                            Platform.GENERAL -> com.yash.feedrunner.R.drawable.ic_brand_general
                        },
                    ),
                    contentDescription = entry.platform.label,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                        entry.platform.hue,
                    ),
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(11.dp),
                )
                Text(
                    text = entry.author.ifEmpty { "unknown" },
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
    HairlineCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Space.lg)) {
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

            AnimatedVisibility(
                visible = copied,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut(),
            ) {
                Text(
                    text = "✓ copied — paste it in",
                    style = MetaTextStyle,
                    fontWeight = FontWeight.SemiBold,
                    color = UsedGreen,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Text(
                text = if (showRefinements) "refine ▴" else "refine ▾",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(Radius.chip))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .clickable { showRefinements = !showRefinements }
                    .padding(horizontal = Space.sm, vertical = Space.xs),
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
    // A pale wash of the angle's own hue rather than a solid block of it. The hue
    // is still doing the work of telling the takes apart, just at a volume that
    // suits everything around it.
    SoftAccentChip(text = text.lowercase(), hue = color)
}

@Composable
private fun RefinementChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.4f
    val shape = RoundedCornerShape(Radius.chip)
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        modifier = Modifier
            .pressClickable(enabled = enabled, pressedScale = 0.94f, onClick = onClick)
            .clip(shape)
            .border(Space.hair, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = Space.md, vertical = Space.sm),
    )
}

/** Fixed so the dividing hairlines run the full height of the rail. */
private val RAIL_HEIGHT = 52.dp

/** Roughly the height of the history strip plus the post block. */
private const val STICKY_HEADER_AFTER_PX = 220

/** Same green as a posted seed, so "done with this" looks the same everywhere. */
internal val UsedGreen = Color(0xFF00BA7C)
