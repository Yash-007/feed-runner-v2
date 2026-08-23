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
import com.yash.feedrunner.ui.SeedStatus
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import com.yash.feedrunner.ui.theme.WashHeader
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
            serverReachable = state.serverReachable,
            onEditAddress = { showAddressDialog = true },
        )

        StreakCard(
            streak = state.streak,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )

        FilterBar(
            status = state.filter,
            tag = state.tagFilter,
            tags = state.availableTags,
            onStatus = actions.onFilterChange,
            onTag = actions.onTagFilterChange,
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
                    filtered = state.filter != null || state.tagFilter != null,
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

@Composable
private fun Header(pendingCount: Int, serverReachable: Boolean?, onEditAddress: () -> Unit) {
    WashHeader {
      Column(
          modifier = Modifier.padding(
              start = Space.lg,
              end = Space.sm,
              top = Space.xl,
              bottom = Space.lg,
          ),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Ideas",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            // A dot beats a word: the only thing worth knowing at a glance is
            // whether the laptop is answering.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onEditAddress)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            when (serverReachable) {
                                true -> Color(0xFF00BA7C)
                                false -> MaterialTheme.colorScheme.error
                                null -> MaterialTheme.colorScheme.outline
                            },
                        ),
                )
                Text(
                    text = "server",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        Text(
            text = if (pendingCount > 0) {
                "$pendingCount waiting to sync · pull down to retry"
            } else {
                "Saved from your replies and posts · pull down to refresh"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.xs),
        )
      }
    }
}

@Composable
private fun FilterBar(
    status: SeedStatus?,
    tag: String?,
    tags: List<String>,
    onStatus: (SeedStatus?) -> Unit,
    onTag: (String?) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            FilterChip(label = "all", active = status == null, onClick = { onStatus(null) })
            SeedStatus.entries.forEach { entry ->
                FilterChip(
                    label = entry.label,
                    active = status == entry,
                    onClick = { onStatus(entry) },
                )
            }
        }
        // Tags only appear once there is more than one to choose between.
        if (tags.size > 1) {
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
        modifier = Modifier.clickable(onClick = onClick),
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
            Text(
                text = when {
                    !configured -> "Set the backend address to start banking ideas."
                    filtered -> "Nothing matches these filters."
                    else -> "No seeds yet. They save themselves when a post is worth building on."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!configured) {
                PrimaryButton(
                    label = "Set address",
                    modifier = Modifier.padding(top = Space.lg),
                    onClick = onEditAddress,
                )
            } else if (filtered) {
                SecondaryButton(
                    label = "Clear filters",
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
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
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
