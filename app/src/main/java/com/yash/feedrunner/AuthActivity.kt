package com.yash.feedrunner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.lifecycle.lifecycleScope
import com.yash.feedrunner.data.BackendConfig
import com.yash.feedrunner.data.IdeaBankApi
import com.yash.feedrunner.ui.ideas.IdeasActivity
import com.yash.feedrunner.ui.theme.FeedRunnerTheme
import com.yash.feedrunner.ui.theme.HairlineCard
import com.yash.feedrunner.ui.theme.Motion
import com.yash.feedrunner.ui.theme.Radius
import com.yash.feedrunner.ui.theme.SegmentedControl
import com.yash.feedrunner.ui.theme.Space
import com.yash.feedrunner.ui.theme.halftone
import com.yash.feedrunner.ui.theme.pressClickable
import com.yash.feedrunner.ui.theme.washBrush
import androidx.compose.foundation.background
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AuthMode(val label: String) {
    LOGIN("Sign in"),
    SIGNUP("Create account"),
}

/**
 * The front door. Shown once: the session token never expires, so after one
 * signup or login the app opens straight onto Ideas until you sign out.
 *
 * The whole screen is the wash the app's headers wear, with one card on it —
 * the same materials as everywhere else, just allowed to fill the room for the
 * only screen that has nothing else to show.
 */
class AuthActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val config = BackendConfig(this)
        val api = IdeaBankApi(config)

        setContent {
            FeedRunnerTheme {
                var mode by remember { mutableStateOf(AuthMode.LOGIN) }
                var name by remember { mutableStateOf("") }
                var username by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }
                var showPassword by remember { mutableStateOf(false) }
                var busy by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf<String?>(null) }

                fun submit() {
                    if (busy) return
                    error = null
                    val cleanUsername = username.trim().lowercase()
                    when {
                        cleanUsername.isEmpty() || password.isEmpty() ->
                            error = "Fill in username and password."
                        mode == AuthMode.SIGNUP && name.trim().isEmpty() ->
                            error = "Fill in your name."
                        else -> {
                            busy = true
                            lifecycleScope.launch {
                                val outcome = withContext(Dispatchers.IO) {
                                    runCatching {
                                        if (mode == AuthMode.SIGNUP) {
                                            api.signup(cleanUsername, password, name.trim())
                                        } else {
                                            api.login(cleanUsername, password)
                                        }
                                    }
                                }
                                busy = false
                                outcome
                                    .onSuccess { result ->
                                        config.authToken = result.token
                                        config.accountName = result.name
                                        config.accountUsername = result.username
                                        startActivity(
                                            Intent(this@AuthActivity, IdeasActivity::class.java),
                                        )
                                        finish()
                                    }
                                    .onFailure { failure ->
                                        error = failure.message ?: "Something went wrong."
                                    }
                            }
                        }
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(washBrush())
                        .halftone()
                        .imePadding(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Space.xl, vertical = Space.xxl),
                    ) {
                        Text(
                            text = "Feed Runner",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Your reply and post copilot.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.xs, bottom = Space.xl),
                        )

                        HairlineCard(
                            shape = RoundedCornerShape(Radius.panel),
                            fill = MaterialTheme.colorScheme.surface,
                        ) {
                            Column(
                                modifier = Modifier
                                    .animateContentSize(animationSpec = Motion.enter())
                                    .padding(Space.xl),
                            ) {
                                SegmentedControl(
                                    options = AuthMode.entries.toList(),
                                    selected = mode,
                                    label = { it.label },
                                    onSelect = {
                                        mode = it
                                        error = null
                                    },
                                    enabled = !busy,
                                )

                                AnimatedVisibility(visible = mode == AuthMode.SIGNUP) {
                                    AuthField(
                                        value = name,
                                        onValueChange = { name = it },
                                        label = "Your name",
                                        enabled = !busy,
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = KeyboardCapitalization.Words,
                                            imeAction = ImeAction.Next,
                                        ),
                                    )
                                }

                                AuthField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = "Username",
                                    enabled = !busy,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Ascii,
                                        imeAction = ImeAction.Next,
                                    ),
                                )

                                AuthField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = "Password",
                                    enabled = !busy,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done,
                                    ),
                                    onDone = ::submit,
                                    visualTransformation = if (showPassword) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
                                    trailing = {
                                        Text(
                                            text = if (showPassword) "hide" else "show",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(Radius.chip))
                                                .clickable { showPassword = !showPassword }
                                                .padding(
                                                    horizontal = Space.sm,
                                                    vertical = Space.xs,
                                                ),
                                        )
                                    },
                                )

                                AnimatedVisibility(visible = error != null) {
                                    Text(
                                        text = error.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(top = Space.md),
                                    )
                                }

                                SubmitButton(
                                    label = when (mode) {
                                        AuthMode.LOGIN -> "Sign in"
                                        AuthMode.SIGNUP -> "Create account"
                                    },
                                    busy = busy,
                                    onClick = ::submit,
                                )

                                Text(
                                    text = "Your ideas, drafts and streak live in your account.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(top = Space.md)
                                        .align(Alignment.CenterHorizontally),
                                )
                            }
                        }

                        Spacer(Modifier.height(Space.xxl))
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    onDone: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@androidx.compose.runtime.Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        enabled = enabled,
        singleLine = true,
        shape = RoundedCornerShape(Radius.control),
        keyboardOptions = keyboardOptions,
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onDone = { onDone?.invoke() },
        ),
        visualTransformation = visualTransformation,
        trailingIcon = trailing,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.md),
    )
}

/** The primary button, with the spinner living inside it while a call runs. */
@androidx.compose.runtime.Composable
private fun SubmitButton(label: String, busy: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(top = Space.lg)
            .fillMaxWidth()
            .pressClickable(enabled = !busy, pressedScale = 0.97f, onClick = onClick)
            .clip(RoundedCornerShape(Radius.control))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (busy) 0.7f else 1f))
            .padding(vertical = 14.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
