package com.yash.feedrunner.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PanelShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
private val WorthColor = Color(0xFF00BA7C)
private val SkipColor = Color(0xFF8B98A5)

@Composable
fun ReplyPanel(
    state: PanelState,
    onDraftCopy: (Draft) -> Unit,
    onRefine: (Draft, Refinement) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
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
                .heightIn(max = 560.dp)
                // Swallow taps on the sheet so they don't reach the scrim.
                .clickable(enabled = false) {},
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                PanelHeader(onDismiss = onDismiss)

                when (state) {
                    is PanelState.Loading -> LoadingBody()
                    is PanelState.Error -> ErrorBody(state.message, onRetry)
                    is PanelState.Ready -> ReadyBody(state, onDraftCopy, onRefine)
                }
            }
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
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        (state.source as? ResultSource.Cached)?.let { CachedBanner(it) }
        VerdictRow(state.verdict)
        state.drafts.forEach { draft ->
            DraftCard(
                draft = draft,
                onCopy = { onDraftCopy(draft) },
                onRefine = { refinement -> onRefine(draft, refinement) },
            )
        }
        Text(
            text = "Tap a draft to copy it, then paste in X.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

/** Makes it obvious the drafts came from disk rather than a fresh capture. */
@Composable
private fun CachedBanner(cached: ResultSource.Cached) {
    val thumbnail = remember(cached.thumbnailPath) {
        cached.thumbnailPath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(8.dp),
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .size(width = 40.dp, height = 52.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
        }
        Column(modifier = Modifier.padding(start = if (thumbnail != null) 10.dp else 2.dp)) {
            Text(
                text = "Saved result",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${relativeAge(cached.savedAtMillis)} · no new API call",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VerdictRow(verdict: Verdict) {
    val accent = if (verdict.worthReplying) WorthColor else SkipColor
    Row(modifier = Modifier.padding(top = 4.dp)) {
        Pill(
            text = if (verdict.worthReplying) "WORTH REPLYING" else "SKIP",
            color = accent,
        )
    }
    Text(
        text = verdict.reason,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DraftCard(
    draft: Draft,
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
                Refinement.entries.forEach { refinement ->
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
