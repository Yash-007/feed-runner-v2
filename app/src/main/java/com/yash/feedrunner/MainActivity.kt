package com.yash.feedrunner

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yash.feedrunner.bubble.BubbleService
import com.yash.feedrunner.capture.CaptureService
import com.yash.feedrunner.data.VoiceRulesStore

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

        val voiceRulesStore = VoiceRulesStore(this)
        val apiKeyConfigured = BuildConfig.ANTHROPIC_API_KEY.isNotBlank()

        setContent {
            MaterialTheme {
                var voiceRules by remember { mutableStateOf(voiceRulesStore.rules) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("Feed Runner", style = MaterialTheme.typography.headlineMedium)

                    SetupSection(
                        hasOverlayPermission = hasOverlayPermission,
                        captureServiceEnabled = captureServiceEnabled,
                        apiKeyConfigured = apiKeyConfigured,
                        onOpenOverlaySettings = ::openOverlaySettings,
                        onOpenAccessibilitySettings = ::openAccessibilitySettings,
                    )

                    Spacer(Modifier.height(4.dp))

                    Text("Extra voice rules (optional)", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Your full voice is already built into the app. Use this only for " +
                            "temporary tweaks — they are appended last and override the rest.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = voiceRules,
                        onValueChange = { voiceRules = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        onClick = {
                            voiceRulesStore.rules = voiceRules
                            Toast.makeText(this@MainActivity, "Extra rules saved", Toast.LENGTH_SHORT)
                                .show()
                        },
                    ) {
                        Text("Save extra rules")
                    }

                    Spacer(Modifier.height(4.dp))

                    if (hasOverlayPermission && captureServiceEnabled) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(onClick = { BubbleService.start(this@MainActivity) }) {
                                Text("Start bubble")
                            }
                            OutlinedButton(onClick = { BubbleService.stop(this@MainActivity) }) {
                                Text("Stop")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasOverlayPermission = Settings.canDrawOverlays(this)
        captureServiceEnabled = isCaptureServiceEnabled()
    }

    /**
     * Reads the system's enabled-services list rather than checking
     * [CaptureService.instance]. The static instance only exists once Android
     * has bound the service, which lags a reinstall — so a live, enabled
     * service would otherwise show as "not enabled" until the next bind.
     */
    private fun isCaptureServiceEnabled(): Boolean {
        val expected = ComponentName(this, CaptureService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
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

@androidx.compose.runtime.Composable
private fun SetupSection(
    hasOverlayPermission: Boolean,
    captureServiceEnabled: Boolean,
    apiKeyConfigured: Boolean,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (hasOverlayPermission) {
            Text("Overlay permission granted ✓")
        } else {
            Text("Step 1: allow drawing over other apps")
            Button(onClick = onOpenOverlaySettings) { Text("Grant overlay permission") }
        }

        if (captureServiceEnabled) {
            Text("Capture service enabled ✓")
        } else {
            Text("Step 2: enable the capture service under Accessibility")
            Button(onClick = onOpenAccessibilitySettings) { Text("Open Accessibility settings") }
        }

        if (apiKeyConfigured) {
            Text("API key configured ✓")
        } else {
            Text(
                "No API key — add anthropic.apiKey to local.properties and rebuild.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
