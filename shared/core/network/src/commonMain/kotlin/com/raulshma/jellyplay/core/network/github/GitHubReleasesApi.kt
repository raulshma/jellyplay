package com.raulshma.jellyplay.core.network.github

import com.raulshma.jellyplay.core.model.AppUpdateInfo

/**
 * One published release's notes metadata — the remote source of the What's
 * New feed. [version] is the tag minus any leading `v`; [date] is the
 * `published_at` timestamp reduced to `YYYY-MM-DD`; [title] is the
 * human-authored release name; [body] is the release-notes markdown (the
 * single authoring artifact behind both the update sheet's notes and the
 * What's New cards).
 */
data class GitHubReleaseNotes(
    val version: String,
    val date: String? = null,
    val title: String? = null,
    val body: String = "",
)

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

    /**
     * Fetches the recent published releases (newest first, as GitHub orders
     * them) with their notes bodies — the What's New feed's remote refresh.
     * Same allow-list gates as [fetchLatestUpdate].
     *
     * @return The releases' notes metadata, or failure on network/parse
     *   error. An empty list is a legitimate (if unlikely) success.
     */
    suspend fun fetchReleaseNotes(): Result<List<GitHubReleaseNotes>>
}
