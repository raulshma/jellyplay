package com.raulshma.jellyplay.shell

import android.content.Intent
import com.raulshma.jellyplay.core.model.AppUpdateInfo
import com.raulshma.jellyplay.update.UpdateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * The shell's self-update seam, flavor-bound: store binds the GitHub-release
 * [UpdateCoordinator]; fdroid binds [com.raulshma.jellyplay.shell.NoopAppUpdateCoordinator]
 * (no download, no installer intent, no REQUEST_INSTALL_PACKAGES — fdroid
 * updates come from the F-Droid client). What's New gating reads [updateState]
 * only, so an idle coordinator leaves the What's New sheet free.
 *
 * src/main consumers ([ShellInfra], [com.raulshma.jellyplay.MainViewModel],
 * the composition overlays) all type against this interface; the concrete
 * updater classes live in the store flavor's source set.
 */
interface AppUpdateCoordinator {
    val updateState: StateFlow<UpdateState>
    val selfUpdateDownloadEnabled: StateFlow<Boolean>

    /** Shell-start hook (MainViewModel's init starts every coordinator uniformly). */
    fun start(scope: CoroutineScope)

    /** Launch-time hook, called once session restore completes. */
    fun onSessionRestored()

    /** Manual, user-initiated check (Settings → About); always surfaces the result. */
    fun manualCheckForUpdate()

    /** Persists the "download updates automatically" preference. */
    fun setSelfUpdateDownloadEnabled(enabled: Boolean)

    /** Begins streaming the APK for the given update, reporting progress. */
    fun startUpdateDownload(info: AppUpdateInfo)

    /** Cancels any in-flight download, returning to [UpdateState.UpdateAvailable]. */
    fun cancelDownload()

    /** Re-downloads the update whose APK is already on disk. */
    fun redownloadUpdate()

    /**
     * The system package-installer intent for the downloaded APK, or null when
     * no installable file is held. The installer seam is store-only — fdroid's
     * coordinator always returns null.
     */
    fun buildInstallIntent(): Intent?

    /** Hides the update sheet and cancels any active download. */
    fun dismissUpdate()
}
