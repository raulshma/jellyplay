package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class SyncPlayGroup(
    val groupId: String,
    val groupName: String,
    val participantCount: Int,
    val participants: List<String> = emptyList(),
    val playingItemId: String? = null,
    val playingItemName: String? = null,
    val playingPlaylistItemId: String? = null,
    val isPlaying: Boolean = false,
    val positionTicks: Long? = null,
    val playlistItemIds: List<String> = emptyList(),
    val playlistItemMap: Map<String, String> = emptyMap(),
    val repeatMode: SyncPlayRepeatMode = SyncPlayRepeatMode.REPEAT_NONE,
    val shuffleMode: SyncPlayShuffleMode = SyncPlayShuffleMode.SORTED,
)

@Immutable
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
    val repeatMode: SyncPlayRepeatMode = SyncPlayRepeatMode.REPEAT_NONE,
    val shuffleMode: SyncPlayShuffleMode = SyncPlayShuffleMode.SORTED,
    val playlistItemIds: List<String> = emptyList(),
    val playlistItemMap: Map<String, String> = emptyMap(),
)

@Immutable
@Serializable
data class SyncPlayParticipant(
    val userId: String,
    val userName: String,
    val isConnected: Boolean = true,
    val isPlaying: Boolean = false,
    val positionTicks: Long? = null,
)

@Immutable
@Serializable
enum class SyncPlayRepeatMode {
    REPEAT_NONE,
    REPEAT_ALL,
    REPEAT_ONE,
}

@Immutable
@Serializable
enum class SyncPlayShuffleMode {
    SORTED,
    SHUFFLE,
}

@Immutable
@Serializable
data class SyncPlayChatMessage(
    val id: String,
    val userId: String,
    val userName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: SyncPlayChatMessageType = SyncPlayChatMessageType.USER,
)

@Immutable
@Serializable
enum class SyncPlayChatMessageType {
    USER,
    SYSTEM,
}

@Immutable
@Serializable
data class UtcTimeResponse(
    val requestReceptionTime: String,
    val responseTransmissionTime: String,
)
