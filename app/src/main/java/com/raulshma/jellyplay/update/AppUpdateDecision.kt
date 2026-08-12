package com.raulshma.jellyplay.update

import com.raulshma.jellyplay.core.model.AppUpdateInfo
import com.raulshma.jellyplay.core.model.compareVersions

/**
 * Pure decision functions for the in-app self-update flow, split out of
 * `MainViewModel` so the rules are unit-testable without the ViewModel's
 * scopes, repositories, or file I/O.
 *
 * Everything here is side-effect free; the ViewModel remains the only thing
 * that touches [UpdateState], the experimental store, or the network.
 */
object AppUpdateDecision {

    /** How long a dismissed version is suppressed on the launch auto-check. */
    internal const val DISMISSED_UPDATE_SUPPRESS_MS: Long = 24L * 60 * 60 * 1000

    /**
     * True when [version] matches the last dismissed update and that dismissal
     * happened within [suppressMs] of [nowMs]. Manual checks bypass this by not
     * calling it. A null/blank dismissed version or a clock-skewed negative
     * elapsed both read as "not recently dismissed".
     */
    fun isRecentlyDismissed(
        version: String,
        dismissedVersion: String?,
        dismissedAtMs: Long,
        nowMs: Long,
        suppressMs: Long = DISMISSED_UPDATE_SUPPRESS_MS,
    ): Boolean {
        if (dismissedVersion.isNullOrBlank() || dismissedVersion != version) return false
        val elapsed = nowMs - dismissedAtMs
        return elapsed in 0..suppressMs
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
     * state isn't dismissible from a prompt/install-ready sheet (Idle,
     * Downloading, Checking, NoUpdate, Error). Used so the 24h suppression only
     * applies to versions the user actually saw and dismissed.
     */
    fun dismissedVersion(state: UpdateState): String? = when (state) {
        is UpdateState.UpdateAvailable -> state.info.latestVersion
        is UpdateState.Downloaded -> state.info.latestVersion
        else -> null
    }
}
