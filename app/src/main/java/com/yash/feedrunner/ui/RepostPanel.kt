package com.yash.feedrunner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val SheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

/**
 * Composer for reposting or quoting the captured post.
 *
 * The steer is optional, so suggesting is always available: you can type nothing
 * and just ask. The field takes focus on open so the keyboard is already up,
 * which is the whole point of the flow.
 */
@Composable
fun RepostPanel(
    state: RepostState,
    capturePath: String?,
    steer: String,
    onSteerChange: (String) -> Unit,
    onSuggest: () -> Unit,
    onCopyText: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var viewerPath by remember { mutableStateOf<String?>(null) }

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
                .heightIn(max = 600.dp)
                .imePadding()
                .clickable(enabled = false) {},
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Repost",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text("Close") }
                }

                CapturedPostRow(
                    capturePath = capturePath,
                    onView = { viewerPath = it },
                )

                Composer(
                    steer = steer,
                    enabled = state !is RepostState.Loading,
                    onSteerChange = onSteerChange,
                    onFocusChanged = onFocusChanged,
                    onSuggest = onSuggest,
                )

                when (state) {
                    is RepostState.Composing -> Unit
                    is RepostState.Loading -> SuggestingRow()
                    is RepostState.Error -> Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    is RepostState.Ready -> Captions(
                        captions = state.captions,
                        onCopyText = onCopyText,
                    )
                }
            }
        }

        viewerPath?.let { path ->
            CaptureViewer(path = path, onDismiss = { viewerPath = null })
        }
    }
}

/** The post being captioned, as a preview you can open full-screen. */
@Composable
private fun CapturedPostRow(capturePath: String?, onView: (String) -> Unit) {
    val preview = rememberDecoded(capturePath, MAX_PREVIEW_PIXELS)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(8.dp),
    ) {
        if (preview != null && capturePath != null) {
            androidx.compose.foundation.Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = "View capture",
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .size(width = 38.dp, height = 48.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .clickable { onView(capturePath) },
            )
        }
        Column(modifier = Modifier.padding(start = if (preview != null) 10.dp else 2.dp)) {
            Text(
                text = "Captioning this post",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (capturePath != null) "tap the image to check it" else "capture attached",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Composer(
    steer: String,
    enabled: Boolean,
    onSteerChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSuggest: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    // Open with the keyboard already up: typing a steer is the primary action.
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Column {
        Text(
            text = "Add a steer (optional)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Leave it empty to just get suggestions.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )

        OutlinedTextField(
            value = steer,
            onValueChange = onSteerChange,
            enabled = enabled,
            placeholder = {
                Text("e.g. tie it to exchange infra", style = MaterialTheme.typography.bodyMedium)
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(18.dp),
            maxLines = 4,
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
            REPOST_STEERS.forEach { option ->
                Text(
                    text = option,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .clickable(enabled = enabled) {
                            onSteerChange(if (steer.isBlank()) option else "$steer, $option")
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
                .clickable(enabled = enabled, onClick = onSuggest),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp)) {
                Text(
                    text = "Suggest captions",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SuggestingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            text = "Writing captions…",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun Captions(captions: List<CaptionSuggestion>, onCopyText: (String) -> Unit) {
    var selectingId by remember { mutableIntStateOf(-1) }
    var copiedId by remember { mutableIntStateOf(-1) }

    LaunchedEffect(copiedId) {
        if (copiedId >= 0) {
            delay(1400)
            copiedId = -1
        }
    }

    Column(modifier = Modifier.padding(top = 14.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
        ) {
            Text(
                text = "Captions",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "tap to copy · hold to select",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        captions.forEach { caption ->
            CaptionCard(
                caption = caption,
                selecting = selectingId == caption.id,
                copied = copiedId == caption.id,
                onCopy = {
                    onCopyText(caption.text)
                    copiedId = caption.id
                    selectingId = -1
                },
                onLongPress = { selectingId = caption.id },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CaptionCard(
    caption: CaptionSuggestion,
    selecting: Boolean,
    copied: Boolean,
    onCopy: () -> Unit,
    onLongPress: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (selecting) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
            border = if (selecting) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            val body = @Composable {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (caption.note.isNotEmpty()) {
                        Text(
                            text = caption.note,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
                    Text(text = caption.text, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (selecting) {
                SelectionContainer { body() }
            } else {
                Box(
                    modifier = Modifier.combinedClickable(
                        onClick = onCopy,
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress()
                        },
                    ),
                ) { body() }
            }
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
