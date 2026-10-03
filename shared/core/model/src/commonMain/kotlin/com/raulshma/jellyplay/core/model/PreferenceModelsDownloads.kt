package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The downloads preference models: the scheduled-download window shared by
 * the download scheduler and its storage settings row.
 */

@Immutable
@Serializable
data class DownloadScheduleWindow(
    val startHour: Int = 0,
    val endHour: Int = 6,
    val wifiOnly: Boolean = true,
)
