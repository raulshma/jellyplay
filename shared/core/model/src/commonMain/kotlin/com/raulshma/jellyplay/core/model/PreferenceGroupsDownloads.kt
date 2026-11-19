package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The downloads/storage preference aggregates: the logical downloads domain
 * and the StorageSettingsScreen slice.
 */

/**
 * The new-episode auto-download policy (gh-181) — what a check pass does with
 * candidates it finds for a downloaded series:
 * - [INSTANT]: enqueue them for download (within the lookahead window).
 * - [ASK]: record them as pending suggestions the user confirms on the
 *   Downloads screen (plus a summary notification on Android).
 * - [NEVER]: the pass does nothing for that series.
 * - [FOLLOW_GLOBAL]: a SERIES-ROW sentinel (ADR-0015) — the series is armed
 *   and its effective mode is the global preference.
 */
@Immutable
@Serializable
enum class AutoDownloadMode { INSTANT, ASK, NEVER, FOLLOW_GLOBAL }

/**
 * Whether this mode keeps the auto-download machinery (schedule + passes)
 * alive. [FOLLOW_GLOBAL] is a series-row sentinel (ADR-0015), never a legal
 * global value — the DownloadsStore setter's write-side clamp owns that
 * invariant; this projection only has to keep the sentinel "on" (an armed
 * series must not cancel the periodic pass).
 */
val AutoDownloadMode.isEnabled: Boolean get() = this != AutoDownloadMode.NEVER

/**
 * The ONE policy-resolution home (ADR-0015): a stored policy row — or a
 * missing row — resolves against the global mode. [FOLLOW_GLOBAL] rows and
 * absent rows both yield the global mode; an explicit [INSTANT]/[ASK]/[NEVER]
 * row wins. Every consumer of `series_download_policies` (the auto-download
 * check, the Downloads screen's armed list) resolves through this, never by
 * hand — a second hand-fold of the FOLLOW_GLOBAL branch is a defect.
 */
fun AutoDownloadMode?.resolveAgainst(global: AutoDownloadMode): AutoDownloadMode =
    if (this == null || this == AutoDownloadMode.FOLLOW_GLOBAL) global else this

@Immutable
@Serializable
data class DownloadPreferences(
    val wifiOnlyDownloads: Boolean = true,
    val downloadConnections: Int = 4,
    val downloadQuality: DownloadQuality = DownloadQuality.ORIGINAL,
    val smartDownloadsEnabled: Boolean = false,
    val autoDownloadMode: AutoDownloadMode = AutoDownloadMode.NEVER,
    val maxDownloadStorageGb: Int = 0,
    val downloadStorageLocation: String = "INTERNAL",
    val manualOfflineEnabled: Boolean = false,
    val autoOfflineEnabled: Boolean = true,
)

/** Fields read by `StorageSettingsScreen`. */
@Immutable
@Serializable
data class StoragePreferences(
    val wifiOnlyDownloads: Boolean = true,
    val downloadConnections: Int = 4,
    val maxConcurrentDownloads: Int = 3,
    val downloadQuality: DownloadQuality = DownloadQuality.ORIGINAL,
    val smartDownloadsEnabled: Boolean = false,
    val autoDownloadMode: AutoDownloadMode = AutoDownloadMode.NEVER,
    val autoDownloadLookahead: Int = 3,
    val autoDownloadMaxPerPass: Int = 0,
    val autoDownloadKeepDays: Int = 0,
    val autoDownloadServers: Set<String> = emptySet(),
    val maxDownloadStorageGb: Int = 0,
    val downloadStorageLocation: String = "INTERNAL",
    val autoDeleteAfterWatch: Boolean = false,
    val manualOfflineEnabled: Boolean = false,
    val autoOfflineEnabled: Boolean = true,
    val maxCacheSizeMb: Int = 0,
    val autoDeleteCache: Boolean = true,
    val cellularDownloadSizeWarningMb: Int = 0,
    val downloadScheduleEnabled: Boolean = false,
    val downloadScheduleWindow: DownloadScheduleWindow = DownloadScheduleWindow(),
    val streamingQuality: StreamingQuality = StreamingQuality.AUTO,
    val cellularStreamingQuality: StreamingQuality = StreamingQuality.AUTO,
    val meteredNetworkBehavior: MeteredNetworkBehavior = MeteredNetworkBehavior.WARN,
    val adaptiveBitrateEnabled: Boolean = true,
    val manualBandwidthCap: Long = 0L,
    val dataSaverEnabled: Boolean = false,
    val verboseNetworkLogging: Boolean = false,
    val networkTimeoutPreset: NetworkTimeoutPreset = NetworkTimeoutPreset.DEFAULT,
    val userDataSyncEnabled: Boolean = true,
)
