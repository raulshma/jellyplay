package com.raulshma.jellyplay.core.data.update

import android.content.Intent
import com.raulshma.jellyplay.core.model.AppUpdateInfo
import java.io.File

/**
 * Checks for app updates against GitHub Releases and manages the in-app
 * download/install flow. Shared between the launch-time auto-check
 * ([com.raulshma.jellyplay.MainViewModel]) and the manual Settings check.
 */
interface AppUpdateRepository {

    /**
     * Fetches the latest release and resolves the matching APK asset for this
     * device's flavor + ABI. Returns failure on network/parse errors.
     *
     * @param supportedAbis The device's preferred ABIs, from
     *   `Build.SUPPORTED_ABIS` (the only value the application layer must
     *   supply; the running product flavor is derived from the package name).
     */
    suspend fun checkForUpdate(supportedAbis: Array<String>): Result<AppUpdateInfo>

    /**
     * Streams the APK at [url] into the app's files directory, reporting
     * progress. Stored under filesDir (not cacheDir) so it survives the
     * system installer's round trip — backgrounding, screen lock, or the user
     * leaving to grant unknown-sources permission must not delete the file.
     *
     * @param url `browser_download_url` of the chosen asset.
     * @param onProgress Called with (fraction 0..1, bytesRead, totalBytes) on
     *   a background dispatcher; safe to update UI state from here via the
     *   caller's coroutine context.
     * @return The downloaded [File], or failure.
     */
    suspend fun downloadApk(
        url: String,
        onProgress: (Float, Long, Long) -> Unit = { _, _, _ -> },
    ): Result<File>

    /**
     * Builds a launchable [Intent] that asks the system package installer to
     * install [apkFile] via its FileProvider content URI. The caller
     * `startActivity`s it.
     */
    fun buildInstallIntent(apkFile: File): Intent

    /**
     * Deletes any previously-downloaded update APK. The system installer is
     * launched in a separate process and returns no result for `ACTION_VIEW`,
     * so this is best invoked on app startup ([Application.onCreate]): a
     * successful self-update restarts the process, leaving the prior APK
     * orphaned — the next launch sweeps it. Safe to call before any download
     * has started.
     */
    fun cleanupDownloadedApk()
}
