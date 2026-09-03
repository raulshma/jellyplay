package com.raulshma.jellyplay.core.model.remote

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Pins the invariants of the remote-control wire models — the payloads the
 * Jellyfin websocket "Play"/"Playstate"/"GeneralCommand" messages decode into:
 *
 *  - [PlaystateCommand] is a sealed hierarchy covering the nine server
 *    commands; every variant round-trips through polymorphic serialization
 *    (the socket dispatcher decodes on this), and the data objects are
 *    singletons — equality is by variant identity.
 *  - Only [PlaystateCommand.Seek] carries a payload (its
 *    [PlaystateCommand.Seek.positionTicks]).
 *  - [GeneralCommand] round-trips for the parameterised variants the remote
 *    receiver dispatches on (SetVolume with its nullable mute flag, and
 *    [GeneralCommand.Unknown] preserving the raw server command name).
 *  - [PlaybackDomain] routes a PlayRequest to the audio or video player.
 */
class RemoteControlRequestsTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ── PlaystateCommand ─────────────────────────────────────────────────────

    @Test
    fun `every playstate command round-trips`() {
        val commands: List<PlaystateCommand> = listOf(
            PlaystateCommand.Stop,
            PlaystateCommand.Pause,
            PlaystateCommand.Unpause,
            PlaystateCommand.NextTrack,
            PlaystateCommand.PreviousTrack,
            PlaystateCommand.Rewind,
            PlaystateCommand.FastForward,
            PlaystateCommand.PlayPause,
            PlaystateCommand.Seek(positionTicks = 1_234_567L),
        )
        for (command in commands) {
            val encoded = json.encodeToString(PlaystateCommand.serializer(), command)
            val decoded = json.decodeFromString(PlaystateCommand.serializer(), encoded)
            assertEquals(command, decoded, encoded)
        }
    }

    @Test
    fun `seek is the only payload-bearing playstate command`() {
        val encoded = json.encodeToString(PlaystateCommand.serializer(), PlaystateCommand.Seek(positionTicks = 42L))
        assertTrue(encoded.contains("42"), encoded)
        val decoded = json.decodeFromString(PlaystateCommand.serializer(), encoded)
        assertIs<PlaystateCommand.Seek>(decoded)
        assertEquals(42L, decoded.positionTicks)
    }

    @Test
    fun `playstate data objects decode to singletons`() {
        val decoded = json.decodeFromString(
            PlaystateCommand.serializer(),
            json.encodeToString(PlaystateCommand.serializer(), PlaystateCommand.Stop),
        )
        assertTrue(decoded === PlaystateCommand.Stop)
    }

    @Test
    fun `playstate polymorphism uses the type discriminator`() {
        val encoded = json.encodeToString(PlaystateCommand.serializer(), PlaystateCommand.Pause)
        val discriminator = json.parseToJsonElement(encoded).jsonObject["type"]!!.jsonPrimitive.content
        assertTrue(discriminator.endsWith(".Pause"), encoded)
    }

    // ── GeneralCommand ───────────────────────────────────────────────────────

    @Test
    fun `parameterised general commands round-trip`() {
        val commands: List<GeneralCommand> = listOf(
            GeneralCommand.SetVolume(volume0to100 = 70, mute = null),
            GeneralCommand.SetVolume(volume0to100 = 0, mute = true),
            GeneralCommand.SetAudioStreamIndex(index = 2),
            GeneralCommand.SetSubtitleStreamIndex(index = 3),
            GeneralCommand.SetRepeatMode(mode = "RepeatAll"),
            GeneralCommand.SetShuffleQueue(shuffle = true),
            GeneralCommand.SetPlaybackOrder(order = "Random"),
            GeneralCommand.SetMaxStreamingBitrate(bitrate = 8_000_000),
            GeneralCommand.DisplayMessage(header = "h", text = "t", timeoutMs = 5_000),
            GeneralCommand.Unknown(name = "SomeFutureCommand"),
        )
        for (command in commands) {
            val encoded = json.encodeToString(GeneralCommand.serializer(), command)
            val decoded = json.decodeFromString(GeneralCommand.serializer(), encoded)
            assertEquals(command, decoded, encoded)
        }
    }

    @Test
    fun `object general commands round-trip`() {
        val commands: List<GeneralCommand> = listOf(
            GeneralCommand.VolumeUp,
            GeneralCommand.VolumeDown,
            GeneralCommand.Mute,
            GeneralCommand.Unmute,
            GeneralCommand.ToggleMute,
            GeneralCommand.ToggleFullscreen,
        )
        for (command in commands) {
            val encoded = json.encodeToString(GeneralCommand.serializer(), command)
            assertEquals(command, json.decodeFromString(GeneralCommand.serializer(), encoded), encoded)
        }
    }

    @Test
    fun `unknown command preserves the raw server name`() {
        val encoded = json.encodeToString(
            GeneralCommand.serializer(),
            GeneralCommand.Unknown(name = "SetVolume2"),
        )
        val discriminator = json.parseToJsonElement(encoded).jsonObject["type"]!!.jsonPrimitive.content
        assertTrue(discriminator.endsWith(".Unknown"), encoded)
        val decoded = json.decodeFromString(GeneralCommand.serializer(), encoded) as GeneralCommand.Unknown
        assertEquals("SetVolume2", decoded.name)
    }

    // ── PlayRequest / PlaybackDomain ─────────────────────────────────────────

    @Test
    fun `play request round-trips with defaults`() {
        val request = PlayRequest(itemIds = listOf("a", "b"), startPositionTicks = 10L)
        val encoded = json.encodeToString(request)
        assertEquals(request, json.decodeFromString<PlayRequest>(encoded))
        assertEquals("PlayNow", request.playCommand)
        assertEquals(0, request.startIndex)
    }

    @Test
    fun `playback domain covers both players plus the unrouted fallback`() {
        assertEquals(setOf("AUDIO", "VIDEO", "UNKNOWN"), PlaybackDomain.entries.map { it.name }.toSet())
    }
}
