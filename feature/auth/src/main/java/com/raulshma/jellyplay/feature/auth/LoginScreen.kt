package com.raulshma.jellyplay.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    serverAddress: String,
    onLoginSuccess: () -> Unit,
    onQuickConnect: () -> Unit,
    onBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isLoggingIn by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var contentVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign In") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val adaptiveInfo = LocalAdaptiveInfo.current
        val isTv = LocalTvMode.current
        val contentPad = adaptiveInfo.contentPadding(isTv)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = contentPad, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(AnimationTokens.StandardDuration, easing = AlphaEasing)) + slideInVertically(
                    initialOffsetY = { it / 20 },
                    animationSpec = tween(AnimationTokens.StandardDuration, easing = FancyTransitionEasing),
                ),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        serverAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(AnimationTokens.StandardDuration, delayMillis = 100, easing = AlphaEasing)) + slideInVertically(
                    initialOffsetY = { it / 20 },
                    animationSpec = tween(AnimationTokens.StandardDuration, delayMillis = 100, easing = FancyTransitionEasing),
                ),
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        error = null
                    },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(AnimationTokens.StandardDuration, delayMillis = 150, easing = AlphaEasing)) + slideInVertically(
                    initialOffsetY = { it / 20 },
                    animationSpec = tween(AnimationTokens.StandardDuration, delayMillis = 150, easing = FancyTransitionEasing),
                ),
            ) {
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
                )
            }

            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                AnimatedVisibility(
                    visible = error != null,
                    enter = fadeIn(tween(AnimationTokens.DefaultDuration, easing = AlphaEasing)),
                ) {
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(AnimationTokens.StandardDuration, delayMillis = 250, easing = AlphaEasing)) + slideInVertically(
                    initialOffsetY = { it / 20 },
                    animationSpec = tween(AnimationTokens.StandardDuration, delayMillis = 250, easing = FancyTransitionEasing),
                ),
            ) {
                Button(
                    onClick = {
                        if (username.isBlank()) {
                            error = "Please enter a username"
                            return@Button
                        }
                        isLoggingIn = true
                        error = null
                        viewModel.login(serverAddress, username, password) { result ->
                            isLoggingIn = false
                            result.onSuccess {
                                onLoginSuccess()
                            }.onFailure {
                                error = it.message ?: "Login failed"
                            }
                        }
                    },
                    enabled = !isLoggingIn,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isLoggingIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isLoggingIn) "Signing in..." else "Sign In")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(AnimationTokens.StandardDuration, delayMillis = 300, easing = AlphaEasing)) + slideInVertically(
                    initialOffsetY = { it / 20 },
                    animationSpec = tween(AnimationTokens.StandardDuration, delayMillis = 300, easing = FancyTransitionEasing),
                ),
            ) {
                OutlinedButton(
                    onClick = onQuickConnect,
                    enabled = !isLoggingIn,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Quick Connect")
                }
            }
        }
    }
}
