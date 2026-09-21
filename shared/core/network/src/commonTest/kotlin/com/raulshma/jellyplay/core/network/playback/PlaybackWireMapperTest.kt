package com.raulshma.jellyplay.core.network.playback

import com.raulshma.jellyplay.core.model.PlayMethod
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the playback wire contract: the PlaybackInfo response decode, the
 * progress-report request bodies, transcode-reason name conversion,
 * media-segment decode and the server-time passthrough. DTO→model mapping
 * semantics are pinned jvmShared-side (`JellyfinDtoMappers` /
 * `PlaybackApiClientImplTest`).
 */
class PlaybackWireMapperTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `playback info response decodes play session id and pascalCase media sources`() {
        val response = json.decodeFromString<PlaybackInfoResponseDtoWire>(
            """
            {
              "PlaySessionId": "ps-1",
              "ErrorCode": null,
              "MediaSources": [
                {
                  "Id": "ms1", "Name": "1080p", "Container": "mp4",
                  "SupportsTranscoding": true, "SupportsDirectStream": false,
                  "SupportsDirectPlay": false,
                  "TranscodingUrl": "/videos/i/master.m3u8?MediaSourceId=ms1",
                  "MediaStreams": [
                    {"Index": 0, "Type": "Video", "Codec": "h264"},
                    {"Index": 1, "Type": "Audio", "Codec": "aac", "IsDefault": true},
                    {"Index": 2, "Type": "Subtitle", "Codec": "srt", "IsExternal": true,
                     "DeliveryUrl": "/Videos/i/ms1/Subtitles/2/Stream.srt"}
                  ]
                }
              ]
            }
            """.trimIndent(),
        )
        assertEquals("ps-1", response.playSessionId)
        // The wire decode pin: PascalCase fields land on the right members
        // (DTO→model mapping semantics are pinned jvmShared-side).
        val source = response.mediaSources.single()
        assertEquals("ms1", source.id)
        assertEquals("1080p", source.name)
        assertEquals("mp4", source.container)
        assertTrue(source.supportsTranscoding == true)
        assertFalse(source.supportsDirectPlay == true)
        assertEquals("/videos/i/master.m3u8?MediaSourceId=ms1", source.transcodingUrl)
        assertEquals(3, source.mediaStreams?.size)
        assertEquals("srt", source.mediaStreams?.get(2)?.codec)
        assertTrue(source.mediaStreams?.get(2)?.isExternal == true)
        assertEquals("/Videos/i/ms1/Subtitles/2/Stream.srt", source.mediaStreams?.get(2)?.deliveryUrl)
    }

    @Test
    fun `playback start and stop bodies serialize to pascalCase wire`() {
        val start = json.encodeToString(
            PlaybackStartInfoDtoWire(
                canSeek = true,
                itemId = "item-1",
                sessionId = "sess-1",
                isPaused = false,
                isMuted = false,
                playMethod = PlayMethod.DIRECT_STREAM.wireName(),
                repeatMode = "RepeatNone",
                playbackOrder = "Default",
            ),
        )
        assertEquals(
            """{"CanSeek":true,"ItemId":"item-1","SessionId":"sess-1","IsPaused":false,"IsMuted":false,"PlayMethod":"DirectStream","RepeatMode":"RepeatNone","PlaybackOrder":"Default"}""",
            start,
        )

        val stop = json.encodeToString(
            PlaybackStopInfoDtoWire(itemId = "item-1", sessionId = "sess-1", positionTicks = 9000, failed = false),
        )
        assertEquals(
            """{"ItemId":"item-1","SessionId":"sess-1","PositionTicks":9000,"Failed":false}""",
            stop,
        )
    }

    @Test
    fun `transcode reasons convert serial names to enum constant names`() {
        assertEquals("CONTAINER_NOT_SUPPORTED", transcodeReasonName("ContainerNotSupported"))
        assertEquals("VIDEO_RANGE_TYPE_NOT_SUPPORTED", transcodeReasonName("VideoRangeTypeNotSupported"))
        assertEquals("DIRECT_PLAY_ERROR", transcodeReasonName("DirectPlayError"))
        assertEquals("AUDIO_IS_EXTERNAL", transcodeReasonName("AudioIsExternal"))
    }

    @Test
    fun `session scan finds this device playing the item`() {
        val sessions = json.decodeFromString<List<SessionInfoDtoWire>>(
            """
            [
              {"DeviceId": "other", "NowPlayingItem": {"Id": "item-1"},
               "TranscodingInfo": {"TranscodeReasons": ["ContainerNotSupported"]}},
              {"DeviceId": "me", "NowPlayingItem": {"Id": "item-1"},
               "TranscodingInfo": {"TranscodeReasons": ["VideoCodecNotSupported", "AudioIsExternal"]}},
              {"DeviceId": "me", "NowPlayingItem": {"Id": "item-2"}, "TranscodingInfo": {}}
            ]
            """.trimIndent(),
        )
        val reasons = sessions
            .firstOrNull { it.deviceId == "me" && it.nowPlayingItem?.id == "item-1" }
            ?.transcodingInfo?.transcodeReasons
            .orEmpty()
            .map { transcodeReasonName(it) }
        assertEquals(listOf("VIDEO_CODEC_NOT_SUPPORTED", "AUDIO_IS_EXTERNAL"), reasons)
    }

    @Test
    fun `media segments decode and map to the app model`() {
        val result = json.decodeFromString<MediaSegmentQueryResultDtoWire>(
            """
            {"Items": [
               {"Id": "seg-1", "ItemId": "item-1", "Type": "Intro",
                "StartTicks": 100, "EndTicks": 200},
               {"Id": "seg-2", "ItemId": "item-1", "Type": "Commercial",
                "StartTicks": 300, "EndTicks": 400}
             ], "TotalRecordCount": 2}
            """.trimIndent(),
        )
        val segments = result.items.map {
            com.raulshma.jellyplay.core.model.MediaSegment(
                id = it.id ?: "",
                itemId = it.itemId ?: "item-1",
                type = com.raulshma.jellyplay.core.model.MediaSegmentType.fromApiName(it.type ?: ""),
                startTicks = it.startTicks,
                endTicks = it.endTicks,
            )
        }
        assertEquals(com.raulshma.jellyplay.core.model.MediaSegmentType.INTRO, segments[0].type)
        assertEquals(com.raulshma.jellyplay.core.model.MediaSegmentType.COMMERCIAL, segments[1].type)
        assertEquals(100L, segments[0].startTicks)
    }

    @Test
    fun `utc time keeps raw wire strings`() {
        val dto = json.decodeFromString<UtcTimeDtoWire>(
            """{"RequestReceptionTime":"2026-08-24T10:00:00.0000000Z",
               "ResponseTransmissionTime":"2026-08-24T10:00:00.1000000Z"}""",
        )
        assertEquals("2026-08-24T10:00:00.0000000Z", dto.requestReceptionTime ?: "")
        assertEquals("2026-08-24T10:00:00.1000000Z", dto.responseTransmissionTime ?: "")
    }
}
