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
import com.yash.feedrunner.ui.SeedStatus

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

    Column(modifier = Modifier.fillMaxSize()) {
        Header(
            pendingCount = state.pendingCount,
            serverReachable = state.serverReachable,
            onEditAddress = { showAddressDialog = true },
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

        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = actions.onRefresh,
            modifier = Modifier.weight(1f),
        ) {
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
                        SeedCard(seed = seed, onOpen = { actions.onOpenSeed(seed) })
                    }
                }
            }
        }

        BottomBar(onAddManual = { showManualDialog = true })
    }

    state.pendingDelete?.let { seed ->
        AlertDialog(
            onDismissRequest = actions.onCancelDelete,
            title = { Text("Delete this seed?") },
            text = { Text(seed.headline, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { actions.onConfirmDelete(seed) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = actions.onCancelDelete) { Text("Cancel") } },
        )
    }

    if (showManualDialog) {
        TextEntryDialog(
            title = "Add your own idea",
            label = "the idea, in your words",
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
    Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Ideas",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
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
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp),
        )
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
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
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
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (active) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

@Composable
private fun BottomBar(onAddManual: () -> Unit) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = "Tap a seed to write posts from it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onAddManual) { Text("Add mine") }
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
                Button(
                    onClick = onEditAddress,
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("Set address") }
            } else if (filtered) {
                TextButton(
                    onClick = onClearFilters,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Clear filters") }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(label) },
                    modifier = Modifier.fillMaxWidth(),
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
