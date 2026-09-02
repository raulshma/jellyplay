package com.raulshma.jellyplay.core.network.github

import com.raulshma.jellyplay.core.model.AppUpdateInfo

/**
 * Fetches JellyPlay release metadata from the GitHub Releases API and selects
 * the APK asset appropriate for the running flavor + ABI.
 */
interface GitHubReleasesApi {

    /**
     * Fetches the latest release, compares it to [currentVersionName], and
     * resolves the best-matching APK asset (flavor + ABI, falling back to
     * the universal build).
     *
     * @param currentVersionName The installed version name (e.g. `1.2.3`).
     * @param flavor The running product flavor: `phone` or `tv`
     *   (from `Build.FLAVOR`).
     * @param supportedAbis The device's preferred ABIs (from
     *   `Build.SUPPORTED_ABIS`), ordered most-preferred first.
     * @return Resolved update info, or failure on network/parse error.
     */
    suspend fun fetchLatestUpdate(
        currentVersionName: String,
        flavor: String,
        supportedAbis: Array<String>,
    ): Result<AppUpdateInfo>
}
