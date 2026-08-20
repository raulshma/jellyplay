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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
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
import com.composables.icons.tabler.outline.LockAccess
import com.raulshma.jellyplay.core.ui.R
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.tv.RequestOrRestoreFocus
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
    val deviceCredentialAvailability = rememberDeviceCredentialAvailability()
    val hasPin = pinHash != null
    val canUseBiometric = biometricEnabled && biometricAvailability == BiometricAuthHelper.Availability.AVAILABLE && activity != null
    // Device credential (screen-lock PIN/pattern/password) is the WhatsApp-style fallback
    // for the biometric lock: it lets the user recover when biometric auth can't be
    // completed (cancelled, failed, or became unavailable). Gated on biometricEnabled so
    // app-PIN-only users keep using their own PIN.
    val canUseDeviceCredential = biometricEnabled &&
        deviceCredentialAvailability == BiometricAuthHelper.Availability.AVAILABLE &&
        activity != null

    var showBiometric by remember { mutableStateOf(canUseBiometric) }
    var biometricError by remember { mutableStateOf<String?>(null) }
    var biometricPromptTrigger by remember { mutableStateOf(0) }
    var deviceCredentialTrigger by remember { mutableStateOf(0) }
    var deviceCredentialActive by remember { mutableStateOf(false) }

    // TV: the biometric branch has no PIN pad to grab focus — land on the retry button.
    // The PIN branch is covered by PinLockScreen's own first-key grab.
    val biometricRetryFocusRequester = remember { FocusRequester() }
    RequestOrRestoreFocus(
        focusRequester = if (showBiometric && canUseBiometric) biometricRetryFocusRequester else null,
        debugKey = "auth_biometric_retry",
    )

    // Shared launch action for the device-credential prompt — identical across
    // the three call sites below, hoisted so each ScreenLockButton stays a
    // one-liner instead of repeating the trigger bookkeeping.
    val launchDeviceCredential: () -> Unit = {
        biometricError = null
        deviceCredentialActive = true
        deviceCredentialTrigger++
    }

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

    if (deviceCredentialActive && canUseDeviceCredential) {
        DeviceCredentialPromptLauncher(
            activity = activity,
            title = title,
            description = stringResource(R.string.core_ui_auth_device_credential_prompt),
            trigger = deviceCredentialTrigger,
            onSuccess = {
                deviceCredentialActive = false
                onPinEntered("")
            },
            onError = { error ->
                deviceCredentialActive = false
                biometricError = error
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
                            .focusRequester(biometricRetryFocusRequester)
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
                    if (canUseDeviceCredential) {
                        ScreenLockButton(
                            labelRes = R.string.core_ui_auth_use_screen_lock,
                            onClick = launchDeviceCredential,
                        )
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
                if (canUseDeviceCredential) {
                    Spacer(Modifier.height(16.dp))
                    ScreenLockButton(
                        labelRes = R.string.core_ui_auth_use_screen_lock,
                        onClick = launchDeviceCredential,
                    )
                }
            } else if (canUseDeviceCredential) {
                // Biometric lock is enabled but biometric auth is unavailable (e.g.
                // fingerprints were removed after enabling) and no app PIN is set, so
                // the PIN keypad branch above is skipped. Offer the device screen-lock
                // credential as the recovery path so the user is never hard-locked
                // out. (A "Use PIN" button is intentionally absent here: this branch
                // is only reached when hasPin == false, so it would be dead code.)
                Icon(
                    imageVector = Tabler.Outline.Lock,
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
                    text = subtitle,
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
                ScreenLockButton(
                    labelRes = R.string.core_ui_auth_unlock_screen_lock,
                    onClick = launchDeviceCredential,
                )
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

@Composable
private fun DeviceCredentialPromptLauncher(
    activity: FragmentActivity?,
    title: String,
    description: String,
    trigger: Int = 0,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
) {
    val nonNullActivity = activity ?: return
    DisposableEffect(trigger) {
        BiometricAuthHelper.authenticateDeviceCredential(
            activity = nonNullActivity,
            title = title,
            description = description,
            onSuccess = onSuccess,
            onError = onError,
        )
        onDispose { }
    }
}

/**
 * The "Use screen lock" / "Unlock with screen lock" button — rendered at three
 * sites in [AuthChallengeScreen] (biometric branch, PIN branch, recovery
 * branch). Extracted so the focus state, icon, shape, and trigger bookkeeping
 * live in one place instead of being copy-pasted.
 */
@Composable
private fun ScreenLockButton(
    @StringRes labelRes: Int,
    onClick: () -> Unit,
) {
    val focusState = rememberTvFocusState(focusedScale = 1.05f)
    FilledTonalButton(
        onClick = onClick,
        shape = ShapeCache.smooth12,
        modifier = Modifier
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth12),
    ) {
        Icon(
            Tabler.Outline.LockAccess,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.padding(horizontal = 4.dp))
        Text(stringResource(labelRes))
    }
}
