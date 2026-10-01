package com.raulshma.jellyplay.shell

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter
import com.raulshma.jellyplay.core.ui.components.AuthChallengeScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The app-lock gate's composition — everything MainActivity's `setContent`
 * used to inline between the `if (showLockScreen)` fork's braces: the
 * lockout clock loop, the error-message fork (a live lockout countdown wins
 * over the last submit error), the biometric-vs-PIN title fork and the
 * verify-spinner choreography. The lock DECISIONS stay where they were:
 * [PinGateController] owns the submit fold (its pure display folds are
 * pinned by its own suite); this composable is only the composition that
 * feeds them a live clock and renders the outcome. The overlay-host pattern
 * (JellyPlayApp's BackExitHost / UpdateSheetOverlay): the gate is a host
 * composable beside its controller, not logic smeared through the activity.
 *
 * Owns the gate's two ephemeral fields ([pinError] / [pinVerifying] —
 * rememberSaveable). They previously sat at setContent scope, outside the
 * gate fork; every reachable path clears both before the gate hides (the
 * submit fold sets pinVerifying false on every outcome and the unlocked
 * callback nulls pinError), so declaring them inside the gate — where they
 * are discarded on unlock and start fresh on re-lock — is observable
 * nowhere.
 *
 * No behavior change: same states, same remember keys, same 1 s clock
 * granularity, same string resources, same Main.immediate dispatch (the
 * caller passes its lifecycleScope).
 *
 * @param pinHash the configured PIN hash (null when the user chose
 *   biometric-only — flips the title to the biometric string).
 * @param biometricLockEnabled whether biometric unlock is enabled.
 * @param pinLockoutUntilEpochMs the persisted lockout deadline — the key
 *   the limiter's lockout re-read rides.
 * @param pinRateLimiter the attempt-counter/lockout store.
 * @param pinGateController the submit fold (click-time lockout re-check +
 *   off-main verify + failure/success accounting).
 * @param submitScope the scope the verification launches in — the
 *   activity's lifecycleScope (its Main.immediate dispatch runs the
 *   empty-PIN instant unlock in this frame).
 */
@Composable
internal fun LockGateHost(
    pinHash: String?,
    biometricLockEnabled: Boolean,
    pinLockoutUntilEpochMs: Long,
    pinRateLimiter: PinRateLimiter,
    pinGateController: PinGateController,
    submitScope: CoroutineScope,
) {
    var pinError by rememberSaveable { mutableStateOf<String?>(null) }
    var pinVerifying by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    // Surface the rate-limit lockout to the user when present.
    val lockoutState = remember(pinLockoutUntilEpochMs) {
        pinRateLimiter.getPinLockoutState()
    }
    // Live clock for the lockout display. The former
    // `remember { System.currentTimeMillis() }` computed "now"
    // ONCE per composition, so a lockout that expired while
    // the gate stayed up kept `enabled` false (keypad dead)
    // and the countdown message stale until the pref itself
    // changed. The tick is 1s-granular, restarts whenever the
    // limiter state changes (the remember key above), and
    // self-terminates the moment no lockout holds — an
    // unlocked gate never ticks.
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lockoutState) {
        while (PinGateController.lockoutRemainingMs(lockoutState, nowMs) > 0L) {
            delay(1_000L)
            nowMs = System.currentTimeMillis()
        }
    }
    // Pure display decision (pinned beside PinGateController's
    // other folds): the composition only supplies the live
    // clock; the keypad revives when this hits zero.
    val lockoutRemainingMs = PinGateController.lockoutRemainingMs(lockoutState, nowMs)
    val lockoutActive = lockoutRemainingMs > 0L
    AuthChallengeScreen(
        title = if (biometricLockEnabled && pinHash == null) {
            stringResource(R.string.auth_title_biometric)
        } else {
            stringResource(R.string.auth_title_pin)
        },
        subtitle = stringResource(R.string.auth_subtitle),
        pinHash = pinHash,
        biometricEnabled = biometricLockEnabled,
        enabled = !lockoutActive && !pinVerifying,
        verifying = pinVerifying,
        onPinEntered = { pin ->
            // Both paths fold through the controller — it owns
            // the click-time lockout re-check and the
            // failure/success accounting.
            if (pin.isEmpty()) {
                // The empty-PIN shortcut shows no spinner (the old
                // inline path unlocked synchronously);
                // lifecycleScope's Main.immediate dispatch runs it
                // in this frame.
                submitScope.launch {
                    pinGateController.submit(pin, onUnlocked = { pinError = null })
                }
            } else if (pinHash != null && !pinVerifying) {
                pinVerifying = true
                submitScope.launch {
                    when (
                        val outcome = pinGateController.submit(pin, onUnlocked = { pinError = null })
                    ) {
                        PinGateController.PinSubmitOutcome.Unlocked -> Unit
                        PinGateController.PinSubmitOutcome.Incorrect ->
                            pinError = context.getString(R.string.pin_incorrect)
                        is PinGateController.PinSubmitOutcome.LockedOut ->
                            pinError = PinGateController
                                .lockoutMessage(outcome.remainingMs)
                                .resolve(context)
                    }
                    pinVerifying = false
                }
            }
        },
        onErrorClear = { pinError = null },
        errorMessage = if (lockoutActive) {
            PinGateController.lockoutMessage(lockoutRemainingMs)
                .resolve(context)
        } else {
            pinError
        },
    )
}

/**
 * String-side twin of [PinGateController.lockoutMessage] — resolves the pure
 * fold's buckets against the app resources (same `pin_lockout_*` strings the
 * former in-activity formatter produced).
 */
private fun PinGateController.LockoutMessage.resolve(context: Context): String = when (this) {
    is PinGateController.LockoutMessage.Now -> context.getString(R.string.pin_lockout_now)
    is PinGateController.LockoutMessage.Seconds -> context.getString(R.string.pin_lockout_seconds, seconds)
    is PinGateController.LockoutMessage.Minutes -> context.getString(R.string.pin_lockout_minutes, minutes)
    is PinGateController.LockoutMessage.Hours -> context.getString(R.string.pin_lockout_hours, hours)
    is PinGateController.LockoutMessage.HoursMinutes ->
        context.getString(R.string.pin_lockout_hours_minutes, hours, minutes)
}
