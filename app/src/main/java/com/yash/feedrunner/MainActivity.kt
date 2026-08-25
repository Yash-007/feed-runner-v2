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
import androidx.compose.material3.Surface
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
import com.yash.feedrunner.ui.ideas.IdeasActivity
import com.yash.feedrunner.capture.CaptureService
import com.yash.feedrunner.data.VoiceRulesStore
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import com.yash.feedrunner.ui.theme.ThemeMode
import com.yash.feedrunner.ui.theme.ThemePreference
import com.yash.feedrunner.ui.theme.FeedRunnerTheme
import com.yash.feedrunner.ui.theme.WashHeader
import com.yash.feedrunner.ui.theme.Space
import com.yash.feedrunner.ui.theme.SecondaryButton
import com.yash.feedrunner.ui.theme.Radius
import com.yash.feedrunner.ui.theme.PrimaryButton
import com.yash.feedrunner.ui.theme.HairlineCard
import com.yash.feedrunner.ui.theme.Accent
import androidx.compose.ui.draw.clip
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import com.yash.feedrunner.ui.theme.Hairline
import androidx.compose.foundation.clickable

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

        setContent {
            FeedRunnerTheme {
                var voiceRules by remember { mutableStateOf(voiceRulesStore.rules) }

                Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    // The one place the app raises its voice. Serif on a wash, and
                    // then everything below it stays plain.
                    WashHeader(
                        modifier = Modifier.padding(bottom = Space.lg),
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                start = Space.xl,
                                end = Space.xl,
                                top = Space.xxl,
                                bottom = Space.xl,
                            ),
                        ) {
                            Text(
                                text = "Feed Runner",
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = "A reply and post copilot for X and LinkedIn.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Space.sm),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.padding(horizontal = Space.lg),
                        verticalArrangement = Arrangement.spacedBy(Space.lg),
                    ) {
                        SetupSection(
                            hasOverlayPermission = hasOverlayPermission,
                            captureServiceEnabled = captureServiceEnabled,
                            onOpenOverlaySettings = ::openOverlaySettings,
                            onOpenAccessibilitySettings = ::openAccessibilitySettings,
                        )

                        HairlineCard {
                            Column(modifier = Modifier.padding(Space.lg)) {
                                Text(
                                    text = "Extra voice rules",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "Optional. Your full voice is already built in; " +
                                        "these are appended last and override the rest.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Space.xs),
                                )
                                OutlinedTextField(
                                    value = voiceRules,
                                    onValueChange = { voiceRules = it },
                                    shape = RoundedCornerShape(Radius.control),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor =
                                            MaterialTheme.colorScheme.outlineVariant,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = Space.md)
                                        .height(140.dp),
                                    textStyle = MaterialTheme.typography.bodySmall,
                                )
                                SecondaryButton(
                                    label = "Save extra rules",
                                    modifier = Modifier.padding(top = Space.md),
                                    onClick = {
                                        voiceRulesStore.rules = voiceRules
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Extra rules saved",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                )
                            }
                        }

                        if (hasOverlayPermission && captureServiceEnabled) {
                            val bubbleRunning = BubbleService.running.value
                            HairlineCard {
                                Column(modifier = Modifier.padding(Space.lg)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // A dot rather than a sentence about state:
                                        // the same language the Ideas screen uses for
                                        // the server.
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    if (bubbleRunning) {
                                                        Accent.lastResult
                                                    } else {
                                                        MaterialTheme.colorScheme.outline
                                                    },
                                                ),
                                        )
                                        Text(
                                            text = if (bubbleRunning) {
                                                "Bubble is running · open X and tap it"
                                            } else {
                                                "Bubble is off"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = Space.sm),
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                                        modifier = Modifier.padding(top = Space.md),
                                    ) {
                                        // One button that does the thing that is
                                        // currently possible, so the state is never
                                        // in question.
                                        PrimaryButton(
                                            label = if (bubbleRunning) {
                                                "Stop bubble"
                                            } else {
                                                "Start bubble"
                                            },
                                            onClick = {
                                                if (bubbleRunning) {
                                                    BubbleService.stop(this@MainActivity)
                                                } else {
                                                    BubbleService.start(this@MainActivity)
                                                }
                                            },
                                        )
                                        SecondaryButton(
                                            label = "Ideas",
                                            onClick = {
                                                startActivity(
                                                    Intent(
                                                        this@MainActivity,
                                                        IdeasActivity::class.java,
                                                    ),
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        HairlineCard {
                            Column(modifier = Modifier.padding(Space.lg)) {
                                Text(
                                    text = "Appearance",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "The overlays follow this too, so pin Dark if " +
                                        "you mostly scroll at night.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Space.xs),
                                )
                                val trackShape = RoundedCornerShape(Radius.control)
                                val current = ThemePreference.mode.value
                                Row(
                                    modifier = Modifier
                                        .padding(top = Space.md)
                                        .fillMaxWidth()
                                        .clip(trackShape)
                                        .border(
                                            Space.hair,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            trackShape,
                                        )
                                        .padding(Space.xs),
                                ) {
                                    ThemeMode.entries.forEach { option ->
                                        val selected = option == current
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(Radius.chip))
                                                .background(
                                                    if (selected) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        Color.Transparent
                                                    },
                                                )
                                                .clickable(enabled = !selected) {
                                                    ThemePreference.set(this@MainActivity, option)
                                                }
                                                .padding(vertical = Space.sm),
                                        ) {
                                            Text(
                                                text = option.label,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (selected) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(Space.xl))
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
        // The bubble is for other apps; it should not cover our own screens.
        BubbleService.setOwnUiVisible(this, true)
    }

    override fun onPause() {
        super.onPause()
        BubbleService.setOwnUiVisible(this, false)
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
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    HairlineCard {
        ReadyRow(
            done = hasOverlayPermission,
            doneLabel = "Overlay permission granted",
            todoLabel = "Allow drawing over other apps",
            action = "Grant",
            onAction = onOpenOverlaySettings,
        )
        Hairline()
        ReadyRow(
            done = captureServiceEnabled,
            doneLabel = "Capture service enabled",
            todoLabel = "Enable the capture service under Accessibility",
            action = "Open settings",
            onAction = onOpenAccessibilitySettings,
        )
    }
}

/**
 * One line of the readiness list. A tick when it is done, the thing to do and the
 * button that does it when it is not, so the card is a checklist rather than three
 * paragraphs of instructions.
 */
@androidx.compose.runtime.Composable
private fun ReadyRow(
    done: Boolean,
    doneLabel: String,
    todoLabel: String,
    action: String?,
    onAction: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md),
    ) {
        Text(
            text = if (done) "✓" else "•",
            style = MaterialTheme.typography.titleMedium,
            color = if (done) Accent.lastResult else MaterialTheme.colorScheme.error,
        )
        Text(
            text = if (done) doneLabel else todoLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = if (done) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier
                .padding(start = Space.md)
                .weight(1f),
        )
        if (!done && action != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.chip))
                    .clickable(onClick = onAction)
                    .padding(horizontal = Space.sm, vertical = Space.xs),
            )
        }
    }
}
