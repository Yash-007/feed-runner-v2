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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.yash.feedrunner.ui.PostIdea
import com.yash.feedrunner.ui.StoredSeed
import com.yash.feedrunner.ui.Platform
import com.yash.feedrunner.ui.SeedLane
import com.yash.feedrunner.ui.SeedStatus
import com.yash.feedrunner.ui.theme.SegmentedControl
import com.yash.feedrunner.ui.theme.VerticalHairline
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.layout.navigationBarsPadding
import com.yash.feedrunner.ui.theme.WashHeader
import com.yash.feedrunner.ui.theme.pressClickable
import com.yash.feedrunner.ui.theme.Space
import com.yash.feedrunner.ui.theme.SecondaryButton
import com.yash.feedrunner.ui.theme.Radius
import com.yash.feedrunner.ui.theme.PrimaryButton
import com.yash.feedrunner.ui.theme.Hairline
import androidx.compose.foundation.BorderStroke

/**
 * The Idea Bank.
 *
 * Seeds arrive on their own as a side effect of generating replies and posts, so
 * this screen is mostly for reading. The two jobs it has to make easy are finding
 * the seed worth writing about, and turning a few of them into post ideas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeasScreen(state: IdeasUiState, actions: IdeasActions) {
    var showManualDialog by remember { mutableStateOf(false) }
    var showAddressDialog by remember { mutableStateOf(false) }

    // The whole screen is the pull target, not just the list. Wrapping only the list
    // put the indicator behind the filter chips, where it was invisible and the
    // gesture had to start halfway down the screen to work at all.
    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = actions.onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
    Column(modifier = Modifier.fillMaxSize()) {
        Header(
            pendingCount = state.pendingCount,
            bubbleRunning = state.bubbleRunning,
            onToggleBubble = actions.onToggleBubble,
            onOpenSetup = actions.onOpenSetup,
        )

        StreakCard(
            streak = state.streak,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )

        // The one split that changes what you are looking at, rather than
        // narrowing it, so it sits above the chips and looks like a control
        // instead of another filter.
        if (state.showLanes) {
            LaneTabs(
                lane = state.lane,
                counts = state.laneCounts,
                onLane = actions.onLaneChange,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = Space.xs),
            )
        }

        FilterBar(
            status = state.filter,
            tag = state.tagFilter,
            tags = state.availableTags,
            platform = state.platformFilter,
            platforms = state.presentPlatforms,
            onStatus = actions.onFilterChange,
            onTag = actions.onTagFilterChange,
            onPlatform = actions.onPlatformFilterChange,
        )

        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (state.visibleSeeds.isEmpty() && !state.loading) {
                EmptyState(
                    configured = state.backendConfigured,
                    filtered = state.anyFilterActive,
                    lane = state.lane,
                    onEditAddress = { showAddressDialog = true },
                    onClearFilters = actions.onClearFilters,
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.visibleSeeds, key = { it.key }) { seed ->
                        SeedCard(
                            seed = seed,
                            onOpen = { actions.onOpenSeed(seed) },
                            onDelete = { actions.onAskDelete(seed) },
                            onOpenLink = actions.onOpenLink,
                        )
                    }
                }
            }
        }

        BottomBar(onAddManual = { showManualDialog = true })
    }
    }

    if (showManualDialog) {
        TextEntryDialog(
            title = "Add your own idea",
            supporting = "It joins the bank, and opens so you can generate from it.",
            label = "the post you want to write",
            confirm = "Add",
            minLines = 5,
            onDismiss = { showManualDialog = false },
            onConfirm = {
                actions.onAddManual(it)
                showManualDialog = false
            },
        )
    }

    if (showAddressDialog) {
        TextEntryDialog(
            title = "Backend address",
            label = "10.0.0.5:8080",
            initial = state.baseUrl,
            confirm = "Save",
            supporting = "Printed in the server's startup log. Changes with the network.",
            onDismiss = { showAddressDialog = false },
            onConfirm = {
                actions.onSetBaseUrl(it)
                showAddressDialog = false
            },
        )
    }
}

/**
 * The home header. Ideas is the screen you sit down with, so the things that used
 * to live on a separate front page — is the bubble on, is everything set up —
 * live here now, as two small controls beside the title.
 *
 * Kept to one row plus an occasional sync line: this header and the streak card
 * were eating the space the seeds need.
 */
@Composable
private fun Header(
    pendingCount: Int,
    bubbleRunning: Boolean,
    onToggleBubble: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    WashHeader(padStatusBar = true) {
      Column(
          modifier = Modifier.padding(
              start = Space.lg,
              end = Space.sm,
              top = Space.md,
              bottom = Space.md,
          ),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Ideas",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            // The bubble is the app's whole point, so its switch lives on the
            // home screen: a dot for its state, one tap to flip it.
            HeaderPill(
                label = "bubble",
                dotColor = if (bubbleRunning) {
                    Color(0xFF00BA7C)
                } else {
                    MaterialTheme.colorScheme.outline
                },
                onClick = onToggleBubble,
            )
            // Permissions, voice rules and appearance moved one tap away. The
            // server dot that used to sit here retired with the laptop backend:
            // the deployed address is the default, and failures still surface
            // through the message line and the sync counter.
            Text(
                text = "⚙",
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenSetup)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        // Only sync trouble earns a second line; the happy path stays quiet.
        if (pendingCount > 0) {
            Text(
                text = "$pendingCount waiting to sync · pull down to retry",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xs),
            )
        }
      }
    }
}

/** A dot-and-word control in the header: state at a glance, action on tap. */
@Composable
private fun HeaderPill(label: String, dotColor: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}

/**
 * All / Harvested / From me.
 *
 * The count rides in the label rather than as a badge: at this size a badge is
 * a dot you cannot read, and the number is the actual reason to glance here
 * ("the engine found nine overnight"). The active tab drops its count, because
 * you are already looking at the list it describes.
 */
@Composable
private fun LaneTabs(
    lane: SeedLane,
    counts: Map<SeedLane, Int>,
    onLane: (SeedLane) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedControl(
        options = SeedLane.entries.toList(),
        selected = lane,
        label = { entry ->
            val count = counts[entry] ?: 0
            if (entry == lane || count == 0) entry.label else "${entry.label}  $count"
        },
        onSelect = onLane,
        textStyle = MaterialTheme.typography.labelMedium,
        // A lane with nothing in it still reads, just quietly: it says the
        // engine has not run yet rather than that the tab is broken.
        optionDimmed = { (counts[it] ?: 0) == 0 },
        modifier = modifier,
    )
}

/**
 * Status, platform and topic, in one row that scrolls.
 *
 * These were three stacked rows. With the lane tabs above them that put four
 * rows of controls between the header and the first idea, about a third of the
 * screen, and the bank stopped reading as a list of ideas and started reading
 * as a filter panel. The same complaint the seed cards got, one level up.
 *
 * Status leads because it is the one used daily. Platform only appears with
 * both networks in the bank. Topics fold behind a chip, because there are up to
 * eight of them and they are for hunting something specific, not for glancing
 * at; the chip carries the active one so a filter is never hidden while it is
 * doing something.
 */
@Composable
private fun FilterBar(
    status: SeedStatus?,
    tag: String?,
    tags: List<String>,
    platform: Platform?,
    platforms: List<Platform>,
    onStatus: (SeedStatus?) -> Unit,
    onTag: (String?) -> Unit,
    onPlatform: (Platform?) -> Unit,
) {
    var topicsOpen by remember { mutableStateOf(false) }
    // An active tag has to stay visible even if the row is folded away.
    val showTopics = topicsOpen || tag != null

    Column {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(label = "all", active = status == null, onClick = { onStatus(null) })
            SeedStatus.entries.forEach { entry ->
                FilterChip(
                    label = entry.label,
                    active = status == entry,
                    onClick = { onStatus(entry) },
                )
            }

            // Hidden until the bank holds seeds from both networks; a filter
            // with one possible answer is noise.
            if (platforms.size > 1) {
                VerticalHairline(height = 18.dp, modifier = Modifier.padding(horizontal = Space.xs))
                platforms.forEach { entry ->
                    val active = platform == entry
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) Color.White else entry.hue,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.chip))
                            .background(
                                if (active) entry.hue else entry.hue.copy(alpha = 0.12f),
                            )
                            .clickable { onPlatform(if (active) null else entry) }
                            .padding(horizontal = Space.md, vertical = Space.sm),
                    )
                }
            }

            if (tags.size > 1) {
                VerticalHairline(height = 18.dp, modifier = Modifier.padding(horizontal = Space.xs))
                Text(
                    text = when {
                        tag != null -> "$tag  ✕"
                        topicsOpen -> "topics  ▴"
                        else -> "topics  ▾"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (tag != null) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.chip))
                        .background(
                            if (tag != null) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            },
                        )
                        .clickable {
                            // Tapping the chip while a tag is on clears it,
                            // which is what the ✕ is promising.
                            if (tag != null) onTag(null) else topicsOpen = !topicsOpen
                        }
                        .padding(horizontal = Space.md, vertical = Space.sm),
                )
            }
        }

        AnimatedVisibility(visible = showTopics && tags.size > 1) {
            Row(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tags.forEach { entry ->
                    TagChip(
                        label = entry,
                        active = tag == entry,
                        onClick = { onTag(if (tag == entry) null else entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    // Solid violet when it is on, a hairline outline when it is not. Both states
    // occupy the same box, so switching between them does not shift the row.
    val shape = RoundedCornerShape(Radius.chip)
    Surface(
        shape = shape,
        color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
        border = if (active) {
            null
        } else {
            BorderStroke(Space.hair, MaterialTheme.colorScheme.outlineVariant)
        },
        modifier = Modifier.pressClickable(pressedScale = 0.94f, onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
        )
    }
}

@Composable
private fun TagChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = if (active) "$label ✕" else label,
        fontSize = 11.sp,
        color = if (active) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.primary
        },
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.chip))
            .background(
                if (active) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Space.sm, vertical = Space.xs),
    )
}

@Composable
private fun BottomBar(onAddManual: () -> Unit) {
    // Flat, separated by a line rather than a shadow, like everything else now.
    Column(modifier = Modifier.fillMaxWidth()) {
        Hairline()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding()
                .padding(horizontal = Space.lg, vertical = Space.md),
        ) {
            Text(
                text = "Tap a seed to write from it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = Space.md),
            )
            // Your own ideas belong in the same bank as the captured ones, so this
            // sits next to them rather than behind a menu.
            PrimaryButton(label = "+  My own idea", onClick = onAddManual)
        }
    }
}

@Composable
private fun EmptyState(
    configured: Boolean,
    filtered: Boolean,
    lane: SeedLane,
    onEditAddress: () -> Unit,
    onClearFilters: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // The zero state gets the full anatomy — mark, serif title, one
            // line, one action — instead of a lone sentence in dead air.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(
                        com.yash.feedrunner.R.drawable.ic_brand_general,
                    ),
                    contentDescription = null,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                        MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = when {
                    !configured -> "No backend yet"
                    lane == SeedLane.HARVESTED -> "Nothing harvested yet"
                    filtered -> "Nothing matches"
                    else -> "No seeds yet"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = Space.lg),
            )
            Text(
                text = when {
                    !configured -> "Set the backend address to start banking ideas."
                    // The harvested lane fills itself on a schedule, so an
                    // empty one is a waiting state, not something to fix here.
                    lane == SeedLane.HARVESTED ->
                        "The harvesting engine files these while it reads your feed. " +
                            "Nothing has landed yet, so check back after its next run."
                    filtered -> "These filters hide everything in the bank."
                    else -> "Seeds save themselves when a post is worth building on. " +
                        "Capture something, or add your own below."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = Space.sm),
            )
            if (!configured) {
                PrimaryButton(
                    label = "Set address",
                    modifier = Modifier.padding(top = Space.lg),
                    onClick = onEditAddress,
                )
            } else if (filtered) {
                SecondaryButton(
                    // Same action either way; the wording matches what the
                    // person is actually looking at, so the button is not
                    // offering to clear filters they never set.
                    label = if (lane == SeedLane.HARVESTED) {
                        "Show all seeds"
                    } else {
                        "Clear filters"
                    },
                    modifier = Modifier.padding(top = Space.md),
                    onClick = onClearFilters,
                )
            }
        }
    }
}

@Composable
private fun TextEntryDialog(
    title: String,
    label: String,
    confirm: String,
    initial: String = "",
    supporting: String? = null,
    /** More than one for prose (an idea); one for identifiers (an address). */
    minLines: Int = 1,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    // The only thing to do in this dialog is type, so start there rather than
    // making the first tap be on the field.
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(label) },
                    minLines = minLines,
                    maxLines = (minLines * 2).coerceAtLeast(1),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        // Prose keeps its return key; a one-liner submits on Done.
                        imeAction = if (minLines > 1) ImeAction.Default else ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (text.isNotBlank()) onConfirm(text) },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(field),
                )
                supporting?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(confirm)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Asked before a seed is deleted, from the list or from inside its thread.
 *
 * Hosted by the activity rather than either screen: it used to live in the list,
 * which is not composed while a thread is open, so "delete seed" in a thread put
 * up no question and did nothing at all.
 */
@Composable
internal fun ConfirmDeleteSeedDialog(
    seed: StoredSeed,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Delete this seed?") },
        text = { Text(seed.headline, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
