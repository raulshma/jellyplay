package com.raulshma.jellyplay.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.BiometricAuthHelper
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.findFragmentActivity
import com.raulshma.jellyplay.core.ui.components.rememberBiometricAvailability
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

sealed class SecuritySettingsDialog {
    object None : SecuritySettingsDialog()
    object PinDialog : SecuritySettingsDialog()
    object PinDisableAuth : SecuritySettingsDialog()
    object QuickConnectAuthorize : SecuritySettingsDialog()
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SecuritySettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences = viewModel.preferences
    val showAdvanced = preferences.showAdvancedSettings
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    var activeDialog by remember { mutableStateOf<SecuritySettingsDialog>(SecuritySettingsDialog.None) }
    var pinInput by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinDisableAuthError by remember { mutableStateOf<String?>(null) }
    var qcCode by remember { mutableStateOf("") }
    var qcError by remember { mutableStateOf<String?>(null) }
    var qcLoading by remember { mutableStateOf(false) }

    val biometricAvailability = rememberBiometricAvailability()
    val canShowBiometric = biometricAvailability == BiometricAuthHelper.Availability.AVAILABLE
    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()
    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = 1,
        tag = "security_init",
    )

    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scrollIndex = remember(highlightSettingId) {
        // 0 = Security (PIN/Biometric/Auto-lock), 1 = Quick Connect Authorizer, 2 = Remote Control.
        when (highlightSettingId) {
            in listOf("pin_lock", "biometric_lock", "pin_for_player_lock", "auto_lock_timer") -> 0
            "quick_connect_authorize" -> 1
            "remote_control_enabled" -> 2
            else -> -1
        }
    }

    // Phase 1 (coarse): scroll the containing group into the LazyColumn's composition window so the
    // target item is actually composed — items in off-screen groups (later sections) are otherwise
    // never mounted and their bringIntoViewRequester has no target. Phase 2 (centering) is then
    // performed by the highlighted item itself via CenterBringIntoViewSpec.
    LaunchedEffect(scrollIndex) {
        if (scrollIndex >= 0) {
            try {
                scrollState.animateScrollToItem(scrollIndex)
            } catch (_: Exception) {}
        }
    }

    JellyPlayScreenScaffold(
        title = "Security",
        onBack = onBack,
        backgroundColor = backgroundColor,
        actions = {
            AdvancedSettingsToggleButton(
                showAdvanced = showAdvanced,
                onToggle = { viewModel.setShowAdvancedSettings(!showAdvanced) },
            )
        },
    ) { innerPadding ->
        // Center a highlighted (search-navigated) setting in the viewport instead of parking it
        // at the bottom edge, which is the default BringIntoViewSpec behaviour.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides
                com.raulshma.jellyplay.core.ui.tv.CenterBringIntoViewSpec
        ) {
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .tvFocusRestorer()
                .focusRequester(focusRequester),
            contentPadding = PaddingValues(
                start = adaptiveInfo.contentPadding(isTv),
                end = adaptiveInfo.contentPadding(isTv),
                bottom = adaptiveInfo.bottomPadding(isTv),
            ),
        ) {
            item {
                SettingsGroup(
                    icon = Tabler.Outline.Lock,
                    title = "Security",
                    summary = {
                        when {
                            preferences.pinLockEnabled && preferences.biometricLockEnabled -> "PIN + Biometric lock: On"
                            preferences.biometricLockEnabled -> "Biometric lock: On"
                            preferences.pinLockEnabled -> "PIN lock: On"
                            else -> "Lock: Off"
                        }
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    val baseSecTotal = if (canShowBiometric) 2 else 1
                    val secTotal = if (showAdvanced) baseSecTotal + 1 else baseSecTotal
                    var secIdx = 0
                    SettingToggleItem(
                        icon = if (preferences.pinLockEnabled) Tabler.Outline.Lock else Tabler.Outline.LockOpen,
                        title = "PIN Lock",
                        subtitle = if (preferences.pinLockEnabled) "App locked with PIN" else "No PIN set",
                        checked = preferences.pinLockEnabled,
                        highlighted = highlightSettingId == "pin_lock",
                        index = secIdx++, count = secTotal,
                        onCheckedChange = { enabled ->
                            if (enabled) activeDialog = SecuritySettingsDialog.PinDialog
                            else activeDialog = SecuritySettingsDialog.PinDisableAuth
                        },
                        onClick = {
                            if (preferences.pinLockEnabled) activeDialog = SecuritySettingsDialog.PinDisableAuth
                            else activeDialog = SecuritySettingsDialog.PinDialog
                        },
                    )
                    if (canShowBiometric) {
                        val bioContext = LocalContext.current
                        val bioActivity = remember(bioContext) { bioContext.findFragmentActivity() }
                        SettingToggleItem(
                            icon = Tabler.Outline.Fingerprint,
                            title = "Biometric Unlock",
                            subtitle = if (preferences.biometricLockEnabled) "Use fingerprint, face, or device credential" else "Disabled",
                            checked = preferences.biometricLockEnabled,
                            highlighted = highlightSettingId == "biometric_lock",
                            index = secIdx++, count = secTotal,
                            onCheckedChange = { enabled ->
                                if (enabled && bioActivity != null) {
                                    BiometricAuthHelper.authenticate(
                                        activity = bioActivity,
                                        title = "Enable Biometric Unlock",
                                        subtitle = "Verify your identity to enable biometric lock",
                                        onSuccess = { viewModel.setBiometricLockEnabled(true) },
                                        onError = {},
                                        onFailed = {},
                                    )
                                } else {
                                    viewModel.setBiometricLockEnabled(false)
                                }
                            },
                        )
                    }
                    if (preferences.pinLockEnabled) {
                        SettingToggleItem(
                            icon = Tabler.Outline.Key,
                            title = "PIN for Player Lock",
                            subtitle = if (preferences.usePinForPlayerLock) "Require PIN to unlock player" else "Slide to unlock",
                            checked = preferences.usePinForPlayerLock,
                            highlighted = highlightSettingId == "pin_for_player_lock",
                            index = secIdx++, count = secTotal,
                            onCheckedChange = { viewModel.setUsePinForPlayerLock(it) },
                        )
                    }
                    if (showAdvanced) {
                        val lockTimerOptions = listOf(0L, 30_000L, 60_000L, 300_000L, 600_000L)
                        val lockTimerLabels = listOf("Immediately", "30 seconds", "1 minute", "5 minutes", "10 minutes")
                        SettingListItem(
                            icon = Tabler.Outline.Clock,
                            title = "Auto-Lock Timer",
                            subtitle = "Time before app locks after leaving",
                            trailingText = lockTimerLabels[lockTimerOptions.indexOf(preferences.autoLockTimerMs).coerceAtMost(lockTimerOptions.lastIndex)],
                            highlighted = highlightSettingId == "auto_lock_timer",
                            index = secIdx, count = secTotal,
                            onClick = {
                                val currentIdx = lockTimerOptions.indexOf(preferences.autoLockTimerMs).coerceAtMost(lockTimerOptions.lastIndex)
                                val nextIdx = (currentIdx + 1) % lockTimerOptions.size
                                viewModel.setAutoLockTimerMs(lockTimerOptions[nextIdx])
                            },
                        )
                    }
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.Bolt,
                    title = "Quick Connect Authorizer",
                    summary = { "Approve Quick Connect codes from other devices" },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    SettingListItem(
                        icon = Tabler.Outline.DeviceDesktop,
                        title = "Authorize Device",
                        subtitle = "Enter a Quick Connect code to approve another device",
                        trailingText = "",
                        highlighted = highlightSettingId == "quick_connect_authorize",
                        index = 0, count = 1,
                        onClick = {
                            qcCode = ""
                            qcError = null
                            activeDialog = SecuritySettingsDialog.QuickConnectAuthorize
                        },
                    )
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.Cast,
                    title = "Remote Control",
                    summary = {
                        if (preferences.remoteControlEnabled) "This device can be controlled from other sessions"
                        else "Only this device can control playback"
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    SettingToggleItem(
                        icon = Tabler.Outline.Cast,
                        title = "Allow Remote Control",
                        subtitle = "Let other sessions play, pause and seek on this device",
                        checked = preferences.remoteControlEnabled,
                        highlighted = highlightSettingId == "remote_control_enabled",
                        index = 0, count = 1,
                        onCheckedChange = { viewModel.setRemoteControlEnabled(it) },
                    )
                }
            }

            if (!showAdvanced) {
                item {
                    HiddenSettingsHint(
                        hiddenCount = 1,
                        onShowAdvanced = { viewModel.setShowAdvancedSettings(true) },
                    )
                }
            }
        }
        }
    }

    if (activeDialog is SecuritySettingsDialog.PinDialog) {
        AlertDialog(
            onDismissRequest = {
                activeDialog = SecuritySettingsDialog.None
                pinInput = ""
                pinConfirm = ""
                pinError = null
            },
            title = { Text("Set PIN Lock") },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                pinInput = it
                                pinError = null
                            }
                        },
                        label = { Text("4-digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = pinError != null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = pinConfirm,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                pinConfirm = it
                                pinError = null
                            }
                        },
                        label = { Text("Confirm PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = pinError != null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AnimatedVisibility(
                        visible = pinError != null,
                        enter = expandVertically(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = shrinkVertically(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
                    ) {
                        pinError?.let { error ->
                            Text(
                                error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        pinInput.length != 4 -> pinError = "PIN must be 4 digits"
                        pinInput != pinConfirm -> pinError = "PINs do not match"
                        else -> {
                            viewModel.setPin(pinInput)
                            activeDialog = SecuritySettingsDialog.None
                            pinInput = ""
                            pinConfirm = ""
                            pinError = null
                        }
                    }
                }) { Text("Set PIN") }
            },
            dismissButton = {
                TextButton(onClick = {
                    activeDialog = SecuritySettingsDialog.None
                    pinInput = ""
                    pinConfirm = ""
                    pinError = null
                }) { Text("Cancel") }
            },
        )
    }

    if (activeDialog is SecuritySettingsDialog.PinDisableAuth) {
        AlertDialog(
            onDismissRequest = {
                activeDialog = SecuritySettingsDialog.None
                pinDisableAuthError = null
            },
            title = { Text("Disable PIN Lock") },
            text = {
                Column {
                    Text(
                        "Enter your current PIN to disable PIN lock.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                pinInput = it
                                pinDisableAuthError = null
                            }
                        },
                        label = { Text("Current PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = pinDisableAuthError != null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AnimatedVisibility(
                        visible = pinDisableAuthError != null,
                        enter = expandVertically(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = shrinkVertically(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
                    ) {
                        pinDisableAuthError?.let { error ->
                            Text(
                                error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val valid = viewModel.verifyPin(pinInput)
                    if (valid) {
                        viewModel.clearPin()
                        activeDialog = SecuritySettingsDialog.None
                        pinInput = ""
                        pinDisableAuthError = null
                    } else {
                        pinDisableAuthError = "Incorrect PIN"
                    }
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = {
                    activeDialog = SecuritySettingsDialog.None
                    pinInput = ""
                    pinDisableAuthError = null
                }) { Text("Cancel") }
            },
        )
    }

    if (activeDialog is SecuritySettingsDialog.QuickConnectAuthorize) {
        AlertDialog(
            onDismissRequest = {
                if (!qcLoading) {
                    activeDialog = SecuritySettingsDialog.None
                    qcCode = ""
                    qcError = null
                }
            },
            title = { Text("Authorize Quick Connect") },
            text = {
                Column {
                    Text(
                        "Enter the 6-digit code displayed on the other device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = qcCode,
                        onValueChange = {
                            if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                qcCode = it
                                qcError = null
                            }
                        },
                        label = { Text("Quick Connect Code") },
                        singleLine = true,
                        isError = qcError != null,
                        enabled = !qcLoading,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AnimatedVisibility(
                        visible = qcError != null,
                        enter = expandVertically(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = shrinkVertically(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
                    ) {
                        qcError?.let { error ->
                            Text(
                                error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !qcLoading && qcCode.length == 6,
                    onClick = {
                        qcLoading = true
                        qcError = null
                        viewModel.authorizeQuickConnect(qcCode) { success, error ->
                            qcLoading = false
                            if (success) {
                                activeDialog = SecuritySettingsDialog.None
                                qcCode = ""
                            } else {
                                qcError = error ?: "Authorization failed"
                            }
                        }
                    },
                ) {
                    Text(if (qcLoading) "Authorizing..." else "Authorize")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !qcLoading,
                    onClick = {
                        activeDialog = SecuritySettingsDialog.None
                        qcCode = ""
                        qcError = null
                    },
                ) { Text("Cancel") }
            },
        )
    }
}
