package com.raulshma.jellyplay.feature.onboarding.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.feature.onboarding.R

@Composable
fun SecurityStep(
    pinLockEnabled: Boolean,
    biometricLockEnabled: Boolean,
    autoLockTimerMs: Long,
    onPinLockEnabledChange: (Boolean) -> Unit,
    onPinHashSet: (String?) -> Unit,
    biometricAvailable: Boolean,
    onBiometricLockEnabledChange: (Boolean) -> Unit,
    onAutoLockTimerMsChange: (Long) -> Unit,
    hashPin: (String) -> String,
    modifier: Modifier = Modifier,
) {
    var pinInput by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var showPinSetup by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val pinsDoNotMatch = stringResource(R.string.onboarding_security_pins_do_not_match)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OnboardingStepScaffold(
            title = stringResource(R.string.onboarding_security_title),
            subtitle = stringResource(R.string.onboarding_security_subtitle),
            icon = Tabler.Outline.Lock,
            onNext = {},
        ) {
            OnboardingToggleRow(
                title = stringResource(R.string.onboarding_security_pin_lock),
                subtitle = stringResource(R.string.onboarding_security_pin_lock_subtitle),
                checked = pinLockEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        showPinSetup = true
                    } else {
                        onPinLockEnabledChange(false)
                        onPinHashSet(null)
                        showPinSetup = false
                    }
                },
            )

            if (showPinSetup || pinLockEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it; pinError = null },
                        label = { Text(stringResource(R.string.onboarding_security_enter_pin)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = pinConfirm,
                        onValueChange = { pinConfirm = it; pinError = null },
                        label = { Text(stringResource(R.string.onboarding_security_confirm_pin)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (pinError != null) {
                        Text(
                            text = pinError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (pinInput.length >= 4 && pinConfirm.length >= 4 && pinError == null) {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                if (pinInput == pinConfirm) {
                                    onPinHashSet(hashPin(pinInput))
                                    onPinLockEnabledChange(true)
                                    showPinSetup = false
                                } else {
                                    pinError = pinsDoNotMatch
                                }
                            },
                        ) {
                            Text(stringResource(R.string.onboarding_security_save_pin))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            OnboardingToggleRow(
                title = stringResource(R.string.onboarding_security_biometric_lock),
                subtitle = if (biometricAvailable) {
                    stringResource(R.string.onboarding_security_biometric_unlock)
                } else {
                    stringResource(R.string.onboarding_security_biometric_setup_hint)
                },
                checked = biometricLockEnabled,
                onCheckedChange = onBiometricLockEnabledChange,
                enabled = biometricAvailable,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.onboarding_security_auto_lock_timer),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                listOf(0L, 15_000L, 30_000L, 60_000L, 300_000L).forEach { duration ->
                    val selected = duration == autoLockTimerMs
                    OnboardingOptionCard(
                        label = when (duration) {
                            0L -> stringResource(R.string.onboarding_security_lock_never)
                            15_000L -> stringResource(R.string.onboarding_security_lock_15s)
                            30_000L -> stringResource(R.string.onboarding_security_lock_30s)
                            60_000L -> stringResource(R.string.onboarding_security_lock_1m)
                            300_000L -> stringResource(R.string.onboarding_security_lock_5m)
                            else -> "?"
                        },
                        selected = selected,
                        onClick = { onAutoLockTimerMsChange(duration) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
