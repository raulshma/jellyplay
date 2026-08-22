package com.raulshma.jellyplay.core.data.syncplay

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.model.SyncPlayGroupUpdateData
import com.raulshma.jellyplay.core.model.SyncPlayPlaybackCommand
import com.raulshma.jellyplay.core.model.SyncPlayQueueUpdateData
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime

sealed class SyncPlayEvent {
    data class PlaybackCommand(val cmd: SyncPlayPlaybackCommand) : SyncPlayEvent()
    data class PlayQueueUpdate(val data: SyncPlayQueueUpdateData) : SyncPlayEvent()
    data class GroupUpdate(val groupName: String, val participantCount: Int) : SyncPlayEvent()

    /**
     * @param state raw server GroupStateType: "Playing", "Waiting", "Paused",
     *   "Idle". Waiting is the transient everyone-parked-while-a-client-catches-up
     *   state — the only one that should surface as "syncing"; a Paused group
     *   is still in sync.
     */
    data class StateUpdate(val isPlaying: Boolean, val state: String, val reason: String) : SyncPlayEvent()
    data class WaitForGroup(val userName: String?) : SyncPlayEvent()
    data class Notification(val message: String) : SyncPlayEvent()
    data object GroupLeft : SyncPlayEvent()
}

class SyncPlayEventHandler constructor() {

    fun parse(rawType: String, rawData: JSONObject): SyncPlayEvent? {
        return try {
            when (rawType) {
                "SyncPlayCommand" -> parseCommand(rawData)
                "SyncPlayGroupUpdate" -> parseGroupUpdate(rawData)
                "GroupJoined" -> parseGroupJoined(rawData)
                "GroupLeft" -> SyncPlayEvent.GroupLeft
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse SyncPlay event: $rawType", e)
            null
        }
    }

    private fun parseCommand(data: JSONObject): SyncPlayEvent? {
        val command = data.optString("Command", "")
        val positionTicks = parseTicks(data, "PositionTicks")
        val whenMs = parseTimestamp(data, listOf("When", "EmittedAt"))
        val playlistItemId = data.optString("PlaylistItemId", "")
        val emittedAt = parseTimestamp(data, listOf("EmittedAt"))

        return when (command) {
            "Unpause", "Pause", "Seek", "Stop" -> SyncPlayEvent.PlaybackCommand(
                SyncPlayPlaybackCommand(
                    command = command,
                    whenMs = whenMs,
                    positionTicks = positionTicks,
                    playlistItemId = playlistItemId,
                    emittedAtMs = emittedAt,
                )
            )
            else -> null
        }
    }

    private fun parseGroupUpdate(json: JSONObject): SyncPlayEvent {
        val type = json.optString("Type", "")
        val innerData = json.optJSONObject("Data")

        return when (type) {
            "PlayQueue" -> parsePlayQueue(innerData ?: json)
            "GroupJoined", "GroupUpdate" -> parseGroupJoined(innerData ?: json)
            "StateUpdate" -> {
                val data = innerData ?: json
                val state = data.optString("State", "")
                val reason = data.optString("Reason", "")
                SyncPlayEvent.StateUpdate(
                    isPlaying = state.equals("Playing", ignoreCase = true),
                    state = state,
                    reason = reason,
                )
            }
            "UserJoined" -> {
                val data = innerData ?: json
                val userName = data.optString("UserName", "")
                if (userName.isBlank()) {
                    val raw = json.optString("Data", "")
                    SyncPlayEvent.Notification("$raw joined the group")
                } else {
                    SyncPlayEvent.Notification("$userName joined the group")
                }
            }
            "UserLeft" -> {
                val data = innerData ?: json
                val userName = data.optString("UserName", "")
                if (userName.isBlank()) {
                    val raw = json.optString("Data", "")
                    SyncPlayEvent.Notification("$raw left the group")
                } else {
                    SyncPlayEvent.Notification("$userName left the group")
                }
            }
            "GroupWait" -> {
                val data = innerData ?: json
                val userName = data.optString("UserName", "")
                SyncPlayEvent.WaitForGroup(userName)
            }
            "GroupLeft", "NotInGroup" -> SyncPlayEvent.GroupLeft
            "SyncPlayIsDisabled", "GroupDoesNotExist", "CreateGroupDenied",
            "JoinGroupDenied", "LibraryAccessDenied" -> {
                val data = innerData ?: json
                val message = data.optString("Message", type)
                SyncPlayEvent.Notification("SyncPlay: $message")
            }
            else -> SyncPlayEvent.Notification("SyncPlay: Unknown update type $type")
        }
    }

    private fun parsePlayQueue(data: JSONObject): SyncPlayEvent.PlayQueueUpdate {
        val playlist = data.optJSONArray("Playlist")
        val itemIds = mutableListOf<String>()
        val playlistItemIds = mutableListOf<String>()

        if (playlist != null) {
            for (i in 0 until playlist.length()) {
                val item = playlist.optJSONObject(i)
                if (item != null) {
                    val itemId = item.optString("ItemId", "")
                    val plItemId = item.optString("PlaylistItemId", "")
                    if (itemId.isNotBlank()) itemIds.add(itemId)
                    if (plItemId.isNotBlank()) playlistItemIds.add(plItemId)
                }
            }
        }

        val playingItemIndex = data.optInt("PlayingItemIndex", 0)
        val startPositionTicks = parseTicks(data, "StartPositionTicks")
        val isPlaying = data.optBoolean("IsPlaying", false)
        val whenMs = parseTimestamp(data, listOf("When", "LastUpdate"))
        val lastUpdate = parseTimestamp(data, listOf("LastUpdate"))

        val repeatModeStr = data.optString("RepeatMode", "RepeatNone")
        val repeatMode = when (repeatModeStr) {
            "RepeatOne" -> SyncPlayRepeatMode.REPEAT_ONE
            "RepeatAll" -> SyncPlayRepeatMode.REPEAT_ALL
            else -> SyncPlayRepeatMode.REPEAT_NONE
        }
        val shuffleModeStr = data.optString("ShuffleMode", "Sorted")
        val shuffleMode = when (shuffleModeStr) {
            "Shuffle" -> SyncPlayShuffleMode.SHUFFLE
            else -> SyncPlayShuffleMode.SORTED
        }

        val reason = data.optString("Reason", "NewPlaylist")
        val playingItemId = itemIds.getOrNull(playingItemIndex) ?: ""
        val playingPlaylistItemId = playlistItemIds.getOrNull(playingItemIndex) ?: ""

        return SyncPlayEvent.PlayQueueUpdate(
            SyncPlayQueueUpdateData(
                playlistItemIds = playlistItemIds,
                itemIds = itemIds,
                playingItemIndex = playingItemIndex,
                playingItemId = playingItemId,
                playingPlaylistItemId = playingPlaylistItemId,
                startPositionTicks = startPositionTicks,
                isPlaying = isPlaying,
                whenMs = whenMs,
                lastUpdateMs = lastUpdate,
                repeatMode = repeatMode,
                shuffleMode = shuffleMode,
                reason = reason,
            )
        )
    }

    private fun parseGroupJoined(data: JSONObject): SyncPlayEvent.GroupUpdate {
        val groupName = data.optString("GroupName", "")
        val participants = data.optJSONArray("Participants")
        val count = participants?.length() ?: 0
        return SyncPlayEvent.GroupUpdate(groupName, count)
    }

    fun parseTicks(json: JSONObject, key: String): Long {
        val ticks = json.optJSONObject(key)
        if (ticks == null) return json.optLong(key, 0L)
        return ticks.optLong("Value", 0L)
    }

    fun parseTimestamp(json: JSONObject, keys: List<String>): Long {
        for (key in keys) {
            val iso = json.optString(key, "")
            if (iso.isBlank()) continue
            try {
                return Instant.parse(iso).toEpochMilli()
            } catch (_: Exception) {
                try {
                    return OffsetDateTime.parse(iso).toInstant().toEpochMilli()
                } catch (_: Exception) {
                }
            }
        }
        return 0L
    }

    companion object {
        private const val TAG = "SyncPlayEventHandler"
    }
}
