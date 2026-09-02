package com.raulshma.jellyplay.core.network.websocket

import com.raulshma.jellyplay.core.model.SessionInfo
import kotlinx.serialization.json.Json

/**
 * Wire DTO for the `Sessions` WebSocket push (`Data` = `SessionInfo[]`).
 *
 * The WS payload is PascalCase (`Id`, `PlayState`, `NowPlayingItem`, …) — unlike
 * the REST DTOs, which the jellyfin-sdk maps. [SessionInfo] itself has no
 * `@SerialName` annotations (camelCase), so decoding the WS payload straight
 * into it silently produces all-default objects; this DTO carries the mapping.
 *
 * Deliberately minimal: only the fields the remote-play transport sync
 * consumes (session identity + play state + now-playing header). Everything
 * else stays at the model defaults under `ignoreUnknownKeys`.
 */
@kotlinx.serialization.Serializable
data class WsSessionInfoDto(
    @kotlinx.serialization.SerialName("Id") val id: String = "",
    @kotlinx.serialization.SerialName("UserName") val userName: String? = null,
    @kotlinx.serialization.SerialName("PlayState") val playState: WsSessionPlayStateDto? = null,
    @kotlinx.serialization.SerialName("NowPlayingItem") val nowPlayingItem: WsSessionNowPlayingItemDto? = null,
)

@kotlinx.serialization.Serializable
data class WsSessionPlayStateDto(
    @kotlinx.serialization.SerialName("PositionTicks") val positionTicks: Long? = null,
    @kotlinx.serialization.SerialName("IsPaused") val isPaused: Boolean? = null,
    @kotlinx.serialization.SerialName("IsMuted") val isMuted: Boolean? = null,
    @kotlinx.serialization.SerialName("VolumeLevel") val volumeLevel: Int? = null,
)

@kotlinx.serialization.Serializable
data class WsSessionNowPlayingItemDto(
    @kotlinx.serialization.SerialName("Id") val id: String? = null,
    @kotlinx.serialization.SerialName("Name") val name: String? = null,
    @kotlinx.serialization.SerialName("SeriesName") val seriesName: String? = null,
    @kotlinx.serialization.SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
)

/** Maps the WS wire DTO onto the shared [SessionInfo] model (REST-compatible). */
fun WsSessionInfoDto.toSessionInfo(): SessionInfo = SessionInfo(
    id = id,
    userName = userName.orEmpty(),
    playState = playState?.let {
        com.raulshma.jellyplay.core.model.SessionPlayState(
            positionTicks = it.positionTicks,
            isPaused = it.isPaused ?: false,
            isMuted = it.isMuted ?: false,
            volumeLevel = it.volumeLevel,
        )
    },
    nowPlayingItem = nowPlayingItem?.let {
        com.raulshma.jellyplay.core.model.SessionNowPlayingItem(
            id = it.id.orEmpty(),
            name = it.name.orEmpty(),
            seriesName = it.seriesName,
            runTimeTicks = it.runTimeTicks,
        )
    },
)

/**
 * Envelope of a `Sessions` WS push. Decoding the whole [WebSocketEvent.rawText]
 * — instead of re-serializing `Data` to a String via org.json first — keeps the
 * hot path to one kotlinx pass over the message.
 */
@kotlinx.serialization.Serializable
private data class WsSessionsMessageDto(
    @kotlinx.serialization.SerialName("Data") val data: List<WsSessionInfoDto> = emptyList(),
)

/**
 * Decodes the `Data` array straight from a `Sessions` push's raw envelope text
 * (one kotlinx pass; no org.json re-serialization). Callers should filter by
 * session id on the DTOs *before* invoking [toSessionInfo] so only the matching
 * session pays for the full mapping.
 */
fun parseSessionsMessage(json: Json, rawText: String): List<WsSessionInfoDto> =
    json.decodeFromString(WsSessionsMessageDto.serializer(), rawText).data
