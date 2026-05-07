package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class IntroTimestamps(
    @SerialName("ItemId") val itemId: String,
    @SerialName("IntroStartTicks") val introStartTicks: Long = 0,
    @SerialName("IntroEndTicks") val introEndTicks: Long = 0,
    @SerialName("ShowSkipPromptAtTicks") val showSkipPromptAtTicks: Long = 0,
    @SerialName("HideSkipPromptAtTicks") val hideSkipPromptAtTicks: Long = 0,
) {
    val hasIntro: Boolean get() = introEndTicks > introStartTicks
}

@Immutable
@Serializable
data class CreditTimestamps(
    @SerialName("ItemId") val itemId: String,
    @SerialName("CreditStartTicks") val creditStartTicks: Long = 0,
    @SerialName("CreditEndTicks") val creditEndTicks: Long = 0,
    @SerialName("ShowSkipPromptAtTicks") val showSkipPromptAtTicks: Long = 0,
    @SerialName("HideSkipPromptAtTicks") val hideSkipPromptAtTicks: Long = 0,
) {
    val hasCredits: Boolean get() = creditEndTicks > creditStartTicks
}

@Immutable
@Serializable
data class RemoteSubtitleInfo(
    val id: String,
    val language: String? = null,
    val name: String? = null,
    val format: String? = null,
    val comment: String? = null,
    val dateCreated: String? = null,
    val downloadCount: Int = 0,
    val isHashMatch: Boolean = false,
    val provider: String? = null,
)
