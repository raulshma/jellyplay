package com.raulshma.jellyplay.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the security screen's dialog slot ([SecurityDialogPhase]) and the
 * Quick Connect authorize machine ([QuickConnectAuthorizePhase] — the
 * screen-local twin of the landed `SeerrBridgeQuickConnectPhase`) through
 * their pure decision object ([SecurityDialogRules]) — per
 * StateManagementConventions §3. This module has no Compose screen harness,
 * so what is unit-pinned here is exactly the hand-settled logic the former
 * seven satellite `remember` fields trusted to five different
 * dismiss/confirm call sites (each clearing a different subset):
 *
 *  - the input filters (4-digit PIN, 6-digit QC code) byte-identical;
 *  - the set-PIN confirm fork's ORDER (a short PIN wins over a mismatch);
 *  - the SHARED typed-PIN buffer's per-exit survivor table — the set-PIN arm
 *    clears on every exit, the disable arm's scrim/back exit alone keeps it
 *    (reopening showed the retained entry), Cancel/confirm-success clear it;
 *  - typing clears whichever banner the open PIN arm holds;
 *  - the QC dismiss refusal — EXACTLY while Authorizing (the former
 *    `if (!qcLoading)` scrim/cancel gate), allowed from Entering and from a
 *    Failed retry face;
 *  - the QC machine's keep-vs-clear behavior: the confirm press clears the
 *    banner entering the in-flight arm (which carries the typed code), the
 *    failure answer re-raises the banner and KEEPS the code, typing lands a
 *    fresh Entering (clearing the banner) — and the in-flight arm cannot
 *    dismiss or type (field disabled), so `loading && error` and
 *    `loading && partial-code` are unrepresentable;
 *  - the QC confirm enablement (`!loading && code.length == 6`).
 *
 * Comment-pinned (composition facts, declared at the slot's KDoc and the
 * render site): which callback fires which rule, the dialogs' modality and
 * render order, the one-slot mutual exclusion (Closed/SetPin/DisablePinAuth/
 * QuickConnectAuthorize), the QC success closing the slot, and the
 * disable-PIN verify latch (`verifyingDisablePin` — concurrency plumbing,
 * positional reset preserved and flagged).
 */
class SecurityDialogSlotTest {

    // ── input filters ──

    @Test
    fun `pin filter accepts up to 4 digits only`() {
        assertTrue(SecurityDialogRules.isLegalPin(""))
        assertTrue(SecurityDialogRules.isLegalPin("1234"))
        assertFalse(SecurityDialogRules.isLegalPin("12345"), "over 4 digits must be refused")
        assertFalse(SecurityDialogRules.isLegalPin("12a4"), "non-digits must be refused")
    }

    @Test
    fun `qc code filter accepts up to 6 digits only`() {
        assertTrue(SecurityDialogRules.isLegalQcCode(""))
        assertTrue(SecurityDialogRules.isLegalQcCode("123456"))
        assertFalse(SecurityDialogRules.isLegalQcCode("1234567"), "over 6 digits must be refused")
        assertFalse(SecurityDialogRules.isLegalQcCode("12345a"), "non-digits must be refused")
    }

    // ── the set-PIN confirm fork (order preserved) ──

    @Test
    fun `a short pin wins over a mismatch - the length check ran first`() {
        assertEquals(
            "too short",
            SecurityDialogRules.setPinViolation("12", "9999", lengthText = "too short", mismatchText = "mismatch"),
        )
    }

    @Test
    fun `a length-4 mismatch reports the mismatch`() {
        assertEquals(
            "mismatch",
            SecurityDialogRules.setPinViolation("1234", "4321", lengthText = "too short", mismatchText = "mismatch"),
        )
    }

    @Test
    fun `a valid matching pair proceeds`() {
        assertNull(
            SecurityDialogRules.setPinViolation("1234", "1234", lengthText = "too short", mismatchText = "mismatch"),
        )
    }

    // ── the shared pinInput buffer's survivor table ──

    @Test
    fun `the set-pin arm clears the typed buffer on every exit`() {
        for (exit in DialogExit.entries) {
            assertTrue(
                SecurityDialogRules.exitClearsPinInput(SecurityDialogPhase.SetPin(confirm = "1234", error = null), exit),
                "SetPin + $exit must clear the buffer (all three former sites cleared the identical set)",
            )
        }
    }

    @Test
    fun `the disable arm's scrim exit alone keeps the typed buffer - the lone survivor`() {
        assertFalse(
            SecurityDialogRules.exitClearsPinInput(SecurityDialogPhase.DisablePinAuth(error = null), DialogExit.Scrim),
            "the scrim/back exit kept the retained entry (reopening showed it)",
        )
        assertTrue(
            SecurityDialogRules.exitClearsPinInput(SecurityDialogPhase.DisablePinAuth(error = null), DialogExit.Cancel),
            "Cancel cleared the buffer",
        )
        assertTrue(
            SecurityDialogRules.exitClearsPinInput(SecurityDialogPhase.DisablePinAuth(error = null), DialogExit.ConfirmSuccess),
            "confirm success cleared the buffer",
        )
    }

    @Test
    fun `closed and the QC arm keep the buffer trivially`() {
        for (exit in DialogExit.entries) {
            assertFalse(
                SecurityDialogRules.exitClearsPinInput(SecurityDialogPhase.Closed, exit),
                "nothing bespoke open - no buffer to clear",
            )
            assertFalse(
                SecurityDialogRules.exitClearsPinInput(
                    SecurityDialogPhase.QuickConnectAuthorize(QuickConnectAuthorizePhase.Entering("123456")),
                    exit,
                ),
                "the QC dialog never touched the PIN buffer",
            )
        }
    }

    // ── typing clears whichever banner the open arm holds ──

    @Test
    fun `typing in the set-pin fields clears its banner and keeps the confirm buffer`() {
        val errored = SecurityDialogPhase.SetPin(confirm = "12", error = "mismatch")

        val inputChanged = SecurityDialogRules.setPinInputChanged(errored)
        assertIs<SecurityDialogPhase.SetPin>(inputChanged).let {
            assertEquals("12", it.confirm, "the confirm buffer must survive the input field's edit")
            assertNull(it.error)
        }

        val confirmChanged = SecurityDialogRules.setPinConfirmChanged(errored, "1234")
        assertIs<SecurityDialogPhase.SetPin>(confirmChanged).let {
            assertEquals("1234", it.confirm)
            assertNull(it.error)
        }
    }

    @Test
    fun `typing in the disable arm clears only its own banner`() {
        val errored = SecurityDialogPhase.DisablePinAuth(error = "wrong")
        val changed = SecurityDialogRules.disableAuthInputChanged(errored)
        assertIs<SecurityDialogPhase.DisablePinAuth>(changed).let { assertNull(it.error) }
    }

    @Test
    fun `typing rules are no-ops when the arm is not theirs`() {
        val closed = SecurityDialogPhase.Closed
        assertEquals(closed, SecurityDialogRules.setPinInputChanged(closed))
        assertEquals(closed, SecurityDialogRules.setPinConfirmChanged(closed, "1234"))
        assertEquals(closed, SecurityDialogRules.disableAuthInputChanged(closed))
    }

    // ── the QC machine ──

    @Test
    fun `qc dismiss is refused exactly while Authorizing - the former qcLoading guard`() {
        assertFalse(
            SecurityDialogRules.qcDismissAllowed(QuickConnectAuthorizePhase.Authorizing("123456")),
            "an in-flight authorize refuses scrim/back dismissal",
        )
        assertTrue(SecurityDialogRules.qcDismissAllowed(QuickConnectAuthorizePhase.Entering("123456")))
        assertTrue(
            SecurityDialogRules.qcDismissAllowed(QuickConnectAuthorizePhase.Failed("123456", "stale code")),
            "a Failed retry face dismisses again",
        )
        // And the Cancel button rides the same rule (the former `enabled = !qcLoading`).
        assertEquals(
            SecurityDialogRules.qcDismissAllowed(QuickConnectAuthorizePhase.Authorizing("123456")),
            SecurityDialogRules.qcEntryEnabled(QuickConnectAuthorizePhase.Authorizing("123456")),
            "entry + dismissal share the in-flight gate",
        )
    }

    @Test
    fun `qc confirm needs a settled machine and a 6-digit code`() {
        assertFalse(
            SecurityDialogRules.qcConfirmEnabled(QuickConnectAuthorizePhase.Entering("12345")),
            "the former !qcLoading && qcCode.length == 6",
        )
        assertTrue(SecurityDialogRules.qcConfirmEnabled(QuickConnectAuthorizePhase.Entering("123456")))
        assertFalse(
            SecurityDialogRules.qcConfirmEnabled(QuickConnectAuthorizePhase.Authorizing("123456")),
        )
        assertTrue(
            SecurityDialogRules.qcConfirmEnabled(QuickConnectAuthorizePhase.Failed("123456", "stale")),
            "the retry face re-enables the confirm with the kept code",
        )
    }

    @Test
    fun `the confirm press clears the banner entering the in-flight arm which carries the code`() {
        val started = SecurityDialogRules.qcStart(QuickConnectAuthorizePhase.Entering("123456"))
        assertIs<QuickConnectAuthorizePhase.Authorizing>(started).let {
            assertEquals("123456", it.code)
        }
        // From a Failed retry face too: the banner is cleared by the arm change itself.
        val retried = SecurityDialogRules.qcStart(QuickConnectAuthorizePhase.Failed("123456", "stale code"))
        assertIs<QuickConnectAuthorizePhase.Authorizing>(retried)
        assertEquals("123456", retried.code, "the kept code is what authorizes")
    }

    @Test
    fun `the failure answer raises the banner and keeps the code for the retry`() {
        val failed = SecurityDialogRules.qcFailed(QuickConnectAuthorizePhase.Authorizing("123456"), "code used")
        assertIs<QuickConnectAuthorizePhase.Failed>(failed).let {
            assertEquals("123456", it.code, "the code must be kept (the former shape kept qcCode)")
            assertEquals("code used", it.error)
        }
    }

    @Test
    fun `typing lands a fresh Entering - clearing the banner - and is a no-op in flight`() {
        val fromFailed = SecurityDialogRules.qcEntryChanged(
            QuickConnectAuthorizePhase.Failed("123456", "code used"),
            "9",
        )
        assertEquals(QuickConnectAuthorizePhase.Entering("9"), fromFailed)

        val fromEntering = SecurityDialogRules.qcEntryChanged(QuickConnectAuthorizePhase.Entering("12"), "123")
        assertEquals(QuickConnectAuthorizePhase.Entering("123"), fromEntering)

        val inFlight = QuickConnectAuthorizePhase.Authorizing("123456")
        assertEquals(
            inFlight,
            SecurityDialogRules.qcEntryChanged(inFlight, "7"),
            "the field is disabled in flight - the rule is a defensive no-op",
        )
    }
}
