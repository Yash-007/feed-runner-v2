package com.yash.feedrunner.ui.ideas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yash.feedrunner.ui.PostIdea
import com.yash.feedrunner.ui.SeedStatus
import com.yash.feedrunner.ui.StoredSeed
import com.yash.feedrunner.ui.relativeAge

/**
 * The Idea Bank.
 *
 * Seeds arrive on their own as a side effect of generating replies and posts, so
 * this screen is mostly for reading: pick the ones worth writing about, ask for
 * post ideas, and mark off what has been used.
 */
@Composable
fun IdeasScreen(state: IdeasUiState, actions: IdeasActions) {
    var showManualDialog by remember { mutableStateOf(false) }
    var showAddressDialog by remember { mutableStateOf(false) }
    var steer by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Header(
            pendingCount = state.pendingCount,
            onRefresh = actions.onRefresh,
            onEditAddress = { showAddressDialog = true },
        )

        FilterRow(selected = state.filter, onSelect = actions.onFilterChange)

        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                state.loading && state.seeds.isEmpty() -> CenterSpinner()
                state.seeds.isEmpty() -> EmptyState(
                    configured = state.backendConfigured,
                    onEditAddress = { showAddressDialog = true },
                )
                else -> SeedList(
                    seeds = state.seeds,
                    selected = state.selectedIds,
                    onToggle = actions.onToggleSelect,
                    onStatus = actions.onSetStatus,
                )
            }
        }

        if (state.ideas.isNotEmpty()) {
            IdeasResult(ideas = state.ideas, onCopy = actions.onCopy, onClear = actions.onClearIdeas)
        }

        BottomBar(
            selectedCount = state.selectedIds.size,
            generating = state.generating,
            steer = steer,
            onSteerChange = { steer = it },
            onGenerate = { actions.onGenerate(steer) },
            onAddManual = { showManualDialog = true },
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
private fun Header(pendingCount: Int, onRefresh: () -> Unit, onEditAddress: () -> Unit) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Ideas",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEditAddress) { Text("Server") }
            TextButton(onClick = onRefresh) { Text("Refresh") }
        }
        Text(
            text = if (pendingCount > 0) {
                "$pendingCount waiting to sync"
            } else {
                "Seeds saved from your replies and posts"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilterRow(selected: SeedStatus?, onSelect: (SeedStatus?) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(label = "all", active = selected == null, onClick = { onSelect(null) })
        SeedStatus.entries.forEach { status ->
            FilterChip(
                label = status.label,
                active = selected == status,
                onClick = { onSelect(status) },
            )
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
            color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun SeedList(
    seeds: List<StoredSeed>,
    selected: Set<String>,
    onToggle: (StoredSeed) -> Unit,
    onStatus: (StoredSeed, SeedStatus) -> Unit,
) {
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, bottom = 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(seeds, key = { it.clientSeedId.ifEmpty { it.remoteId.orEmpty() } }) { seed ->
            SeedCard(
                seed = seed,
                selected = seed.remoteId in selected,
                onToggle = { onToggle(seed) },
                onStatus = { onStatus(seed, it) },
            )
        }
    }
}

@Composable
private fun SeedCard(
    seed: StoredSeed,
    selected: Boolean,
    onToggle: () -> Unit,
    onStatus: (SeedStatus) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectionDot(selected = selected)
                Tag(text = seed.source.label, color = MaterialTheme.colorScheme.primary)
                Tag(text = seed.status.label, color = seed.status.color)
                if (seed.seed.shelfLife.isNotBlank()) {
                    Tag(
                        text = seed.seed.shelfLife,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(modifier = Modifier.weight(1f))
                Text(
                    text = if (seed.isPending) "syncing" else relativeAge(seed.createdAtMillis),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = seed.headline,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp),
            )

            if (seed.note.isBlank() && seed.seed.angleHint.isNotBlank() &&
                seed.seed.tension.isNotBlank()
            ) {
                Text(
                    text = seed.seed.angleHint,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (seed.seed.themeTags.isNotEmpty()) {
                Text(
                    text = seed.seed.themeTags.joinToString(" · "),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            if (seed.postAuthor.isNotBlank()) {
                Text(
                    text = "from ${seed.postAuthor}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            // A pending seed has no server id, so there is nothing to PATCH yet.
            if (!seed.isPending) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    SeedStatus.entries.filter { it != seed.status }.forEach { status ->
                        OutlinedButton(
                            onClick = { onStatus(status) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 12.dp, vertical = 2.dp,
                            ),
                        ) {
                            Text(text = "mark ${status.label}", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionDot(selected: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(end = 8.dp)
            .size(16.dp)
            .clip(CircleShape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                },
            ),
    ) {
        if (selected) Text(text = "✓", color = Color.White, fontSize = 9.sp)
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        modifier = Modifier
            .padding(end = 5.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun IdeasResult(ideas: List<PostIdea>, onCopy: (String) -> Unit, onClear: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            // Capped and scrolled internally: a thread idea can run long, and
            // without this the results push the seed list off the screen.
            .heightIn(max = 320.dp),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${ideas.size} post ideas · tap to copy",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) { Text("Clear") }
            }
            ideas.forEach { idea ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onCopy(listOf(idea.hook, idea.body).filter(String::isNotBlank)
                                .joinToString("\n\n"))
                        }
                        .padding(vertical = 8.dp),
                ) {
                    if (idea.format.isNotBlank()) {
                        Text(
                            text = idea.format,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(text = idea.hook, style = MaterialTheme.typography.bodyLarge)
                    if (idea.body.isNotBlank()) {
                        Text(
                            text = idea.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (idea.whyNow.isNotBlank()) {
                        Text(
                            text = "why now: ${idea.whyNow}",
                            fontSize = 10.sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun BottomBar(
    selectedCount: Int,
    generating: Boolean,
    steer: String,
    onSteerChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onAddManual: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (selectedCount > 0) {
                OutlinedTextField(
                    value = steer,
                    onValueChange = onSteerChange,
                    placeholder = { Text("optional: what you want from this round") },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(18.dp),
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onAddManual) { Text("Add mine") }
                Button(
                    onClick = onGenerate,
                    enabled = selectedCount > 0 && !generating,
                    modifier = Modifier.weight(1f),
                ) {
                    if (generating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Text(
                            text = if (selectedCount == 0) {
                                "Pick seeds to build on"
                            } else {
                                "Generate post ideas ($selectedCount)"
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState(configured: Boolean, onEditAddress: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (configured) {
                    "No seeds yet. They save themselves when a post is worth building on."
                } else {
                    "Set the backend address to start banking ideas."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!configured) {
                Button(
                    onClick = onEditAddress,
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("Set address") }
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
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
