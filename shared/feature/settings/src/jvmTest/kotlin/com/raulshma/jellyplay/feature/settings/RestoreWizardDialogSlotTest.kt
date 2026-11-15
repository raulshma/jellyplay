package com.raulshma.jellyplay.feature.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the restore wizard's dialog slot ([RestoreWizardDialog]) and its two
 * cross-owner rules against the ViewModel's sealed phases
 * ([SecretsPhase] / [ApplyPhase]) — the pure decision object beside the phase
 * types per StateManagementConventions §3. The wizard's dialog choreography
 * itself is composition (this module has no Compose screen harness), so what
 * is unit-pinned here is exactly the logic a comment could only hope for:
 *
 *  - an arriving [SecretsPhase.Unlocked] closes ONLY the passphrase arm —
 *    every other arm (notably the restore confirm, which can be the open arm
 *    while an in-flight unlock settles) stays up, preserving the former
 *    unconditional `showUnlockDialog = false`'s silent no-op semantics;
 *  - a failed ([SecretsPhase.UnlockFailed]) or in-flight unlock closes
 *    nothing — a wrong passphrase keeps the dialog up for the inline retry;
 *  - the restore-confirm arm refuses dismissal exactly while
 *    [ApplyPhase.Applying] runs (an apply abandoned mid-batch leaves the
 *    source half-restored) and dismisses again once the apply settles;
 *  - the passphrase and secrets-apply arms always dismiss.
 *
 * Comment-pinned (composition facts, declared at [RestoreWizardDialog]'s KDoc
 * and the render site): the render order (restore confirm → passphrase
 * unlock → secrets apply confirm, preserved from the former separate `if`s),
 * the newest-show-wins replacement the slot encodes, and the dialogs' absence
 * from any screen back ladder.
 */
class RestoreWizardDialogSlotTest {

    private val unlockedSummary = SecretsRestoreSummary(
        servers = 1,
        arrServers = 0,
        subtitleProviders = 0,
        hasSeerr = false,
    )

    // ── closesOnUnlock: the unlock effect's guard ──

    @Test
    fun `an arriving Unlocked phase closes only the passphrase arm`() {
        assertTrue(
            RestoreWizardDialogRules.closesOnUnlock(
                RestoreWizardDialog.UnlockPassphrase,
                SecretsPhase.Unlocked(unlockedSummary),
            ),
        )
    }

    @Test
    fun `other open arms stay up when Unlocked lands - the former no-op is preserved`() {
        val unlocked = SecretsPhase.Unlocked(unlockedSummary)
        for (arm in RestoreWizardDialog.entries.filter { it != RestoreWizardDialog.UnlockPassphrase }) {
            assertFalse(
                RestoreWizardDialogRules.closesOnUnlock(arm, unlocked),
                "$arm must not close when an unlock settles",
            )
        }
        assertFalse(RestoreWizardDialogRules.closesOnUnlock(null, unlocked))
    }

    @Test
    fun `a failed or in-flight unlock closes nothing`() {
        val phases = listOf<SecretsPhase>(
            SecretsPhase.Hidden,
            SecretsPhase.Locked,
            SecretsPhase.Unlocking,
            SecretsPhase.UnlockFailed,
        )
        for (phase in phases) {
            assertFalse(
                RestoreWizardDialogRules.closesOnUnlock(RestoreWizardDialog.UnlockPassphrase, phase),
                "$phase must not close the passphrase arm (wrong passphrase keeps it up)",
            )
            assertFalse(RestoreWizardDialogRules.closesOnUnlock(null, phase))
        }
    }

    // ── dismissAllowed: the apply-batch guarded dismissal ──

    @Test
    fun `restore confirm refuses dismissal exactly while Applying`() {
        assertFalse(
            RestoreWizardDialogRules.dismissAllowed(RestoreWizardDialog.RestoreConfirm, ApplyPhase.Applying),
            "an apply abandoned mid-batch leaves the source half-restored — dismissal must be refused",
        )
        val settled = listOf<ApplyPhase>(ApplyPhase.Idle, ApplyPhase.Completed, ApplyPhase.Failed("boom"))
        for (phase in settled) {
            assertTrue(
                RestoreWizardDialogRules.dismissAllowed(RestoreWizardDialog.RestoreConfirm, phase),
                "$phase must allow dismissal again",
            )
        }
    }

    @Test
    fun `the passphrase and secrets-apply arms always dismiss`() {
        val phases = listOf<ApplyPhase>(ApplyPhase.Idle, ApplyPhase.Applying, ApplyPhase.Completed, ApplyPhase.Failed("boom"))
        for (arm in listOf(RestoreWizardDialog.UnlockPassphrase, RestoreWizardDialog.SecretsApplyConfirm)) {
            for (phase in phases) {
                assertTrue(RestoreWizardDialogRules.dismissAllowed(arm, phase), "$arm must always dismiss")
            }
        }
    }
}
