package com.raulshma.jellyplay.core.data.update

import com.raulshma.jellyplay.core.model.AppUpdateInfo
import kotlinx.serialization.Serializable
import java.io.File

/**
 * A downloaded update APK that is waiting for the user to install it. Returned
 * by [AppUpdateRepository.getPendingUpdate] so the caller can rebuild an
 * install-ready state with no network round-trip (the release notes / asset
 * name come straight from the on-disk sidecar, see [PendingUpdateMeta]).
 *
 * @property info The reconstructed [AppUpdateInfo] (version + asset metadata
 *   persisted in the sidecar). `isUpdateAvailable` is true and the download URL
 *   is restored so a re-download can overwrite the file.
 * @property apkFile The downloaded APK under `<filesDir>/updates/`.
 */
data class PendingAppUpdate(
    val info: AppUpdateInfo,
    val apkFile: File,
)

/**
 * Sidecar metadata written next to the downloaded APK so the file's identity
 * survives process death and the launch-time cleanup can tell a genuinely
 * pending update (version newer than installed) from an orphan left by a
 * completed/cancelled install. Plain JSON, co-located with the APK at
 * `<filesDir>/updates/jellyplay-update.meta.json`.
 */
@Serializable
internal data class PendingUpdateMeta(
    val version: String,
    val downloadUrl: String?,
    val assetName: String?,
    val releaseSize: Long,
    val downloadedAtMs: Long,
)
