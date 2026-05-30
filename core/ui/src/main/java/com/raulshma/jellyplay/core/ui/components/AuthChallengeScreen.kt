package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.fragment.app.FragmentActivity
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Fingerprint
import com.composables.icons.tabler.outline.Lock

@Composable
fun AuthChallengeScreen(
    title: String = "Authenticate",
    subtitle: String = "Confirm your identity",
    pinHash: String?,
    biometricEnabled: Boolean = false,
    onPinEntered: (String) -> Unit,
    onErrorClear: () -> Unit = {},
    errorMessage: String? = null,
    onDismiss: () -> Unit = {},
    showAsDialog: Boolean = false,
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? FragmentActivity }
    val biometricAvailability = rememberBiometricAvailability()
    val canUseBiometric = biometricEnabled && biometricAvailability == BiometricAuthHelper.Availability.AVAILABLE && activity != null

    var showBiometric by remember { mutableStateOf(canUseBiometric) }
    var biometricError by remember { mutableStateOf<String?>(null) }

    if (showBiometric && canUseBiometric) {
        BiometricPromptLauncher(
            activity = activity!!,
            title = title,
            subtitle = subtitle,
            onSuccess = { onPinEntered("") },
            onError = { error ->
                biometricError = error
                showBiometric = false
            },
            onFailed = {
                showBiometric = false
            },
        )
    }

    val content = @Composable {
        Column(
            modifier = Modifier.fillMaxSize(),
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
                    text = "Use biometric to authenticate",
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
                FilledTonalButton(
                    onClick = {
                        showBiometric = false
                        biometricError = null
                    },
                ) {
                    Icon(
                        Tabler.Outline.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("Use PIN")
                }
            } else {
                PinLockScreen(
                    title = title,
                    subtitle = subtitle,
                    onPinEntered = onPinEntered,
                    onErrorClear = onErrorClear,
                    errorMessage = errorMessage,
                )
                if (canUseBiometric) {
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(
                        onClick = {
                            showBiometric = true
                            biometricError = null
                        },
                    ) {
                        Icon(
                            Tabler.Outline.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text("Use Biometric")
                    }
                }
            }
        }
    }

    if (showAsDialog) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                securePolicy = SecureFlagPolicy.SecureOn,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
            ),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
            ) {
                Box(modifier = Modifier.padding(24.dp)) {
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
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    onFailed: () -> Unit,
) {
    DisposableEffect(Unit) {
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
