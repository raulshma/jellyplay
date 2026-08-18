package com.raulshma.jellyplay.shell

import android.content.Intent
import android.os.Build
import com.raulshma.jellyplay.core.data.update.AppUpdateRepository
import com.raulshma.jellyplay.core.data.update.PendingAppUpdate
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.model.AppUpdateInfo
import com.raulshma.jellyplay.update.AppUpdateDecision
import com.raulshma.jellyplay.update.UpdateState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Owns the in-app self-update engine behind a small seam: one [updateState]
 * flow the shell renders, plus the check / download / install / dismiss
 * commands the update sheet issues. Dismissal suppression, auto-download
 * policy, and the pending-APK restore on launch are private to this module.
 */
@Singleton
class UpdateCoordinator @Inject constructor(
    private val appUpdateRepository: AppUpdateRepository,
    private val experimentalStore: ExperimentalStore,
) : ShellCoordinator() {
    /**
     * In-app self-update state. Observed by the update sheet so a single
     * coordinator instance drives both the launch-time auto-check and any
     * manual check. Stays [UpdateState.Idle] (sheet hidden) until an update is
     * actually found or the user explicitly opens the flow.
     */
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    /**
     * User's "download updates automatically" preference, mirrored from
     * [ExperimentalStore] so the update sheet can render + toggle it while a
     * flow is active without subscribing to the whole experimental slice.
     */
    private val _selfUpdateDownloadEnabled = MutableStateFlow(false)
    val selfUpdateDownloadEnabled: StateFlow<Boolean> = _selfUpdateDownloadEnabled.asStateFlow()

    /**
     * Begins mirroring the auto-download preference on [scope]. Safe to call
     * again (e.g. after activity-state loss rebuilt the ViewModel):
     * [RestartableJob] cancels the previous collector first, so it is never
     * duplicated.
     */
    fun start(scope: CoroutineScope) {
        lifecycleJob.launchIn(scope) {
            experimentalStore.experimental.collect { prefs ->
                _selfUpdateDownloadEnabled.value = prefs.selfUpdateDownloadEnabled
            }
        }
    }

    /**
     * Launch-time hook, called once session restore completes. Best-effort
     * app-update check: first restore any update APK already downloaded but
     * not yet installed (kept on disk across restarts); only if there's
     * nothing pending do we hit the network. Gated by the "check for updates
     * automatically" toggle so that off-switch stays the single way to
     * silence update UI on launch — the file itself is still retained for a
     * manual check.
     */
    fun onSessionRestored() {
        commandScope.launch {
            val experimental = experimentalStore.experimental.first()
            if (experimental.selfUpdateCheckEnabled) {
                val pending = runCatching { appUpdateRepository.getPendingUpdate() }.getOrNull()
                if (pending != null && !isUpdateRecentlyDismissed(pending.info.latestVersion, experimental)) {
                    _updateState.value = UpdateState.Downloaded(pending.info, pending.apkFile)
                } else {
                    checkForAppUpdate(experimental)
                }
            }
        }
    }

    /**
     * Network half of the launch-time auto-check: queries GitHub Releases and
     * stays silent unless an update is actually available — it never surfaces
     * a sheet for an up-to-date result. Callers gate on
     * `selfUpdateCheckEnabled` and pass the already-read [ExperimentalSlice];
     * when the user's opted into auto-download, an available update begins
     * streaming immediately instead of prompting. Use [manualCheckForUpdate]
     * when the user wants feedback regardless of outcome.
     */
    private fun checkForAppUpdate(experimental: ExperimentalSlice) {
        commandScope.launch {
            val result = appUpdateRepository.checkForUpdate(
                supportedAbis = Build.SUPPORTED_ABIS,
            )
            result.onSuccess { info ->
                if (!info.isUpdateAvailable) return@onSuccess // stay Idle.
                // Honor a prior dismissal: if the user dismissed this exact
                // version less than 24h ago, stay quiet on the launch auto-check.
                if (isUpdateRecentlyDismissed(info.latestVersion, experimental)) return@onSuccess
                surfaceAvailableUpdate(info, experimental, pending = null)
            }
        }
    }

    /**
     * Manual, user-initiated check (from Settings). Always surfaces the
     * result: a sheet for an available update, or a "you're up to date" sheet
     * (with a link to view the current version's release notes) when none is.
     * Bypasses the auto-check preference, but still honors auto-download (an
     * available update begins streaming immediately when enabled).
     */
    fun manualCheckForUpdate() {
        commandScope.launch {
            _updateState.value = UpdateState.Checking
            val experimental = experimentalStore.experimental.first()
            val pending = runCatching { appUpdateRepository.getPendingUpdate() }.getOrNull()
            val result = appUpdateRepository.checkForUpdate(
                supportedAbis = Build.SUPPORTED_ABIS,
            )
            // Manual checks ignore the 24h dismissal — the user explicitly asked.
            // Always hit the network so a release published *after* the on-disk
            // APK was downloaded can still surface: when both are present, prefer
            // the newer version (ties keep the pending APK so its already-downloaded
            // bytes stay the install path). On network failure fall back to pending.
            val remote = result.getOrNull()
            val surface = AppUpdateDecision.pickUpdateToSurface(pending?.info, remote)
            when {
                // A newer version is available to download.
                surface != null && surface.isUpdateAvailable ->
                    surfaceAvailableUpdate(surface, experimental, pending)
                // Up to date — show the result (with release notes).
                surface != null -> _updateState.value = UpdateState.NoUpdate(surface)
                // Network failed but a pending APK exists — fall back to it.
                pending != null ->
                    _updateState.value = UpdateState.Downloaded(pending.info, pending.apkFile)
                // Network failed, nothing pending.
                else -> _updateState.value =
                    UpdateState.Error(result.exceptionOrNull()?.message ?: "Update check failed")
            }
        }
    }

    /**
     * Persists the "download updates automatically" preference. Also exposed
     * from the update sheet so the toggle takes effect from either place.
     */
    fun setSelfUpdateDownloadEnabled(enabled: Boolean) {
        _selfUpdateDownloadEnabled.value = enabled
        commandScope.launch { experimentalStore.setSelfUpdateDownloadEnabled(enabled) }
    }

    /**
     * Begins streaming the APK for the given update, reporting progress. Wipes
     * any previously-downloaded APK + sidecar first, so this is also the path
     * used by [redownloadUpdate] to overwrite an existing file.
     */
    fun startUpdateDownload(info: AppUpdateInfo) {
        if (info.downloadAssetUrl == null) return
        commandScope.launch {
            _updateState.value = UpdateState.Downloading(info, 0f, 0L, info.releaseSize)
            val result = appUpdateRepository.downloadApk(info) { fraction, read, total ->
                _updateState.value = UpdateState.Downloading(info, fraction, read, total)
            }
            result
                .onSuccess { file -> _updateState.value = UpdateState.Downloaded(info, file) }
                .onFailure { _updateState.value = UpdateState.Error(it.message ?: "Download failed") }
        }
    }

    /**
     * Re-downloads the update whose APK is already on disk (and shown as
     * [UpdateState.Downloaded]). Falls back to the current state's info; the
     * repository overwrites the existing file + sidecar via the normal
     * download path.
     */
    fun redownloadUpdate() {
        val state = _updateState.value
        val info = (state as? UpdateState.Downloaded)?.info
            ?: (state as? UpdateState.UpdateAvailable)?.info
            ?: return
        startUpdateDownload(info)
    }

    /**
     * Builds the system package-installer intent for the APK held by the
     * current [UpdateState.Downloaded] state — the only state whose install
     * button the sheet renders — or null when the state holds no file (the
     * flow moved on between render and click).
     */
    fun buildInstallIntent(): Intent? =
        (_updateState.value as? UpdateState.Downloaded)?.file
            ?.let { appUpdateRepository.buildInstallIntent(it) }

    /**
     * Hides the update sheet without changing download state. When dismissed
     * from an [UpdateState.UpdateAvailable] prompt or an install-ready
     * [UpdateState.Downloaded] sheet, stamps the version + time so the
     * launch-time auto-check / restore stays quiet for the same version for
     * 24h. The downloaded APK is retained on disk either way. Manual checks
     * still surface the result regardless of dismissal.
     */
    fun dismissUpdate() {
        val dismissedVersion = AppUpdateDecision.dismissedVersion(_updateState.value)
        if (dismissedVersion != null) {
            commandScope.launch {
                experimentalStore.setDismissedUpdate(dismissedVersion)
            }
        }
        _updateState.value = UpdateState.Idle
    }

    /**
     * Routes an available update to its surfaced state: install-ready when
     * that exact version's APK is already on disk, auto-download (skip the
     * prompt, stream the APK — the sheet surfaces progress/cancel) when
     * enabled, otherwise the [UpdateState.UpdateAvailable] prompt.
     */
    private fun surfaceAvailableUpdate(
        info: AppUpdateInfo,
        experimental: ExperimentalSlice,
        pending: PendingAppUpdate?,
    ) {
        if (pending != null && info.latestVersion == pending.info.latestVersion) {
            _updateState.value = UpdateState.Downloaded(pending.info, pending.apkFile)
        } else if (AppUpdateDecision.shouldAutoDownload(experimental.selfUpdateDownloadEnabled, info)) {
            startUpdateDownload(info)
        } else {
            _updateState.value = UpdateState.UpdateAvailable(info)
        }
    }

    /**
     * True when [version] matches the last dismissed update within the 24h
     * suppression window. Centralizes the experimental-slice unpacking and the
     * clock so both launch-time update-check sites apply identical rules.
     */
    private fun isUpdateRecentlyDismissed(version: String, experimental: ExperimentalSlice): Boolean =
        AppUpdateDecision.isRecentlyDismissed(
            version = version,
            dismissedVersion = experimental.dismissedUpdateVersion,
            dismissedAtMs = experimental.dismissedUpdateAtMs,
            nowMs = System.currentTimeMillis(),
        )
}
