package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class DvrTimer(
    val id: String,
    val programId: String,
    val programName: String,
    val channelId: String,
    val channelName: String,
    val startDate: String? = null,
    val endDate: String? = null,
    val status: DvrTimerStatus = DvrTimerStatus.NEW,
    val isPrePaddingRequired: Boolean = false,
    val isPostPaddingRequired: Boolean = false,
    val prePaddingSeconds: Int = 0,
    val postPaddingSeconds: Int = 0,
    val priority: Int = 0,
    val seriesTimerId: String? = null,
)

@Immutable
@Serializable
enum class DvrTimerStatus {
    NEW,
    SCHEDULED,
    RECORDING,
    COMPLETED,
    CANCELLED,
    CONFLICT_OK,
    CONFLICT_NOT_OK,
}

@Immutable
@Serializable
data class DvrSeriesTimer(
    val id: String,
    val name: String,
    val channelId: String? = null,
    val channelName: String? = null,
    val days: List<String> = emptyList(),
    val priority: Int = 0,
    val recordAnyTime: Boolean = true,
    val recordAnyChannel: Boolean = true,
    val keepUpTo: Int = 0,
)
