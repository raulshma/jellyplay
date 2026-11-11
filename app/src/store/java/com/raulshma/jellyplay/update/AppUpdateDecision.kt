package com.raulshma.jellyplay.update

import com.raulshma.jellyplay.core.model.AppUpdateInfo
import com.raulshma.jellyplay.core.model.UpdateDismissPeriod
import com.raulshma.jellyplay.core.model.compareVersions
import com.raulshma.jellyplay.shell.ShellSurface

/**
 * Pure decision functions for the in-app self-update flow, split out of
 * `MainViewModel` so the rules are unit-testable without the ViewModel's
 * scopes, repositories, or file I/O.
 *
 * Everything here is side-effect free; the ViewModel remains the only thing
 * that touches [UpdateState], the experimental store, or the network.
 */
object AppUpdateDecision {

    /**
     * True when [version] matches the last dismissed update and that dismissal
     * is still inside its configured [UpdateDismissPeriod] window. Manual
     * checks bypass this by not calling it. A null/blank dismissed version or a
     * clock-skewed negative elapsed both read as "not recently dismissed";
     * [UpdateDismissPeriod.NEVER] suppresses the version unconditionally.
     */
    fun isRecentlyDismissed(
        version: String,
        dismissedVersion: String?,
        dismissedAtMs: Long,
        nowMs: Long,
        period: UpdateDismissPeriod = UpdateDismissPeriod.DEFAULT,
    ): Boolean {
        if (dismissedVersion.isNullOrBlank() || dismissedVersion != version) return false
        val window = period.suppressMs ?: return true // null = never re-prompt.
        val elapsed = nowMs - dismissedAtMs
        return elapsed in 0..window
    }

    /**
     * Picks the [AppUpdateInfo] to surface for a manual check: the pending
     * (on-disk) version, the freshly-fetched remote version, or `null` when the
     * remote failed and nothing is pending. When both exist, prefers the newer
     * version — ties keep the pending one so the already-downloaded APK stays
     * the install path instead of forcing a re-download.
     */
    fun pickUpdateToSurface(
        pending: AppUpdateInfo?,
        remote: AppUpdateInfo?,
    ): AppUpdateInfo? {
        if (remote == null) return pending
        if (pending == null) return remote
        return if (compareVersions(remote.latestVersion, pending.latestVersion) > 0) {
            remote
        } else {
            pending
        }
    }

    /**
     * True when auto-download is on and the release actually offers a
     * downloadable asset — the condition under which a surfaced update should
     * start streaming immediately instead of prompting.
     */
    fun shouldAutoDownload(autoDownloadEnabled: Boolean, info: AppUpdateInfo): Boolean =
        autoDownloadEnabled && info.downloadAssetUrl != null

    /**
     * The version a [dismissUpdate] of [state] should stamp, or `null` when the
     * state isn't dismissible from an update sheet (Idle, Checking, NoUpdate,
     * Error). Used so dismissal suppression only applies to versions the user
     * actually saw and dismissed.
     */
    fun dismissedVersion(state: UpdateState): String? = when (state) {
        is UpdateState.UpdateAvailable -> state.info.latestVersion
        is UpdateState.Downloading -> state.info.latestVersion
        is UpdateState.Downloaded -> state.info.latestVersion
        else -> null
    }

    /**
     * Whether the self-update sheet may render right now. The sheet is a
     * MAIN-surface-only modal: it must never interrupt the first-run
     * onboarding wizard, the signed-out auth host, or the pinned restoring
     * splash ([ShellSurface.OnboardingOrAuth]) — surfaces where no
     * session-backed UI exists for the sheet to sit on, and where an update
     * prompt is at best noise (a brand-new user dismissing an update they
     * can't evaluate) and at worst a modal over a branch that renders
     * nothing. This gate is what turned the former implicit exclusion (the
     * sheet simply composed after every session branch) into a pure
     * decision; the surface itself is derived at the composition site by the
     * pure `shellSurface` fold (app/src/main), which mirrors JellyPlayApp's
     * session `when` branch-for-branch.
     *
     * GATING, not SUPPRESSION — the two must stay distinct:
     *  - Suppression ([isRecentlyDismissed] + the persisted two-field
     *    dismissal stamp) is "don't show this version again (for a while)" —
     *    a user decision with a side effect, stamped only when the user
     *    dismisses a VISIBLE sheet ([dismissedVersion]).
     *  - Gating (this fold) is "not on this surface, not yet" — no side
     *    effect, no stamp, no state write. While gated the coordinator keeps
     *    its state untouched (it's a StateFlow), so an update found
     *    mid-onboarding stays [UpdateState.UpdateAvailable] and the sheet
     *    appears the moment the user reaches [ShellSurface.Main] — a
     *    deferred render, not a dropped one. The gated sheet can't be
     *    dismissed because it is never composed: no composition, no
     *    `onDismiss` click, no `dismissUpdate`, no stamp.
     *
     * Pinned by AppUpdateDecisionTest's `shouldShowOverlay` rows, including
     * the gated-then-arrive deferred-render pin (same state, surface flips
     * to Main → true).
     */
    fun shouldShowOverlay(updateState: UpdateState, surface: ShellSurface): Boolean =
        updateState !is UpdateState.Idle && surface == ShellSurface.Main
}
