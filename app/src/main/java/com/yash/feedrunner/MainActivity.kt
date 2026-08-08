package com.yash.feedrunner

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yash.feedrunner.bubble.BubbleService
import com.yash.feedrunner.capture.CaptureService

class MainActivity : ComponentActivity() {

    private var hasOverlayPermission by mutableStateOf(false)
    private var captureServiceEnabled by mutableStateOf(false)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Feed Runner", style = MaterialTheme.typography.headlineMedium)

                    if (!hasOverlayPermission) {
                        Text("Step 1: allow drawing over other apps")
                        Button(onClick = ::openOverlaySettings) {
                            Text("Grant overlay permission")
                        }
                    } else if (!captureServiceEnabled) {
                        Text("Overlay permission granted ✓")
                        Text("Step 2: enable the capture service under Accessibility")
                        Button(onClick = ::openAccessibilitySettings) {
                            Text("Open Accessibility settings")
                        }
                    } else {
                        Text("Overlay permission granted ✓")
                        Text("Capture service enabled ✓")
                        Button(onClick = { BubbleService.start(this@MainActivity) }) {
                            Text("Start bubble")
                        }
                        Button(onClick = { BubbleService.stop(this@MainActivity) }) {
                            Text("Stop bubble")
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasOverlayPermission = Settings.canDrawOverlays(this)
        captureServiceEnabled = CaptureService.instance != null
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
