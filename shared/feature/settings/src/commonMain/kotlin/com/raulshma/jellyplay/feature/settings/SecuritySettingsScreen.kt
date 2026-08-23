package com.raulshma.jellyplay.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayCircularProgressIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ImeAlertDialog
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_4_digit_pin
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_allow_remote_control
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_allow_remote_control_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_authorization_failed
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_authorize
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_authorize_device
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_authorize_device_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_authorize_quick_connect
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_authorize_quick_connect_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_authorizing
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_lock_timer
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_lock_timer_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_biometric_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_biometric_unlock
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_biometric_unlock_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cancel
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_confirm
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_confirm_pin
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_current_pin
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_disable_pin_lock
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_disable_pin_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_disabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_enable_biometric_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_enable_biometric_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_incorrect_pin
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_lock_1_minute
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_lock_10_minutes
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_lock_30_seconds
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_lock_5_minutes
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_lock_immediately
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_lock_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_no_pin_set
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pin_biometric_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pin_for_player_lock
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pin_lock
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pin_locked
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pin_must_be_4_digits
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pin_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pins_do_not_match
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quick_connect_authorizer
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quick_connect_authorizer_summary
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_quick_connect_code
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_remote_control
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_remote_control_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_remote_control_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_require_pin_player
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_security
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_set_pin
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_set_pin_lock
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_slide_to_unlock

/**
 * Custom (non-picker) dialogs only. Picker sheets (auto-lock timer, …) flow
 * through the shared `PickerState` dispatcher instead of this enum — keeping
 * the residual sealed-class role narrow: tagging genuinely bespoke dialogs
 * (`PickerState` has no variant for a PIN entry or a QuickConnect auth flow).
 */
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
    viewModel: SecuritySettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.securityPreferences.collectAsStateWithLifecycle()
    val showAdvanced by viewModel.showAdvancedSettings.collectAsStateWithLifecycle()
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    var activeDialog by remember { mutableStateOf<SecuritySettingsDialog>(SecuritySettingsDialog.None) }
    var activePicker by remember { mutableStateOf<PickerState<*>?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinDisableAuthError by remember { mutableStateOf<String?>(null) }
    var verifyingDisablePin by remember { mutableStateOf(false) }
    var qcCode by remember { mutableStateOf("") }
    var qcError by remember { mutableStateOf<String?>(null) }
    var qcLoading by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Null gate (desktop, or no biometric host) takes the legacy unavailable
    // path: the biometric row is hidden entirely, same as NO_HARDWARE/NO_ENROLLED.
    val biometricGate = rememberBiometricGate()
    val canShowBiometric = biometricGate?.isAvailable() == true
    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()

    val pinMustBe4Digits = stringResource(Res.string.settings_pin_must_be_4_digits)
    val pinsDoNotMatch = stringResource(Res.string.settings_pins_do_not_match)
    val incorrectPin = stringResource(Res.string.settings_incorrect_pin)
    val authorizationFailed = stringResource(Res.string.settings_authorization_failed)
    val biometricTitle = stringResource(Res.string.settings_enable_biometric_title)
    val biometricSubtitle = stringResource(Res.string.settings_enable_biometric_subtitle)
    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = 1,
        tag = "security_init",
    )

    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scrollIndex = rememberHighlightScrollIndex(
        highlightSettingId = highlightSettingId,
        groupSettingIds = listOf(
            setOf("pin_lock", "biometric_lock", "pin_for_player_lock", "auto_lock_timer"),
            setOf("quick_connect_authorize"),
            setOf("remote_control_enabled"),
        ),
    )
    HighlightScrollEffect(scrollState, scrollIndex)

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.settings_security),
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
                    title = stringResource(Res.string.settings_security),
                    summary = {
                        when {
                            preferences.pinLockEnabled && preferences.biometricLockEnabled -> stringResource(Res.string.settings_pin_biometric_on)
                            preferences.biometricLockEnabled -> stringResource(Res.string.settings_biometric_on)
                            preferences.pinLockEnabled -> stringResource(Res.string.settings_pin_on)
                            else -> stringResource(Res.string.settings_lock_off)
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
                        title = stringResource(Res.string.settings_pin_lock),
                        subtitle = if (preferences.pinLockEnabled) stringResource(Res.string.settings_pin_locked) else stringResource(Res.string.settings_no_pin_set),
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
                    if (canShowBiometric && biometricGate != null) {
                        SettingToggleItem(
                            icon = Tabler.Outline.Fingerprint,
                            title = stringResource(Res.string.settings_biometric_unlock),
                            subtitle = if (preferences.biometricLockEnabled) stringResource(Res.string.settings_biometric_unlock_subtitle) else stringResource(Res.string.settings_disabled),
                            checked = preferences.biometricLockEnabled,
                            highlighted = highlightSettingId == "biometric_lock",
                            index = secIdx++, count = secTotal,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    biometricGate.authenticate(
                                        title = biometricTitle,
                                        subtitle = biometricSubtitle,
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
                            title = stringResource(Res.string.settings_pin_for_player_lock),
                            subtitle = if (preferences.usePinForPlayerLock) stringResource(Res.string.settings_require_pin_player) else stringResource(Res.string.settings_slide_to_unlock),
                            checked = preferences.usePinForPlayerLock,
                            highlighted = highlightSettingId == "pin_for_player_lock",
                            index = secIdx++, count = secTotal,
                            onCheckedChange = { viewModel.setUsePinForPlayerLock(it) },
                        )
                    }
                    if (showAdvanced) {
                        val lockTimerOptions = listOf(0L, 30_000L, 60_000L, 300_000L, 600_000L)
                        val lockTimerLabels = listOf(
                            stringResource(Res.string.settings_lock_immediately),
                            stringResource(Res.string.settings_lock_30_seconds),
                            stringResource(Res.string.settings_lock_1_minute),
                            stringResource(Res.string.settings_lock_5_minutes),
                            stringResource(Res.string.settings_lock_10_minutes),
                        )
                        val autoLockTimerTitle = stringResource(Res.string.settings_auto_lock_timer)
                        SettingListItem(
                            icon = Tabler.Outline.Clock,
                            title = stringResource(Res.string.settings_auto_lock_timer),
                            subtitle = stringResource(Res.string.settings_auto_lock_timer_subtitle),
                            trailingText = lockTimerLabels[lockTimerOptions.indexOf(preferences.autoLockTimerMs).coerceAtMost(lockTimerOptions.lastIndex)],
                            highlighted = highlightSettingId == "auto_lock_timer",
                            index = secIdx, count = secTotal,
                            onClick = {
                                activePicker = PickerState.List(
                                    title = autoLockTimerTitle,
                                    items = lockTimerOptions,
                                    label = { lockTimerLabels[lockTimerOptions.indexOf(it).coerceAtMost(lockTimerOptions.lastIndex)] },
                                    isSelected = { it == preferences.autoLockTimerMs },
                                    onSelect = { viewModel.setAutoLockTimerMs(it) },
                                )
                            },
                        )
                    }
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.Bolt,
                    title = stringResource(Res.string.settings_quick_connect_authorizer),
                    summary = { stringResource(Res.string.settings_quick_connect_authorizer_summary) },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    SettingListItem(
                        icon = Tabler.Outline.DeviceDesktop,
                        title = stringResource(Res.string.settings_authorize_device),
                        subtitle = stringResource(Res.string.settings_authorize_device_subtitle),
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
                    title = stringResource(Res.string.settings_remote_control),
                    summary = {
                        if (preferences.remoteControlEnabled) stringResource(Res.string.settings_remote_control_on)
                        else stringResource(Res.string.settings_remote_control_off)
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    SettingToggleItem(
                        icon = Tabler.Outline.Cast,
                        title = stringResource(Res.string.settings_allow_remote_control),
                        subtitle = stringResource(Res.string.settings_allow_remote_control_subtitle),
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
        ImeAlertDialog(
            onDismissRequest = {
                activeDialog = SecuritySettingsDialog.None
                pinInput = ""
                pinConfirm = ""
                pinError = null
            },
            title = { Text(stringResource(Res.string.settings_set_pin_lock)) },
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
                        label = { Text(stringResource(Res.string.settings_4_digit_pin)) },
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
                        label = { Text(stringResource(Res.string.settings_confirm_pin)) },
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
                        pinInput.length != 4 -> pinError = pinMustBe4Digits
                        pinInput != pinConfirm -> pinError = pinsDoNotMatch
                        else -> {
                            viewModel.setPin(pinInput)
                            activeDialog = SecuritySettingsDialog.None
                            pinInput = ""
                            pinConfirm = ""
                            pinError = null
                        }
                    }
                }) { Text(stringResource(Res.string.settings_set_pin)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    activeDialog = SecuritySettingsDialog.None
                    pinInput = ""
                    pinConfirm = ""
                    pinError = null
                }) { Text(stringResource(Res.string.settings_cancel)) }
            },
        )
    }

    if (activeDialog is SecuritySettingsDialog.PinDisableAuth) {
        ImeAlertDialog(
            onDismissRequest = {
                activeDialog = SecuritySettingsDialog.None
                pinDisableAuthError = null
            },
            title = { Text(stringResource(Res.string.settings_disable_pin_lock)) },
            text = {
                Column {
                    Text(
                        stringResource(Res.string.settings_disable_pin_message),
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
                        label = { Text(stringResource(Res.string.settings_current_pin)) },
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
                TextButton(
                    enabled = !verifyingDisablePin,
                    onClick = {
                        if (verifyingDisablePin) return@TextButton
                        verifyingDisablePin = true
                        scope.launch {
                            val valid = viewModel.verifyPin(pinInput)
                            if (valid) {
                                viewModel.clearPin()
                                activeDialog = SecuritySettingsDialog.None
                                pinInput = ""
                                pinDisableAuthError = null
                            } else {
                                pinDisableAuthError = incorrectPin
                            }
                            verifyingDisablePin = false
                        }
                    },
                ) {
                    if (verifyingDisablePin) {
                        JellyPlayCircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Text(stringResource(Res.string.settings_confirm))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    activeDialog = SecuritySettingsDialog.None
                    pinInput = ""
                    pinDisableAuthError = null
                }) { Text(stringResource(Res.string.settings_cancel)) }
            },
        )
    }

    if (activeDialog is SecuritySettingsDialog.QuickConnectAuthorize) {
        ImeAlertDialog(
            onDismissRequest = {
                if (!qcLoading) {
                    activeDialog = SecuritySettingsDialog.None
                    qcCode = ""
                    qcError = null
                }
            },
            title = { Text(stringResource(Res.string.settings_authorize_quick_connect)) },
            text = {
                Column {
                    Text(
                        stringResource(Res.string.settings_authorize_quick_connect_message),
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
                        label = { Text(stringResource(Res.string.settings_quick_connect_code)) },
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
                                qcError = error ?: authorizationFailed
                            }
                        }
                    },
                ) {
                    Text(if (qcLoading) stringResource(Res.string.settings_authorizing) else stringResource(Res.string.settings_authorize))
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
                ) { Text(stringResource(Res.string.settings_cancel)) }
            },
        )
    }

    SettingsPickerDialog(
        state = activePicker,
        onDismiss = { activePicker = null },
    )
}
