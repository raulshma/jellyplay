package com.raulshma.jellyplay.core.model

import kotlinx.serialization.Serializable

@Serializable
data class IntroTimestamps(
    val itemId: String,
    val introStartTicks: Long = 0,
    val introEndTicks: Long = 0,
    val showSkipPromptAtTicks: Long = 0,
    val hideSkipPromptAtTicks: Long = 0,
) {
    val hasIntro: Boolean get() = introEndTicks > introStartTicks
}

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
