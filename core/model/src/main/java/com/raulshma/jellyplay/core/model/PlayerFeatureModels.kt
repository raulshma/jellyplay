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
    @SerialName("Id") val id: String,
    @SerialName("ThreeLetterISOLanguageName") val threeLetterISOLanguageName: String = "",
    @SerialName("Language") val language: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Format") val format: String? = null,
    @SerialName("Comment") val comment: String? = null,
    @SerialName("DateCreated") val dateCreated: String? = null,
    @SerialName("DownloadCount") val downloadCount: Int = 0,
    @SerialName("IsHashMatch") val isHashMatch: Boolean = false,
    @SerialName("IsForced") val isForced: Boolean = false,
    @SerialName("IsHearingImpaired") val isHearingImpaired: Boolean = false,
    @SerialName("IsAiTranslated") val isAiTranslated: Boolean? = null,
    @SerialName("IsMachineTranslated") val isMachineTranslated: Boolean? = null,
    @SerialName("IsEmbedNotSynced") val isEmbedNotSynced: Boolean? = null,
    @SerialName("CommunityRating") val communityRating: Double? = null,
    @SerialName("FrameRate") val frameRate: Float? = null,
    @SerialName("Author") val author: String? = null,
    @SerialName("Provider") val provider: String? = null,
    @SerialName("ProviderName") val providerName: String? = null,
)
