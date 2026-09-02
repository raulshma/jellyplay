package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Result of an app self-update check against the GitHub Releases API.
 *
 * @property latestVersion The latest published version, derived from the
 *   release tag with any leading `v` stripped (e.g. `1.2.3`).
 * @property htmlUrl Web URL of the release page.
 * @property releaseNotes The release body (GitHub-flavoured markdown) shown
 *   to the user as "what's new".
 * @property isUpdateAvailable True when [latestVersion] is newer than the
 *   currently installed version.
 * @property downloadAssetUrl `browser_download_url` of the APK asset chosen
 *   for this device's flavor + ABI, or null if no matching asset was found.
 * @property downloadAssetName File name of the chosen asset (for display).
 * @property releaseSize Total size of the chosen asset in bytes (best-effort;
 *   0 when unknown), used for the download progress indicator.
 */
@Immutable
data class AppUpdateInfo(
    val latestVersion: String,
    val htmlUrl: String,
    val releaseNotes: String,
    val isUpdateAvailable: Boolean,
    val downloadAssetUrl: String?,
    val downloadAssetName: String?,
    val releaseSize: Long,
)

/**
 * How long an update the user dismissed via "Later" stays hidden from the
 * launch-time auto-prompt for that same version. [NEVER] keeps that version
 * hidden until a newer release appears; manual checks always surface results.
 */
@Immutable
@Serializable
enum class UpdateDismissPeriod(
    val suppressMs: Long?,
) {
    HOURS_12(12L * 60 * 60 * 1000),
    HOURS_24(24L * 60 * 60 * 1000),
    DAYS_3(3L * 24 * 60 * 60 * 1000),
    WEEK_1(7L * 24 * 60 * 60 * 1000),
    NEVER(null),
    ;

    companion object {
        val DEFAULT = HOURS_24

        fun fromName(name: String?): UpdateDismissPeriod =
            name?.let { candidate -> entries.find { it.name == candidate } } ?: DEFAULT
    }
}
