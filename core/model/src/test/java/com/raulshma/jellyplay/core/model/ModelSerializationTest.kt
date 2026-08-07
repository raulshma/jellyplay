package com.raulshma.jellyplay.core.model

import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `user preferences serialization roundtrip`() {
        val original = UserPreferences(
            preferredPlayer = PlayerType.EXTERNAL,
            preferredSubtitleLanguage = "eng",
            preferredAudioLanguage = "jpn",
            dynamicTheming = false,
            subtitleStyle = SubtitleStyle(
                fontSize = 32,
                fontColor = SubtitleColor.YELLOW,
                backgroundColor = SubtitleColor.BLACK,
                backgroundOpacity = 0.8f,
                edgeType = SubtitleEdgeType.OUTLINE,
                edgeColor = SubtitleColor.BLACK,
                offsetMs = 1500L,
            ),
            streamingQuality = StreamingQuality.FHD_1080P,
            maxCacheSizeMb = 2048,
            autoDeleteCache = false,
            pinLockEnabled = true,
            pinHash = "abc123",
            dialogueBoostEnabled = true,
            dialogueBoostStrength = EffectStrength.HIGH,
            nightModeStrength = EffectStrength.LOW,
        )

        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<UserPreferences>(serialized)

        assertEquals(original, deserialized)
    }

    @Test
    fun `user preferences default values`() {
        val defaults = UserPreferences()

        assertEquals(PlayerType.EXO_PLAYER, defaults.preferredPlayer)
        assertEquals(null, defaults.preferredSubtitleLanguage)
        assertEquals(null, defaults.preferredAudioLanguage)
        assertEquals(true, defaults.dynamicTheming)
        assertEquals(SubtitleStyle(), defaults.subtitleStyle)
        assertEquals(StreamingQuality.AUTO, defaults.streamingQuality)
        assertEquals(0, defaults.maxCacheSizeMb)
        assertEquals(true, defaults.autoDeleteCache)
        assertEquals(false, defaults.pinLockEnabled)
        assertEquals(null, defaults.pinHash)
        assertEquals(false, defaults.dialogueBoostEnabled)
        assertEquals(EffectStrength.MODERATE, defaults.dialogueBoostStrength)
        assertEquals(EffectStrength.MODERATE, defaults.nightModeStrength)
    }

    @Test
    fun `subtitle style serialization roundtrip`() {
        val original = SubtitleStyle(
            fontSize = 18,
            fontColor = SubtitleColor.CYAN,
            backgroundColor = SubtitleColor.BLUE,
            backgroundOpacity = 0.4f,
            edgeType = SubtitleEdgeType.DROP_SHADOW,
            edgeColor = SubtitleColor.RED,
            offsetMs = -500L,
        )

        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<SubtitleStyle>(serialized)

        assertEquals(original, deserialized)
    }

    @Test
    fun `streaming quality enum values`() {
        val qualities = StreamingQuality.entries
        assertEquals(6, qualities.size)
        assertEquals(StreamingQuality.AUTO, qualities[0])
        assertEquals(StreamingQuality.LOW_360P, qualities[1])
        assertEquals(StreamingQuality.SD_480P, qualities[2])
        assertEquals(StreamingQuality.HD_720P, qualities[3])
        assertEquals(StreamingQuality.FHD_1080P, qualities[4])
        assertEquals(StreamingQuality.UHD_4K, qualities[5])
    }

    @Test
    fun `media type enum values`() {
        val types = MediaType.entries
        assertEquals(15, types.size)
        assertEquals(MediaType.MOVIE, types[0])
        assertEquals(MediaType.SERIES, types[1])
        assertEquals(MediaType.SEASON, types[2])
        assertEquals(MediaType.EPISODE, types[3])
    }

    @Test
    fun `player type enum serialization`() {
        assertEquals("EXO_PLAYER", PlayerType.EXO_PLAYER.name)
        assertEquals("MPV", PlayerType.MPV.name)
        assertEquals("LIBVLC", PlayerType.LIBVLC.name)
        assertEquals("EXTERNAL", PlayerType.EXTERNAL.name)
        // Backward compatibility: old "INTERNAL" value migrates to EXO_PLAYER
        assertEquals(PlayerType.EXO_PLAYER, PlayerType.fromStoredName("INTERNAL"))
        assertEquals(PlayerType.MPV, PlayerType.fromStoredName("MPV"))
    }

    @Test
    fun `subtitle color hex values`() {
        assertEquals(0xFFFFFFFF.toInt(), SubtitleColor.WHITE.value)
        assertEquals(0xFFFFFF00.toInt(), SubtitleColor.YELLOW.value)
        assertEquals(0xFF00FF00.toInt(), SubtitleColor.GREEN.value)
        assertEquals(0xFF000000.toInt(), SubtitleColor.BLACK.value)
    }

    @Test
    fun `download item serialization roundtrip`() {
        val original = DownloadItem(
            id = "dl-1",
            mediaItemId = "item-1",
            name = "Test Movie",
            mediaType = MediaType.MOVIE,
            downloadPath = "/downloads/test.mp4",
            downloadUrl = "http://server/videos/item-1/stream",
            totalSizeBytes = 1_000_000_000L,
            downloadedBytes = 500_000_000L,
            status = DownloadStatus.DOWNLOADING,
            mediaSourceId = "source-1",
            imageUrl = "http://server/images/item-1",
        )

        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<DownloadItem>(serialized)

        assertEquals(original, deserialized)
    }

    @Test
    fun `download status enum values`() {
        val statuses = DownloadStatus.entries
        assertEquals(7, statuses.size)
        assertEquals(DownloadStatus.PENDING, statuses[0])
        assertEquals(DownloadStatus.QUEUED, statuses[1])
        assertEquals(DownloadStatus.DOWNLOADING, statuses[2])
        assertEquals(DownloadStatus.PAUSED, statuses[3])
        assertEquals(DownloadStatus.COMPLETED, statuses[4])
        assertEquals(DownloadStatus.FAILED, statuses[5])
        assertEquals(DownloadStatus.CANCELLED, statuses[6])
    }

    @Test
    fun `sync play group serialization roundtrip`() {
        val original = SyncPlayGroup(
            groupId = "group-1",
            groupName = "Movie Night",
            participantCount = 3,
            participants = listOf("Alice", "Bob", "Charlie"),
            playingItemId = "item-1",
            playingItemName = "Test Movie",
            isPlaying = true,
            positionTicks = 500_000_000L,
        )

        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<SyncPlayGroup>(serialized)

        assertEquals(original, deserialized)
    }

    @Test
    fun `sync play group info serialization roundtrip`() {
        val original = SyncPlayGroupInfo(
            groupId = "group-2",
            groupName = "Anime Watch",
            participants = listOf(
                SyncPlayParticipant(userId = "u1", userName = "User1", isConnected = true),
                SyncPlayParticipant(userId = "u2", userName = "User2", isConnected = false),
            ),
            playingItemId = "item-2",
            playingItemName = "Episode 1",
            isPlaying = false,
            positionTicks = 1_200_000_000L,
            playbackSpeed = 1.0f,
        )

        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<SyncPlayGroupInfo>(serialized)

        assertEquals(original, deserialized)
    }
}
