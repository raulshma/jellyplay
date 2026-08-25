package com.raulshma.jellyplay.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Eye
import com.composables.icons.tabler.outline.EyeOff
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Desktop v1 sign-in gate (Phase X desktop nav): server URL + username +
 * password over [AuthRepository.login]. This is deliberately NOT the final
 * auth UX — the Android app's server-list / multi-user picker / QuickConnect
 * flows live in the legacy Hilt feature and migrate later; this pane exists
 * so the desktop shell has a session to navigate with.
 *
 * Success needs no callback: DesktopAppRoot observes
 * [AuthRepository.isAuthenticated], which flips when the API client's atomic
 * session flow publishes the (server, user) pair — and flips back on
 * sign-out, returning here.
 *
 * Cut from v1 (revisit with the real auth migration): QuickConnect, server
 * address alternates management, remembered-user picker, self-signed-cert
 * trust flow.
 */
@Composable
internal fun DesktopSignInPane(authRepository: AuthRepository = koinInject()) {
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var signingIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Prefill from the last persisted identity: the address of the most
    // recently used server and its last user name, when any exist. Pure
    // convenience — the fields stay fully editable for a different account.
    LaunchedEffect(authRepository) {
        val server = authRepository.currentServer.first()
        if (serverUrl.isBlank()) {
            server?.address?.let { serverUrl = it }
        }
        authRepository.currentUser.first()?.name?.let { name ->
            if (username.isBlank()) username = name
        }
    }

    val canSubmit = !signingIn && serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    Card(Modifier.width(420.dp)) {
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sign in to JellyPlay", style = MaterialTheme.typography.titleLarge)
            Text(
                "Jellyfin server URL, e.g. https://media.example.com",
                style = MaterialTheme.typography.bodySmall,
            )

            OutlinedTextField(
                value = serverUrl,
                onValueChange = {
                    serverUrl = it
                    errorMessage = null
                },
                label = { Text("Server URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    errorMessage = null
                },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = null
                },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation =
                    if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Tabler.Outline.EyeOff else Tabler.Outline.Eye,
                            contentDescription =
                                if (passwordVisible) "Hide password" else "Show password",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            errorMessage?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (signingIn) {
                    CircularProgressIndicator(Modifier.padding(end = 12.dp).height(20.dp))
                }
                Button(
                    enabled = canSubmit,
                    onClick = {
                        signingIn = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val result = authRepository.login(serverUrl.trim(), username.trim(), password)
                                // On success isAuthenticated flips and
                                // DesktopAppRoot swaps this pane out; only
                                // failures land here.
                                result.onFailure { failure ->
                                    errorMessage = failure.message ?: "Sign-in failed."
                                }
                            } catch (failure: Throwable) {
                                // login() rethrows on unreachable/typo'd server
                                // URLs (connectToServer Result → getOrThrow
                                // inside the API client); onFailure alone would
                                // leave the spinner stuck.
                                if (failure is kotlinx.coroutines.CancellationException) throw failure
                                errorMessage = failure.message ?: "Sign-in failed."
                            } finally {
                                signingIn = false
                            }
                        }
                    },
                ) {
                    Text("Sign in")
                }
            }
        }
    }
}
