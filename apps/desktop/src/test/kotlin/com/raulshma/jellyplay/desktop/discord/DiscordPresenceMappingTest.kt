package com.raulshma.jellyplay.desktop.discord

import com.raulshma.jellyplay.core.data.playback.NowPlayingReporter
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.deeplink.DeepLinkGrammar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The activity-mapping truth table (feature 4.2): video → WATCHING, music →
 * LISTENING, no meta → clear; the end timestamp rides a playing item with a
 * known length; the SyncPlay group attaches the party + the Join secret,
 * whose payload round-trips back into the deep-link grammar; the
 * `SET_ACTIVITY` payload serializes both shapes (activity + clear).
 */
class DiscordPresenceMappingTest {

    private val nowMs = 1_000_000_000L

    private fun videoMeta(
        positionMs: Long = 10 * 60_000L,
        durationMs: Long? = 24 * 60_000L,
    ) = NowPlayingReporter.NowPlayingMeta(
        itemId = "video-1",
        title = "The Movie",
        subtitle = "The Show · S1E5",
        kind = NowPlayingReporter.Kind.VIDEO,
        positionMs = positionMs,
        durationMs = durationMs,
    )

    private fun musicMeta() = NowPlayingReporter.NowPlayingMeta(
        itemId = "track-1",
        title = "Song A",
        subtitle = "Artist A",
        kind = NowPlayingReporter.Kind.MUSIC,
        positionMs = 30_000,
        durationMs = 200_000,
    )

    private fun group(participants: Int = 2) = SyncPlayGroup(
        groupId = "group-abc",
        groupName = "Movie Night",
        participantCount = participants,
    )

    // ── mapActivity ────────────────────────────────────────────────────────

    @Test
    fun noMeta_mapsToClear() {
        assertNull(DiscordPresenceMapping.mapActivity(null, isPlaying = false, group = null, nowMs = nowMs))
    }

    @Test
    fun video_mapsToWatching_withTheEpisodeLine() {
        val spec = DiscordPresenceMapping.mapActivity(videoMeta(), isPlaying = true, group = null, nowMs = nowMs)
        assertEquals(DiscordPresenceMapping.ACTIVITY_TYPE_WATCHING, spec?.type)
        assertEquals("The Movie", spec?.details)
        assertEquals("The Show · S1E5", spec?.state)
    }

    @Test
    fun music_mapsToListening_withTheArtist() {
        val spec = DiscordPresenceMapping.mapActivity(musicMeta(), isPlaying = true, group = null, nowMs = nowMs)
        assertEquals(DiscordPresenceMapping.ACTIVITY_TYPE_LISTENING, spec?.type)
        assertEquals("Song A", spec?.details)
        assertEquals("Artist A", spec?.state)
    }

    @Test
    fun aPlayingItemWithDuration_carriesTheEndTimestamp() {
        val spec = DiscordPresenceMapping.mapActivity(videoMeta(), isPlaying = true, group = null, nowMs = nowMs)
        // now + (duration - position) = 14 minutes of remaining video.
        assertEquals(nowMs + (24 * 60_000L - 10 * 60_000L), spec?.endTimestampMs)
    }

    @Test
    fun aPausedItem_carriesNoEndTimestamp() {
        val spec = DiscordPresenceMapping.mapActivity(videoMeta(), isPlaying = false, group = null, nowMs = nowMs)
        assertNull(spec?.endTimestampMs, "paused items show no countdown")
    }

    @Test
    fun anUnknownDuration_carriesNoEndTimestamp() {
        val spec = DiscordPresenceMapping.mapActivity(videoMeta(durationMs = null), isPlaying = true, group = null, nowMs = nowMs)
        assertNull(spec?.endTimestampMs)
    }

    @Test
    fun aPositionPastTheEnd_clampsTheEndTimestampToNow() {
        val spec = DiscordPresenceMapping.mapActivity(
            videoMeta(positionMs = 99 * 60_000L),
            isPlaying = true,
            group = null,
            nowMs = nowMs,
        )
        assertEquals(nowMs, spec?.endTimestampMs)
    }

    @Test
    fun syncPlayGroupAttaches_thePartyAndJoinSecret() {
        val spec = DiscordPresenceMapping.mapActivity(videoMeta(), isPlaying = true, group = group(), nowMs = nowMs)
        assertEquals("syncplay:group-abc", spec?.partyId)
        assertEquals(2, spec?.partySize)
        assertEquals("jellyplay://syncplay/group-abc", spec?.joinSecret)
    }

    @Test
    fun aZeroParticipantCount_clampsThePartyFloorToOne() {
        val spec = DiscordPresenceMapping.mapActivity(
            videoMeta(),
            isPlaying = true,
            group = group(participants = 0),
            nowMs = nowMs,
        )
        assertEquals(1, spec?.partySize)
    }

    @Test
    fun anOverfullGroup_clampsThePartyAtTheCeiling() {
        val spec = DiscordPresenceMapping.mapActivity(
            videoMeta(),
            isPlaying = true,
            group = group(participants = 99),
            nowMs = nowMs,
        )
        assertEquals(DiscordPresenceMapping.PARTY_MAX, spec?.partySize)
    }

    @Test
    fun aBlankGroupId_meansNoParty() {
        val spec = DiscordPresenceMapping.mapActivity(videoMeta(), isPlaying = true, group = group().copy(groupId = ""), nowMs = nowMs)
        assertNull(spec?.partyId)
        assertNull(spec?.joinSecret)
    }

    @Test
    fun longTitles_trimInsideTheFrameBudget() {
        val spec = DiscordPresenceMapping.mapActivity(
            videoMeta().copy(title = "T".repeat(5_000)),
            isPlaying = true,
            group = null,
            nowMs = nowMs,
        )
        assertTrue((spec?.details?.length ?: 0) < 5_000)
        assertTrue(
            DiscordPresenceMapping.setActivityPayload(spec, pid = 1, nonce = "n").encodeToByteArray().size
                <= DiscordIpcFrameCodec.MAX_PAYLOAD_BYTES,
            "the serialized payload stays within Discord's frame cap",
        )
    }

    // ── join secret round trip (into the deep-link grammar) ────────────────

    @Test
    fun joinSecret_roundTripsThroughTheGrammar() {
        val secret = DeepLinkGrammar.syncPlayJoinLink("group-abc")
        assertEquals("group-abc", DiscordPresenceMapping.decodeJoinSecret(secret))
    }

    @Test
    fun foreignSecrets_doNotDecode() {
        assertNull(DiscordPresenceMapping.decodeJoinSecret("otherapp://syncplay/group-abc"))
        assertNull(DiscordPresenceMapping.decodeJoinSecret("jellyplay://media/group-abc"))
        assertNull(DiscordPresenceMapping.decodeJoinSecret("jellyplay://syncplay/"))
        assertNull(DiscordPresenceMapping.decodeJoinSecret("not-a-link"))
    }

    // ── SET_ACTIVITY payload ───────────────────────────────────────────────

    @Test
    fun setActivityPayload_serializesTheActivityShape() {
        val payload = DiscordPresenceMapping.setActivityPayload(
            spec = DiscordPresenceMapping.mapActivity(videoMeta(), isPlaying = true, group = group(), nowMs = nowMs),
            pid = 4242,
            nonce = "nonce-1",
        )
        assertTrue(payload.contains("\"cmd\":\"SET_ACTIVITY\""))
        assertTrue(payload.contains("\"nonce\":\"nonce-1\""))
        assertTrue(payload.contains("\"pid\":4242"))
        assertTrue(payload.contains("\"type\":${DiscordPresenceMapping.ACTIVITY_TYPE_WATCHING}"))
        assertTrue(payload.contains("\"details\":\"The Movie\""))
        assertTrue(payload.contains("\"party\":{\"id\":\"syncplay:group-abc\",\"size\":[2,16]}"))
        assertTrue(payload.contains("\"join\":\"jellyplay://syncplay/group-abc\""))
        assertTrue(payload.contains("\"instance\":true"))
    }

    @Test
    fun setActivityPayload_nullSpec_isTheClearShape() {
        val payload = DiscordPresenceMapping.setActivityPayload(spec = null, pid = 4242, nonce = "nonce-2")
        assertTrue(payload.contains("\"activity\":null"))
        assertTrue(payload.contains("\"pid\":4242"))
    }

    @Test
    fun setActivityPayload_ofTheFullSpec_staysWithinTheFrameCap() {
        val payload = DiscordPresenceMapping.setActivityPayload(
            spec = DiscordPresenceMapping.mapActivity(videoMeta(), isPlaying = true, group = group(), nowMs = nowMs),
            pid = 4242,
            nonce = DiscordPresenceMapping.newNonce(),
        )
        assertNotNull(DiscordIpcFrameCodec.decode(DiscordIpcFrameCodec.encode(DiscordIpcFrame(DiscordIpcOpCode.OP_FRAME, payload))))
    }
}
