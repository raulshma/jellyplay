package com.raulshma.jellyplay.update

import com.raulshma.jellyplay.core.model.AppUpdateInfo
import java.io.File

/**
 * State machine for the in-app self-update flow, surfaced as a single
 * [com.raulshma.jellyplay.MainViewModel] [StateFlow][kotlinx.coroutines.flow.StateFlow]
 * that the update sheet collects.
 */
sealed interface UpdateState {

    /** No update activity; the sheet is hidden. */
    data object Idle : UpdateState

    /** A release check is in flight (post-launch or manual). */
    data object Checking : UpdateState

    /** The latest release is not newer than the installed build. Carries the
     *  release info so a manual check can still show the current version's
     *  release notes. */
    data class NoUpdate(val info: AppUpdateInfo) : UpdateState

    /** A newer release is available; the sheet shows release notes + actions. */
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateState

    /** The APK is downloading; [fraction] is 0..1. */
    data class Downloading(
        val info: AppUpdateInfo,
        val fraction: Float,
        val bytesRead: Long,
        val total: Long,
    ) : UpdateState

    /** The APK finished downloading and is ready to install. */
    data class Downloaded(val info: AppUpdateInfo, val file: File) : UpdateState

    /** Something went wrong (check or download). */
    data class Error(val message: String) : UpdateState
}
