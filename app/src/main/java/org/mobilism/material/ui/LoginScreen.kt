package org.mobilism.material.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.AppTextButton
import org.mobilism.material.designsystem.ExpressiveButton
import org.mobilism.material.designsystem.LoadingState
import org.mobilism.material.designsystem.focusOutline

@Composable
fun LoginScreen(
    state: LoginState,
    onSignIn: (String, String) -> Unit,
    onGuest: () -> Unit,
    onRegister: () -> Unit,
    onForgot: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    // Password intentionally does not enter SavedStateHandle or saved instance state.
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        if (!state.busy) {
            keyboard?.hide()
            onSignIn(username, password)
        }
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(Modifier.padding(top = 48.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Mobilism", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Your source for apps & books", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val usernameInteraction = remember { MutableInteractionSource() }
                OutlinedTextField(
                    value = username, onValueChange = { username = it }, label = { Text("Username") },
                    enabled = !state.busy, singleLine = true,
                    interactionSource = usernameInteraction,
                    modifier = Modifier.fillMaxWidth().focusOutline(usernameInteraction),
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                )
                val passwordInteraction = remember { MutableInteractionSource() }
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, label = { Text("Password") },
                    enabled = !state.busy, singleLine = true,
                    interactionSource = passwordInteraction,
                    modifier = Modifier.fillMaxWidth().focusOutline(passwordInteraction),
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        AppIconButton(
                            if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            if (showPassword) "Hide password" else "Show password", { showPassword = !showPassword },
                        )
                    },
                )
                state.error?.let { ErrorPanel(it) }
                ExpressiveButton(submit, Modifier.fillMaxWidth(), enabled = !state.busy) {
                    Text(if (state.busy) "Signing in…" else "Login")
                }
                if (state.busy) LoadingState("Connecting to Mobilism", Modifier.fillMaxWidth())
                AppTextButton("Continue as guest", onGuest, Modifier.fillMaxWidth(), enabled = !state.busy)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    AppTextButton("Forgot password?", onForgot, enabled = !state.busy)
                    AppTextButton("Register", onRegister, enabled = !state.busy)
                }
            }
        }
    }
}
