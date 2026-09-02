package com.raulshma.jellyplay.core.data.update

import com.raulshma.jellyplay.core.model.AppUpdateInfo
import java.io.File

/**
 * Checks for app updates against GitHub Releases and manages the in-app
 * download/install flow. Shared between the launch-time auto-check
 * ([com.raulshma.jellyplay.MainViewModel]) and the manual Settings check.
 *
 * Downloaded APKs are persisted (under `filesDir`, not `cacheDir`) together
 * with a sidecar metadata file until they are installed: a successful install
 * restarts the process in the new version, and the launch-time
 * [cleanupDownloadedUpdate] then sees the sidecar version is no longer newer than
 * the installed one and deletes it. This lets the user install (or re-download)
 * a previously-fetched update across app restarts.
 *
 * AppUpdate split (Wave xB): moved from the legacy `:core:data` shim to
 * :shared:core:data jvmShared, reshaped for platform DI — the device identity
 * the old `checkForUpdate(supportedAbis)` parameter carried (version / flavor
 * / ABIs) is now ctor-injected into [AppUpdateRepositoryImpl] by the Koin
 * platform modules, and the Android-only install intent became the
 * androidMain `ApkInstallBuilder` seam.
 */
interface AppUpdateRepository {

    /**
     * Fetches the latest release and resolves the matching APK asset for this
     * device's flavor + ABI (both ctor-supplied by the platform DI). Returns
     * failure on network/parse errors.
     */
    suspend fun checkForUpdate(): Result<AppUpdateInfo>

    /**
     * Streams the APK for [info] into the app's files directory, reporting
     * progress. Stored under filesDir (not cacheDir) so it survives the
     * system installer's round trip — backgrounding, screen lock, or the user
     * leaving to grant unknown-sources permission must not delete the file.
     *
     * A sidecar `.meta.json` is written next to the APK recording the update's
     * version + asset metadata so [getPendingUpdate] can restore an
     * install-ready state across restarts and [cleanupDownloadedUpdate] can tell a
     * genuinely pending update from an orphan. Any prior APK + sidecar in the
     * updates directory is wiped first, so calling this again re-downloads
     * cleanly over an existing file.
     *
     * @param info The release being downloaded; its version / asset URL / size
     *   are persisted to the sidecar.
     * @param onProgress Called with (fraction 0..1, bytesRead, totalBytes) on
     *   a background dispatcher; safe to update UI state from here via the
     *   caller's coroutine context.
     * @return The downloaded [File], or failure.
     */
    suspend fun downloadUpdate(
        info: AppUpdateInfo,
        onProgress: (Float, Long, Long) -> Unit = { _, _, _ -> },
    ): Result<File>

    /**
     * Returns the previously-downloaded update APK that is still pending
     * install, if any. Reads the on-disk sidecar and only returns a non-null
     * result when the APK file exists **and** its recorded version is newer
     * than the currently-installed build — so a completed install (process
     * restarted in the new version), a stale/older APK, or a missing sidecar
     * all yield `null`. Lets the caller rebuild a `Downloaded` state with no
     * network call.
     */
    suspend fun getPendingUpdate(): PendingAppUpdate?

    /**
     * Sweeps the updates directory on app startup. **Keeps** any APK whose
     * sidecar records a version newer than the currently-installed build (a
     * genuinely pending update the user hasn't installed yet); **deletes**
     * everything else — an orphan left by a completed self-update (process
     * restarted in the new version), a stale/older APK, a partial download, or
     * a sidecar whose APK is gone. The system installer returns no result for
     * `ACTION_VIEW`, so this is the reliable place to reclaim space. Safe to
     * call before any download has started.
     */
    fun cleanupDownloadedUpdate()
}
