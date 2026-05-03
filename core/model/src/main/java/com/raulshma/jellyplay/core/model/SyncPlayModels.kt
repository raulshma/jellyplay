package com.raulshma.jellyplay.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SyncPlayGroup(
    val groupId: String,
    val groupName: String,
    val participantCount: Int,
    val participants: List<String> = emptyList(),
    val playingItemId: String? = null,
    val playingItemName: String? = null,
    val isPlaying: Boolean = false,
    val positionTicks: Long? = null,
)

@Serializable
data class SyncPlayGroupInfo(
    val groupId: String,
    val groupName: String,
    val participants: List<SyncPlayParticipant> = emptyList(),
    val playingItemId: String? = null,
    val playingItemName: String? = null,
    val isPlaying: Boolean = false,
    val positionTicks: Long? = null,
    val playbackSpeed: Float = 1.0f,
    val repeatMode: String = "RepeatNone",
    val shuffleMode: String = "Sorted",
)

@Serializable
data class SyncPlayParticipant(
    val userId: String,
    val userName: String,
    val isConnected: Boolean = true,
    val isPlaying: Boolean = false,
    val positionTicks: Long? = null,
)

@Serializable
data class SyncPlaySettings(
    val groupId: String = "",
    val newGroup: String = "",
)

@Serializable
enum class SyncPlayCommandType {
    PLAY,
    PAUSE,
    STOP,
    SEEK,
    READY,
    BUFFERING,
}
