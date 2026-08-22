package com.raulshma.jellyplay.core.model.remote

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Remote "Play" request from another Jellyfin client.
 * Wire shape (server-side):
 * {
 *   "MessageType": "Play",
 *   "Data": {
 *     "ItemIds": [String],
 *     "StartPositionTicks": Long,
 *     "PlayCommand": "PlayNow" | "PlayNext" | "PlayLast" | "PlayInstantMix" | "PlayShuffle",
 *     "ControllingUserId": String,
 *     "SubtitleStreamIndex": Int?,
 *     "AudioStreamIndex": Int?,
 *     "MediaSourceId": String?,
 *     "StartIndex": Int
 *   }
 * }
 */
@Immutable
@Serializable
data class PlayRequest(
    val itemIds: List<String>,
    val startIndex: Int = 0,
    val startPositionTicks: Long = 0,
    val playCommand: String = "PlayNow",
    val mediaSourceId: String? = null,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
    val controllingUserId: String = "",
)

/**
 * Remote "Playstate" command.
 * Wire shape:
 * {
 *   "MessageType": "Playstate",
 *   "Data": {
 *     "Command": "Stop" | "Pause" | "Unpause" | "NextTrack" | "PreviousTrack"
 *                | "Seek" | "Rewind" | "FastForward" | "PlayPause",
 *     "SeekPositionTicks": Long?,
 *     "ControllingUserId": String?
 *   }
 * }
 */
@Immutable
@Serializable
sealed class PlaystateCommand {
    @Serializable @Immutable data object Stop : PlaystateCommand()
    @Serializable @Immutable data object Pause : PlaystateCommand()
    @Serializable @Immutable data object Unpause : PlaystateCommand()
    @Serializable @Immutable data object NextTrack : PlaystateCommand()
    @Serializable @Immutable data object PreviousTrack : PlaystateCommand()
    @Serializable @Immutable data object Rewind : PlaystateCommand()
    @Serializable @Immutable data object FastForward : PlaystateCommand()
    @Serializable @Immutable data object PlayPause : PlaystateCommand()
    @Serializable @Immutable data class Seek(val positionTicks: Long) : PlaystateCommand()
}

/**
 * Subset of "GeneralCommand" that we actually implement.
 * Wire shape:
 * {
 *   "MessageType": "GeneralCommand",
 *   "Data": {
 *     "Name": "<GeneralCommandType>",
 *     "ControllingUserId": String,
 *     "Arguments": { "Key": "Value", ... }
 *   }
 * }
 *
 * GeneralCommandType values are defined in
 * org.jellyfin.sdk.model.api.GeneralCommandType.
 */
@Immutable
@Serializable
sealed class GeneralCommand {
    @Serializable @Immutable data class SetVolume(val volume0to100: Int, val mute: Boolean?) : GeneralCommand()
    @Serializable @Immutable data object VolumeUp : GeneralCommand()
    @Serializable @Immutable data object VolumeDown : GeneralCommand()
    @Serializable @Immutable data object Mute : GeneralCommand()
    @Serializable @Immutable data object Unmute : GeneralCommand()
    @Serializable @Immutable data object ToggleMute : GeneralCommand()
    @Serializable @Immutable data class SetAudioStreamIndex(val index: Int) : GeneralCommand()
    @Serializable @Immutable data class SetSubtitleStreamIndex(val index: Int) : GeneralCommand()
    @Serializable @Immutable data class SetRepeatMode(val mode: String) : GeneralCommand()
    @Serializable @Immutable data class SetShuffleQueue(val shuffle: Boolean) : GeneralCommand()
    @Serializable @Immutable data class SetPlaybackOrder(val order: String) : GeneralCommand()
    @Serializable @Immutable data class SetMaxStreamingBitrate(val bitrate: Int) : GeneralCommand()
    @Serializable @Immutable data object ToggleFullscreen : GeneralCommand()
    @Serializable @Immutable data class DisplayMessage(val header: String, val text: String, val timeoutMs: Int?) : GeneralCommand()
    @Serializable @Immutable data class Unknown(val name: String) : GeneralCommand()
}

/**
 * Whether a PlayRequest should be routed to the audio or video player.
 */
@Immutable
@Serializable
enum class PlaybackDomain {
    AUDIO,
    VIDEO,
    UNKNOWN,
}
