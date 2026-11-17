package com.raulshma.jellyplay.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.raulshma.jellyplay.core.ui.components.JellyPlayCircularProgressIndicator
import com.raulshma.jellyplay.core.ui.components.ImeAlertDialog
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_4_digit_pin
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_allow_remote_control
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_authorization_failed
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_authorize
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_authorize_device
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_authorize_quick_connect
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_authorize_quick_connect_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_authorizing
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_lock_timer
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_remote_display_content
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_require_pin_player
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_security
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_set_pin
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_set_pin_lock
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_slide_to_unlock

/**
 * How a bespoke dialog exited — the input for
 * [SecurityDialogRules.exitClearsPinInput]'s survivor table.
 */
internal enum class DialogExit {
    /** Scrim tap / system back — the dialog's `onDismissRequest`. */
    Scrim,

    /** The dialog's Cancel (dismissButton) press. */
    Cancel,

    /** The dialog's affirmative action succeeded and closed it itself. */
    ConfirmSuccess,
}

/**
 * The security screen's bespoke dialog machines. ONE slot phase behind the
 * former `SecuritySettingsDialog` enum + its seven hand-settled satellite
 * `remember` fields (`pinConfirm` / `pinError` / `pinDisableAuthError` /
 * `verifyingDisablePin` / `qcCode` / `qcError` / `qcLoading` — the typed
 * `pinInput` buffer survives beside the slot, see its KDoc), whose clears
 * were pairwise hand-written at FIVE dismiss/confirm sites — each a
 * different subset. Now each dialog's
 * ephemeral state lives INSIDE its slot arm, so leaving the arm destroys it:
 * a forgotten clear at an exit is unrepresentable. Picker sheets (auto-lock
 * timer, …) still flow through the shared `PickerState` dispatcher, not this
 * slot — keeping its role narrow: genuinely bespoke dialogs (`PickerState`
 * has no variant for a PIN entry or a QuickConnect auth flow).
 */
sealed interface SecurityDialogPhase {
    /** No bespoke dialog open. */
    data object Closed : SecurityDialogPhase

    /**
     * The set-PIN dialog. Its confirm buffer + validation error live inside
     * the arm: every exit folds to [Closed] (the three former sites cleared
     * the identical set), so neither can outlive the dialog. The typed PIN
     * itself rides the screen's shared `pinInput` buffer — its per-exit
     * survivor table is [SecurityDialogRules.exitClearsPinInput].
     */
    data class SetPin(val confirm: String, val error: String?) : SecurityDialogPhase

    /**
     * The disable-PIN authorization dialog. Its error dies with the arm —
     * the former code cleared it on EVERY exit, scrim included — while the
     * typed PIN rides the shared buffer (the scrim exit's lone survivor:
     * reopening the dialog showed the retained entry, and still does).
     */
    data class DisablePinAuth(val error: String?) : SecurityDialogPhase

    /**
     * The Quick Connect authorize dialog; its machine is
     * [QuickConnectAuthorizePhase] (this screen's long-unmigrated twin of
     * the landed `SeerrBridgeQuickConnectPhase`). Dismissal is refused
     * exactly while the machine is Authorizing
     * ([SecurityDialogRules.qcDismissAllowed] — the former `if (!qcLoading)`
     * scrim guard).
     */
    data class QuickConnectAuthorize(val qc: QuickConnectAuthorizePhase) : SecurityDialogPhase
}

/**
 * The Quick Connect authorize machine — one sealed phase where the former
 * `qcCode`/`qcError`/`qcLoading` trio settled by hand (their illegal
 * combos — loading beside an error banner, loading beside a half-typed
 * code — are unrepresentable now). Shape mirrors the landed
 * `SeerrBridgeQuickConnectPhase`: an idle is this slot simply not being in
 * the QuickConnectAuthorize arm (the machine never exists without its
 * dialog), the in-code arms carry the typed code, and the failure face
 * KEEPS the code for the inline retry — exactly which arms keep the banner
 * vs clear it is preserved byte-for-byte (the confirm press cleared the
 * banner entering the in-flight arm; typing cleared it entering a fresh
 * entry arm; only the failure answer re-raised it).
 */
sealed interface QuickConnectAuthorizePhase {
    /** The typed code — carried by EVERY open arm (empty on a fresh dialog). */
    val code: String

    /** Dialog open, entry enabled, no banner — code may be empty or partial. */
    data class Entering(override val code: String) : QuickConnectAuthorizePhase

    /** The authorize call is in flight: entry disabled, dismiss refused, banner cleared at entry. */
    data class Authorizing(override val code: String) : QuickConnectAuthorizePhase

    /** The call answered failure: banner shows [error], [code] kept for the retry, entry re-enabled. */
    data class Failed(override val code: String, val error: String) : QuickConnectAuthorizePhase
}

/**
 * The pure decision rules for [SecurityDialogPhase] — the decision object
 * beside the phase types (StateManagementConventions §3), so the hand-settled
 * clear-sets the former five fields trusted to five different call sites are
 * pinned by tests (SecurityDialogSlotTest) instead of comments. This module
 * has no Compose screen harness; what is NOT unit-pinned is the composition
 * wiring itself (which callback fires which rule, the dialogs' modality and
 * render order, the disable-PIN verify latch's positional reset — flagged at
 * its declaration).
 */
internal object SecurityDialogRules {

    /** The PIN fields' input filter, byte-identical: up to 4 digits. */
    fun isLegalPin(input: String): Boolean = input.length <= 4 && input.all { it.isDigit() }

    /** The QC code's input filter, byte-identical: up to 6 digits. */
    fun isLegalQcCode(input: String): Boolean = input.length <= 6 && input.all { it.isDigit() }

    /**
     * The set-PIN confirm fork, in the former `when`'s order: a short PIN
     * wins over a mismatch (the length check ran first); `null` = proceed.
     */
    fun setPinViolation(
        input: String,
        confirm: String,
        lengthText: String,
        mismatchText: String,
    ): String? = when {
        input.length != 4 -> lengthText
        input != confirm -> mismatchText
        else -> null
    }

    /**
     * Whether an exit route clears the SHARED typed-PIN buffer: the set-PIN
     * arm clears on every exit (all three former sites cleared it); the
     * disable-PIN arm's scrim/back exit alone keeps it (the lone survivor —
     * the former scrim handler cleared only the error, so reopening the
     * dialog showed the retained entry, which the Cancel and confirm-success
     * exits cleared). Any other arm (nothing bespoke open, or the QC dialog
     * — which never touched the PIN buffer) keeps it trivially.
     */
    fun exitClearsPinInput(arm: SecurityDialogPhase, exit: DialogExit): Boolean = when (arm) {
        is SecurityDialogPhase.SetPin -> true
        is SecurityDialogPhase.DisablePinAuth -> exit != DialogExit.Scrim
        SecurityDialogPhase.Closed, is SecurityDialogPhase.QuickConnectAuthorize -> false
    }

    /** Typing in the set-PIN dialog's new-PIN field: the shared banner clears (the buffer write rides with it). */
    fun setPinInputChanged(phase: SecurityDialogPhase): SecurityDialogPhase =
        (phase as? SecurityDialogPhase.SetPin)?.copy(error = null) ?: phase

    /** Typing in the set-PIN dialog's confirm field: the buffer updates and the shared banner clears. */
    fun setPinConfirmChanged(phase: SecurityDialogPhase, confirm: String): SecurityDialogPhase =
        (phase as? SecurityDialogPhase.SetPin)?.copy(confirm = confirm, error = null) ?: phase

    /** Typing in the disable-PIN dialog's current-PIN field: its banner clears (the buffer write rides with it). */
    fun disableAuthInputChanged(phase: SecurityDialogPhase): SecurityDialogPhase =
        (phase as? SecurityDialogPhase.DisablePinAuth)?.copy(error = null) ?: phase

    /** Whether the QC code field + the dialog's exits are enabled — refused exactly while authorizing. */
    fun qcEntryEnabled(qc: QuickConnectAuthorizePhase): Boolean =
        qc !is QuickConnectAuthorizePhase.Authorizing

    /** Whether the QC confirm button is enabled — the former `!qcLoading && qcCode.length == 6`. */
    fun qcConfirmEnabled(qc: QuickConnectAuthorizePhase): Boolean =
        qc !is QuickConnectAuthorizePhase.Authorizing && qc.code.length == 6

    /**
     * Whether the QC dialog may dismiss (scrim/back/Cancel) — EXACTLY the
     * former `if (!qcLoading)` refusal: refused only while the authorize
     * call is in flight, allowed from Entering and from a Failed retry face.
     */
    fun qcDismissAllowed(qc: QuickConnectAuthorizePhase): Boolean =
        qc !is QuickConnectAuthorizePhase.Authorizing

    /** Typing in the QC code field: every entry arm lands a fresh Entering (the banner clears on typing); in-flight is a no-op. */
    fun qcEntryChanged(qc: QuickConnectAuthorizePhase, code: String): QuickConnectAuthorizePhase =
        when (qc) {
            is QuickConnectAuthorizePhase.Authorizing -> qc
            is QuickConnectAuthorizePhase.Entering -> QuickConnectAuthorizePhase.Entering(code)
            is QuickConnectAuthorizePhase.Failed -> QuickConnectAuthorizePhase.Entering(code)
        }

    /** The confirm press: the banner clears entering the in-flight arm, which carries the typed code. */
    fun qcStart(qc: QuickConnectAuthorizePhase): QuickConnectAuthorizePhase =
        QuickConnectAuthorizePhase.Authorizing(qc.code)

    /** The authorize call answered failure: the banner raises with [error] and the typed code is KEPT for the retry. */
    fun qcFailed(qc: QuickConnectAuthorizePhase, error: String): QuickConnectAuthorizePhase =
        QuickConnectAuthorizePhase.Failed(qc.code, error)
}

/**
 * The security screen's ONE ordered group declaration — the derivation-only
 * shape that BOTH the screen and the catalog contract test's section-7 pins
 * consume, so a reordered or re-flagged group cannot strand a stale replica
 * (the mirror-drift class ADR-0009's amendment closes). The lock slot carries
 * the declared security group's row count (its hidden delta is the auto-lock
 * timer's All(AppLock, Advanced) gate — 1 where the app-lock stack exists, 0
 * on desktop); the quick-connect and remote-control slots are content-only
 * (their rows always render — the shipped undeclared-count quirks, their
 * rows carry no admission). The lock slot's id set is deliberately narrower
 * than the declared security group's (it names only the rows the lock slot
 * renders — the quick-connect / remote-control rows sit in their own
 * trailing slots). Per-group row emission stays hand-written: the screen
 * maps each slot to its body inline (ADR-0009).
 */
internal val SecuritySettingsScreenSlots: List<PreferenceScreenGroup> = listOf(
    PreferenceScreenGroup(
        idSet = setOf(
            SecurityRows.PinLock.id,
            SecurityRows.BiometricLock.id,
            SecurityRows.PinForPlayerLock.id,
            SecurityRows.AutoLockTimer.id,
        ),
        emittedRows = { flags -> rowTotalFor(SettingsScreenGroups.security, flags) },
    ),
    PreferenceScreenGroup(idSet = setOf(SecurityRows.QuickConnectAuthorize.id)),
    PreferenceScreenGroup(
        idSet = setOf(
            SecurityRows.RemoteControlEnabled.id,
            SecurityRows.RemoteDisplayContentEnabled.id,
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SecuritySettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: SecuritySettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.securityPreferences.collectAsStateWithLifecycle()
    val showAdvanced by viewModel.showAdvancedSettings.collectAsStateWithLifecycle()
    var activeDialog by remember { mutableStateOf<SecurityDialogPhase>(SecurityDialogPhase.Closed) }
    var activePicker by remember { mutableStateOf<PickerState<*>?>(null) }

    /**
     * The typed-PIN buffer, shared by the set-PIN and disable-PIN dialogs'
     * entry fields. Deliberately OUTSIDE the [SecurityDialogPhase] arms: the
     * disable arm's scrim exit alone lets it survive the dialog (reopening
     * shows the retained entry — the lone survivor, and the only reason this
     * is a buffer rather than arm state). Which exit clears it is the pure
     * table [SecurityDialogRules.exitClearsPinInput]; opening either dialog
     * never touches it. The set-PIN arm can never actually observe a stale
     * value: it opens only while the PIN is disabled, and the disable arm
     * either cleared the buffer (Cancel / confirm success) or left the PIN
     * enabled (scrim), which reopens the disable arm.
     */
    var pinInput by remember { mutableStateOf("") }

    /**
     * The disable-PIN verify-flight latch — concurrency plumbing, NOT dialog
     * state (the HomeRefresher rule): it intentionally survives a scrim
     * dismissal that lands mid-verify (the verify callback's writes below
     * are global, so a re-opened dialog still shows the spinner and still
     * gets closed by the in-flight verify's success — byte-identical with
     * the former flat `remember` flag). Its reset is POSITIONAL (last line
     * of the verify callback, not a `finally`) — a thrown `verifyPin` would
     * strand it `true`; preserved verbatim and flagged here, not silently
     * "fixed".
     */
    var verifyingDisablePin by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    /**
     * Closes the open PIN arm through [SecurityDialogRules.exitClearsPinInput] —
     * the ONE funnel through which the shared buffer's survivor table applies
     * (the former five hand-settled dismiss/confirm sites' differing clear
     * subsets all fall out of this one rule).
     */
    fun closePinDialog(exit: DialogExit) {
        if (SecurityDialogRules.exitClearsPinInput(activeDialog, exit)) {
            pinInput = ""
        }
        activeDialog = SecurityDialogPhase.Closed
    }

val biometricGate = rememberBiometricGate()
    // Visibility rides the capability surface; the gate object stays for the
    // authenticate call. Desktop pins flag ⟺ null gate in
    // DesktopPlatformActualsTest; on Android a device without biometric
    // hardware still nulls the gate, so the row and its count both require
    // the gate before believing the flag.
    val canShowBiometric = settingsCapabilities.supportsBiometric && biometricGate != null
    // The declared row admissions both the SettingsItemList total and the
    // emission `if`s below read — one gate per id, declared beside the group
    // items (SettingsScreenGroups.security.rowAdmitted). pin_for_player_lock
    // stays hand-gated: it carries NO declared admission (the shipped count
    // quirk — it renders behind the pin toggle but is never counted), so its
    // desktop hiding rides the app-lock capability directly, same flag its
    // sibling rows declare.
    val securityRowFlags = RowAdmissionFlags(showAdvanced = showAdvanced, supportsBiometric = canShowBiometric)
    val pinMustBe4Digits = stringResource(Res.string.settings_pin_must_be_4_digits)
    val pinsDoNotMatch = stringResource(Res.string.settings_pins_do_not_match)
    val incorrectPin = stringResource(Res.string.settings_incorrect_pin)
    val authorizationFailed = stringResource(Res.string.settings_authorization_failed)
    val biometricTitle = stringResource(Res.string.settings_enable_biometric_title)
    val biometricSubtitle = stringResource(Res.string.settings_enable_biometric_subtitle)
    // The screen's group bodies mapped onto the ONE ordered declaration
    // ([SecuritySettingsScreenSlots], in LazyColumn order — the shell derives
    // the deep-link scroll resolution and the hidden-settings hint count from
    // it, and the contract test pins the same declaration). Slots stop at
    // GROUP grain: the row emission inside each slot is the hand-written
    // body, unchanged (ADR-0009).
    val securityGroups = SecuritySettingsScreenSlots.withContent(
        bodies = listOf(
        { activePicker ->
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
                    // Derived by rowTotalFor from the security group
                    // declaration (the shipped count quirks —
                    // pin_for_player_lock never admitted, the biometric count
                    // ignoring the runtime gate — encoded in the
                    // declaration).
                    SettingsItemList(total = rowTotalFor(SettingsScreenGroups.security, securityRowFlags)) {
                    if (SettingsScreenGroups.security.rowAdmitted(SecurityRows.PinLock.id, securityRowFlags)) {
                        SettingToggleItem(
                            icon = if (preferences.pinLockEnabled) Tabler.Outline.Lock else Tabler.Outline.LockOpen,
                            title = rowTitle(SecurityRows.PinLock),
                            subtitle = if (preferences.pinLockEnabled) stringResource(Res.string.settings_pin_locked) else stringResource(Res.string.settings_no_pin_set),
                            checked = preferences.pinLockEnabled,
                            highlighted = highlightSettingId == SecurityRows.PinLock.id,
                            onCheckedChange = { enabled ->
                                activeDialog = if (enabled) {
                                    SecurityDialogPhase.SetPin(confirm = "", error = null)
                                } else {
                                    SecurityDialogPhase.DisablePinAuth(error = null)
                                }
                            },
                            onClick = {
                                activeDialog = if (preferences.pinLockEnabled) {
                                    SecurityDialogPhase.DisablePinAuth(error = null)
                                } else {
                                    SecurityDialogPhase.SetPin(confirm = "", error = null)
                                }
                            },
                        )
                    }
                    if (SettingsScreenGroups.security.rowAdmitted(SecurityRows.BiometricLock.id, securityRowFlags) && biometricGate != null) {
                        SettingToggleItem(
                            icon = Tabler.Outline.Fingerprint,
                            title = rowTitle(SecurityRows.BiometricLock),
                            subtitle = if (preferences.biometricLockEnabled) stringResource(Res.string.settings_biometric_unlock_subtitle) else stringResource(Res.string.settings_disabled),
                            checked = preferences.biometricLockEnabled,
                            highlighted = highlightSettingId == SecurityRows.BiometricLock.id,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    biometricGate.authenticate(
                                        title = biometricTitle,
                                        subtitle = biometricSubtitle,
                                        onSuccess = { viewModel.edit { scope -> scope.security.setBiometricLockEnabled(true) } },
                                        onError = {},
                                        onFailed = {},
                                    )
                                } else {
                                    viewModel.edit { scope -> scope.security.setBiometricLockEnabled(false) }
                                }
                            },
                        )
                    }
                    // The undeclared-count-quirk row: hidden where the
                    // app-lock stack does not enforce (same capability its
                    // sibling rows declare, checked directly because the
                    // quirk keeps no admission to read).
                    if (preferences.pinLockEnabled && settingsCapabilities.supportsAppLock) {
                        SettingToggleItem(
                            icon = Tabler.Outline.Key,
                            title = rowTitle(SecurityRows.PinForPlayerLock),
                            subtitle = if (preferences.usePinForPlayerLock) stringResource(Res.string.settings_require_pin_player) else stringResource(Res.string.settings_slide_to_unlock),
                            checked = preferences.usePinForPlayerLock,
                            highlighted = highlightSettingId == SecurityRows.PinForPlayerLock.id,
                            onCheckedChange = { enabled ->
                                viewModel.edit { scope -> scope.security.setUsePinForPlayerLock(enabled) }
                            },
                        )
                    }
                    if (SettingsScreenGroups.security.rowAdmitted(SecurityRows.AutoLockTimer.id, securityRowFlags)) {
                        // The whole shared ladder (the screensaver dim-after row
                        // takes the same ladder minus the 10-minute rung), each
                        // rung zipped to its label so every lookup keys on the
                        // ms VALUE, never a hand-built index pairing. An
                        // off-ladder stored value degrades to "Immediately".
                        val lockImmediatelyLabel = stringResource(Res.string.settings_lock_immediately)
                        val lockTimerChoices = SETTINGS_TIMER_LADDER_MS.zip(
                            listOf(
                                lockImmediatelyLabel,
                                stringResource(Res.string.settings_lock_30_seconds),
                                stringResource(Res.string.settings_lock_1_minute),
                                stringResource(Res.string.settings_lock_5_minutes),
                                stringResource(Res.string.settings_lock_10_minutes),
                            ),
                        )
                        fun lockTimerLabel(ms: Long): String =
                            lockTimerChoices.firstOrNull { it.first == ms }?.second ?: lockImmediatelyLabel
                        val autoLockTimerTitle = rowTitle(SecurityRows.AutoLockTimer)
                        SettingListItem(
                            icon = Tabler.Outline.Clock,
                            title = rowTitle(SecurityRows.AutoLockTimer),
                            subtitle = rowSubtitle(SecurityRows.AutoLockTimer),
                            trailingText = lockTimerLabel(preferences.autoLockTimerMs),
                            highlighted = highlightSettingId == SecurityRows.AutoLockTimer.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = autoLockTimerTitle,
                                    items = lockTimerChoices.map { it.first },
                                    label = { lockTimerLabel(it) },
                                    isSelected = { it == preferences.autoLockTimerMs },
                                    onSelect = { ms ->
                                        viewModel.edit { scope -> scope.security.setAutoLockTimerMs(ms) }
                                    },
                                )
                            },
                        )
                    }
                    }
                }
        },
        { _ ->
                SettingsGroup(
                    icon = Tabler.Outline.Bolt,
                    title = stringResource(Res.string.settings_quick_connect_authorizer),
                    summary = { stringResource(Res.string.settings_quick_connect_authorizer_summary) },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    SettingListItem(
                        icon = Tabler.Outline.DeviceDesktop,
                        title = rowTitle(SecurityRows.QuickConnectAuthorize),
                        subtitle = rowSubtitle(SecurityRows.QuickConnectAuthorize),
                        trailingText = "",
                        highlighted = highlightSettingId == SecurityRows.QuickConnectAuthorize.id,
                        index = 0, count = 1,
                        onClick = {
                            // A fresh entry face per opening — the former
                            // qcCode/qcError reset-before-open writes, now
                            // structural (a new arm constructs its own empty
                            // Entering).
                            activeDialog = SecurityDialogPhase.QuickConnectAuthorize(
                                QuickConnectAuthorizePhase.Entering(code = ""),
                            )
                        },
                    )
                }
        },
        { _ ->
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
                        title = rowTitle(SecurityRows.RemoteControlEnabled),
                        subtitle = rowSubtitle(SecurityRows.RemoteControlEnabled),
                        checked = preferences.remoteControlEnabled,
                        highlighted = highlightSettingId == SecurityRows.RemoteControlEnabled.id,
                        index = 0, count = 2,
                        onCheckedChange = { enabled ->
                            viewModel.edit { scope -> scope.security.setRemoteControlEnabled(enabled) }
                        },
                    )
                    // the DisplayContent opt-in — a remote may navigate
                    // this device to an item's detail page while it is idle.
                    // Sits beside the master switch it depends on (the
                    // receiver gates on BOTH).
                    SettingToggleItem(
                        icon = Tabler.Outline.DeviceTv,
                        title = rowTitle(SecurityRows.RemoteDisplayContentEnabled),
                        subtitle = rowSubtitle(SecurityRows.RemoteDisplayContentEnabled),
                        checked = preferences.remoteDisplayContentEnabled,
                        highlighted = highlightSettingId == SecurityRows.RemoteDisplayContentEnabled.id,
                        index = 1, count = 2,
                        onCheckedChange = { enabled ->
                            viewModel.edit { scope -> scope.security.setRemoteDisplayContentEnabled(enabled) }
                        },
                    )
                }
        },
        ),
    )

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_security),
        onBack = onBack,
        focusTag = "security_init",
        highlightSettingId = highlightSettingId,
        groups = securityGroups,
        advancedToggle = PreferenceAdvancedToggle(
            showAdvanced = showAdvanced,
            onToggle = { viewModel.setShowAdvancedSettings(!showAdvanced) },
        ),
        hiddenHint = PreferenceHiddenHint(
            // Derived in the shell from the declaration above: the auto-lock
            // timer's All(AppLock, Advanced) gate — 1 where the app-lock
            // stack exists, 0 on desktop.
            rowFlags = securityRowFlags,
            onShowAdvanced = { viewModel.setShowAdvancedSettings(true) },
        ),
        pickerHost = true,
    )

    val setPinDialog = activeDialog as? SecurityDialogPhase.SetPin
    if (setPinDialog != null) {
        ImeAlertDialog(
            onDismissRequest = { closePinDialog(DialogExit.Scrim) },
            title = { Text(stringResource(Res.string.settings_set_pin_lock)) },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (SecurityDialogRules.isLegalPin(it)) {
                                pinInput = it
                                activeDialog = SecurityDialogRules.setPinInputChanged(activeDialog)
                            }
                        },
                        label = { Text(stringResource(Res.string.settings_4_digit_pin)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = setPinDialog.error != null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = setPinDialog.confirm,
                        onValueChange = {
                            if (SecurityDialogRules.isLegalPin(it)) {
                                activeDialog = SecurityDialogRules.setPinConfirmChanged(activeDialog, it)
                            }
                        },
                        label = { Text(stringResource(Res.string.settings_confirm_pin)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = setPinDialog.error != null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AnimatedVisibility(
                        visible = setPinDialog.error != null,
                        enter = expandVertically(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = shrinkVertically(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
                    ) {
                        setPinDialog.error?.let { error ->
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
                    val violation = SecurityDialogRules.setPinViolation(
                        input = pinInput,
                        confirm = setPinDialog.confirm,
                        lengthText = pinMustBe4Digits,
                        mismatchText = pinsDoNotMatch,
                    )
                    if (violation != null) {
                        activeDialog = setPinDialog.copy(error = violation)
                    } else {
                        // Captured before the async edit launches — the
                        // dialog state reset below would otherwise race
                        // the fire-and-forget store write.
                        val newPin = pinInput
                        viewModel.edit { scope -> scope.security.setPin(newPin) }
                        closePinDialog(DialogExit.ConfirmSuccess)
                    }
                }) { Text(stringResource(Res.string.settings_set_pin)) }
            },
            dismissButton = {
                TextButton(onClick = { closePinDialog(DialogExit.Cancel) }) {
                    Text(stringResource(Res.string.settings_cancel))
                }
            },
        )
    }

    val disablePinDialog = activeDialog as? SecurityDialogPhase.DisablePinAuth
    if (disablePinDialog != null) {
        ImeAlertDialog(
            onDismissRequest = { closePinDialog(DialogExit.Scrim) },
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
                            if (SecurityDialogRules.isLegalPin(it)) {
                                pinInput = it
                                activeDialog = SecurityDialogRules.disableAuthInputChanged(activeDialog)
                            }
                        },
                        label = { Text(stringResource(Res.string.settings_current_pin)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = disablePinDialog.error != null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AnimatedVisibility(
                        visible = disablePinDialog.error != null,
                        enter = expandVertically(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = shrinkVertically(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
                    ) {
                        disablePinDialog.error?.let { error ->
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
                                viewModel.edit { scope -> scope.security.clearPin() }
                                closePinDialog(DialogExit.ConfirmSuccess)
                            } else {
                                // The failure face lands only while the dialog
                                // is still the open arm — a scrim dismissal
                                // that raced the verify can no longer plant a
                                // ghost error into a future session (the
                                // former global `pinDisableAuthError` write
                                // did exactly that).
                                (activeDialog as? SecurityDialogPhase.DisablePinAuth)
                                    ?.copy(error = incorrectPin)
                                    ?.let { activeDialog = it }
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
                TextButton(onClick = { closePinDialog(DialogExit.Cancel) }) {
                    Text(stringResource(Res.string.settings_cancel))
                }
            },
        )
    }

    val qcDialog = activeDialog as? SecurityDialogPhase.QuickConnectAuthorize
    if (qcDialog != null) {
        val qc = qcDialog.qc
        ImeAlertDialog(
            onDismissRequest = {
                // The dismiss refusal rides the machine: refused exactly
                // while Authorizing (the former `if (!qcLoading)`).
                if (SecurityDialogRules.qcDismissAllowed(qc)) {
                    activeDialog = SecurityDialogPhase.Closed
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
                        value = qc.code,
                        onValueChange = {
                            if (SecurityDialogRules.isLegalQcCode(it)) {
                                activeDialog = SecurityDialogPhase.QuickConnectAuthorize(
                                    SecurityDialogRules.qcEntryChanged(qc, it),
                                )
                            }
                        },
                        label = { Text(stringResource(Res.string.settings_quick_connect_code)) },
                        singleLine = true,
                        isError = qc is QuickConnectAuthorizePhase.Failed,
                        enabled = SecurityDialogRules.qcEntryEnabled(qc),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AnimatedVisibility(
                        visible = qc is QuickConnectAuthorizePhase.Failed,
                        enter = expandVertically(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = shrinkVertically(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
                    ) {
                        (qc as? QuickConnectAuthorizePhase.Failed)?.error?.let { error ->
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
                    enabled = SecurityDialogRules.qcConfirmEnabled(qc),
                    onClick = {
                        val started = SecurityDialogRules.qcStart(qc)
                        activeDialog = SecurityDialogPhase.QuickConnectAuthorize(started)
                        viewModel.authorizeQuickConnect(started.code) { success, error ->
                            if (success) {
                                // The code + any face die with the arm — the
                                // former close's qcCode=""/qcError=null reset.
                                activeDialog = SecurityDialogPhase.Closed
                            } else {
                                // The banner re-raises and the code is KEPT
                                // for the inline retry (the failure face's
                                // preserved keep-vs-clear behavior).
                                activeDialog = SecurityDialogPhase.QuickConnectAuthorize(
                                    SecurityDialogRules.qcFailed(started, error ?: authorizationFailed),
                                )
                            }
                        }
                    },
                ) {
                    Text(
                        if (qc is QuickConnectAuthorizePhase.Authorizing) {
                            stringResource(Res.string.settings_authorizing)
                        } else {
                            stringResource(Res.string.settings_authorize)
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = SecurityDialogRules.qcDismissAllowed(qc),
                    onClick = { activeDialog = SecurityDialogPhase.Closed },
                ) { Text(stringResource(Res.string.settings_cancel)) }
            },
        )
    }

}
