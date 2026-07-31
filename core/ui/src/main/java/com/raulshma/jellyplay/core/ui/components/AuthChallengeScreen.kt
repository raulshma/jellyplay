package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.foundation.layout.heightIn
import androidx.fragment.app.FragmentActivity
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Fingerprint
import com.composables.icons.tabler.outline.Lock
import com.raulshma.jellyplay.core.ui.R
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

@Composable
fun AuthChallengeScreen(
    title: String = stringResource(R.string.core_ui_auth_title),
    subtitle: String = stringResource(R.string.core_ui_auth_subtitle),
    pinHash: String?,
    biometricEnabled: Boolean = false,
    onPinEntered: (String) -> Unit,
    onErrorClear: () -> Unit = {},
    errorMessage: String? = null,
    onDismiss: () -> Unit = {},
    showAsDialog: Boolean = false,
    enabled: Boolean = true,
    verifying: Boolean = false,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val biometricAvailability = rememberBiometricAvailability()
    val hasPin = pinHash != null
    val canUseBiometric = biometricEnabled && biometricAvailability == BiometricAuthHelper.Availability.AVAILABLE && activity != null

    var showBiometric by remember { mutableStateOf(canUseBiometric) }
    var biometricError by remember { mutableStateOf<String?>(null) }
    var biometricPromptTrigger by remember { mutableStateOf(0) }

    if (showBiometric && canUseBiometric) {
        BiometricPromptLauncher(
            activity = activity,
            title = title,
            subtitle = subtitle,
            trigger = biometricPromptTrigger,
            onSuccess = { onPinEntered("") },
            onError = { error ->
                biometricError = error
                if (hasPin) showBiometric = false
            },
            onFailed = {
                if (hasPin) showBiometric = false
            },
        )
    }

    val content = @Composable {
        Column(
            modifier = if (showAsDialog) Modifier else Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (showBiometric && canUseBiometric) {
                Icon(
                    imageVector = Tabler.Outline.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.core_ui_auth_biometric_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (biometricError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = biometricError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val retryFocusState = rememberTvFocusState(focusedScale = 1.05f)
                    FilledTonalButton(
                        onClick = {
                            biometricError = null
                            biometricPromptTrigger++
                        },
                        shape = ShapeCache.smooth12,
                        modifier = Modifier
                            .then(retryFocusState.focusModifier)
                            .tvFocusIndicator(retryFocusState, ShapeCache.smooth12),
                    ) {
                        Icon(
                            Tabler.Outline.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text(stringResource(R.string.core_retry))
                    }
                    if (hasPin) {
                        val usePinFocusState = rememberTvFocusState(focusedScale = 1.05f)
                        FilledTonalButton(
                            onClick = {
                                showBiometric = false
                                biometricError = null
                            },
                            shape = ShapeCache.smooth12,
                            modifier = Modifier
                                .then(usePinFocusState.focusModifier)
                                .tvFocusIndicator(usePinFocusState, ShapeCache.smooth12),
                        ) {
                            Icon(
                                Tabler.Outline.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text(stringResource(R.string.core_ui_auth_use_pin))
                        }
                    }
                }
            } else if (hasPin) {
                PinLockScreen(
                    title = title,
                    subtitle = subtitle,
                    onPinEntered = onPinEntered,
                    onErrorClear = onErrorClear,
                    errorMessage = errorMessage,
                    compactMode = showAsDialog,
                    enabled = enabled,
                    verifying = verifying,
                )
                if (canUseBiometric) {
                    Spacer(Modifier.height(16.dp))
                    val useBiometricFocusState = rememberTvFocusState(focusedScale = 1.05f)
                    FilledTonalButton(
                        onClick = {
                            showBiometric = true
                            biometricError = null
                        },
                        shape = ShapeCache.smooth12,
                        modifier = Modifier
                            .then(useBiometricFocusState.focusModifier)
                            .tvFocusIndicator(useBiometricFocusState, ShapeCache.smooth12),
                    ) {
                        Icon(
                            Tabler.Outline.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text(stringResource(R.string.core_ui_auth_use_biometric))
                    }
                }
            }
        }
    }

    if (showAsDialog) {
        val adaptiveInfo = LocalAdaptiveInfo.current
        val dialogPadding = when (adaptiveInfo.windowSizeClass) {
            WindowSizeClass.Expanded -> 32.dp
            WindowSizeClass.Medium -> 24.dp
            WindowSizeClass.Compact -> 16.dp
        }
        val innerPadding = when (adaptiveInfo.windowSizeClass) {
            WindowSizeClass.Expanded -> 24.dp
            WindowSizeClass.Medium -> 20.dp
            WindowSizeClass.Compact -> 16.dp
        }

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                securePolicy = SecureFlagPolicy.SecureOn,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
        ) {
            Surface(
                shape = ShapeCache.smooth28,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dialogPadding),
            ) {
                Box(modifier = Modifier.padding(innerPadding)) {
                    content()
                }
            }
        }
    } else {
        content()
    }
}

@Composable
private fun BiometricPromptLauncher(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    trigger: Int = 0,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    onFailed: () -> Unit,
) {
    DisposableEffect(trigger) {
        BiometricAuthHelper.authenticate(
            activity = activity,
            title = title,
            subtitle = subtitle,
            onSuccess = onSuccess,
            onError = onError,
            onFailed = onFailed,
        )
        onDispose { }
    }
}
