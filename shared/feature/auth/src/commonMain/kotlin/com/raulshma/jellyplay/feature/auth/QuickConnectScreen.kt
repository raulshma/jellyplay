@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.raulshma.jellyplay.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.auth.generated.resources.Res
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_back_to_sign_in
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_cancel
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_qc_connected
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_qc_enter_code
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_qc_failed
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_qc_path
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_qc_starting
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_qc_waiting
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_quick_connect
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_signing_in
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_try_again
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuickConnectScreen(
    serverAddress: String,
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit,
    viewModel: AuthViewModel = koinViewModel(),
) {
    val quickConnectState by viewModel.quickConnectState.collectAsStateWithLifecycle()

    var contentVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    // Start Quick Connect when screen enters composition
    LaunchedEffect(serverAddress) {
        viewModel.startQuickConnect(serverAddress)
    }

    // Navigate on success
    LaunchedEffect(quickConnectState) {
        if (quickConnectState is QuickConnectUiState.Success) {
            onLoginSuccess()
        }
    }

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.auth_quick_connect),
        onBack = {
            viewModel.cancelQuickConnect()
            onBack()
        },
    ) { padding ->
        val adaptiveInfo = LocalAdaptiveInfo.current
        val isTv = LocalTvMode.current
        val contentPad = adaptiveInfo.contentPadding(isTv)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = contentPad, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + slideInVertically(
                    initialOffsetY = { it / 20 },
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                ),
            ) {
                when (val state = quickConnectState) {
                    is QuickConnectUiState.Idle -> { /* shouldn't be visible long */ }

                    is QuickConnectUiState.Initiating -> {
                        InitiatingContent()
                    }

                    is QuickConnectUiState.WaitingForApproval -> {
                        WaitingForApprovalContent(
                            code = state.code,
                            onCancel = {
                                viewModel.cancelQuickConnect()
                                onBack()
                            },
                        )
                    }

                    is QuickConnectUiState.Authenticating -> {
                        AuthenticatingContent()
                    }

                    is QuickConnectUiState.Success -> {
                        SuccessContent()
                    }

                    is QuickConnectUiState.Error -> {
                        ErrorContent(
                            message = state.message,
                            onRetry = { viewModel.startQuickConnect(serverAddress) },
                            onBack = {
                                viewModel.resetQuickConnectState()
                                onBack()
                            },
                            isTv = isTv,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InitiatingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        JellyPlayLoadingIndicator(modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            stringResource(Res.string.auth_qc_starting),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun WaitingForApprovalContent(
    code: String,
    onCancel: () -> Unit,
) {
    val isTv = LocalTvMode.current
    val cancelFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (isTv) cancelFocusRequester.tryRequestFocus("qc_cancel")
    }

    val reducedMotion = com.raulshma.jellyplay.core.ui.components.LocalReducedMotion.current
    val pulseAlpha = if (!reducedMotion) {
        val infiniteTransition = rememberInfiniteTransition(label = "qc_pulse")
        infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse_alpha",
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Tabler.Outline.Link,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            stringResource(Res.string.auth_qc_enter_code),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(Res.string.auth_qc_path),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Large code display
        Text(
            code,
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = FontFamily.Monospace,
            ),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .graphicsLayer { alpha = pulseAlpha.value }
                .padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Polling indicator
        JellyPlayLoadingIndicator(
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(Res.string.auth_qc_waiting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        val cancelFocusState = rememberTvFocusState(focusedScale = 1.04f)
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
                .focusRequester(cancelFocusRequester)
                .then(cancelFocusState.focusModifier)
                .tvFocusIndicator(cancelFocusState, ShapeCache.smooth12),
        ) {
            Text(stringResource(Res.string.auth_cancel))
        }
    }
}

@Composable
private fun AuthenticatingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        JellyPlayLoadingIndicator(modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            stringResource(Res.string.auth_signing_in),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun SuccessContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(Res.string.auth_qc_connected),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ErrorContent(
    message: AuthMessage,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    isTv: Boolean,
) {
    val retryFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (isTv) retryFocusRequester.tryRequestFocus("qc_retry")
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(Res.string.auth_qc_failed),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            message.asText(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))

        val tryAgainFocusState = rememberTvFocusState(focusedScale = 1.04f)
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth()
                .focusRequester(retryFocusRequester)
                .then(tryAgainFocusState.focusModifier)
                .tvFocusIndicator(tryAgainFocusState, ShapeCache.smooth12),
        ) {
            Icon(Tabler.Outline.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(Res.string.auth_try_again))
        }

        Spacer(modifier = Modifier.height(12.dp))

        val backFocusState = rememberTvFocusState(focusedScale = 1.04f)
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
                .then(backFocusState.focusModifier)
                .tvFocusIndicator(backFocusState, ShapeCache.smooth12),
        ) {
            Text(stringResource(Res.string.auth_back_to_sign_in))
        }
    }
}
