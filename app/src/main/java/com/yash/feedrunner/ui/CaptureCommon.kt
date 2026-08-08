package com.yash.feedrunner.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-screen look at the captured screenshot. Scrolls vertically, since a
 * Hold capture can be many screens tall. Tap anywhere to close.
 */
@Composable
internal fun CaptureViewer(path: String, onDismiss: () -> Unit) {
    val bitmap = rememberDecoded(path, MAX_VIEWER_PIXELS)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .clickable(onClick = onDismiss),
    ) {
        if (bitmap == null) {
            Text(
                text = "Capture is no longer available.",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Captured screenshot",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = "tap to close",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp),
            )
        }
    }
}

/**
 * Decodes a bitmap on the IO dispatcher and returns null until it is ready.
 * Decoding during composition blocks the frame on file I/O plus a large
 * allocation, which is felt as lag when the panel opens.
 */
@Composable
internal fun rememberDecoded(path: String?, maxPixels: Int): Bitmap? =
    produceState<Bitmap?>(initialValue = null, path, maxPixels) {
        value = path?.let { withContext(Dispatchers.IO) { decodeScaled(it, maxPixels) } }
    }.value

/**
 * Decodes at a reduced sample size. A stitched Hold capture can be several
 * screens tall, and decoding one of those at full resolution is tens of MB.
 */
private fun decodeScaled(path: String, maxPixels: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while ((bounds.outWidth / sample) * (bounds.outHeight / sample) > maxPixels) {
        sample *= 2
    }
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()

/** Full-width on a 1080px screen; half the allocation of the old 4M budget. */
internal const val MAX_VIEWER_PIXELS = 2_000_000

/** The inline preview draws at 44x56dp, so it only ever needs the thumbnail. */
internal const val MAX_PREVIEW_PIXELS = 200_000

