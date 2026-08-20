package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.feature.player.video.state.EpisodeBrowserState
import com.raulshma.jellyplay.feature.player.video.state.SegmentState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Supplementary tests for VideoPlayerUiState covering gaps in the existing suite. */
class VideoPlayerUiStateExtendedTest {

    // ─── Chapter segment detection ─────────────────────────────────────────────

    @Test
    fun detectChapterSegment_introChapterName_returnsIntroSegment() {
        val state = VideoPlayerUiState(
            currentPosition = 5_000L,  // 5 seconds
            duration = 3_600_000L,
            chapters = listOf(
                ChapterInfo(name = "Intro", startPositionTicks = 0L),
                ChapterInfo(name = "Main", startPositionTicks = 600_000_000L), // 1 minute
            ),
        )
        val seg = state.activeSegment
        assertNotNull(seg)
        assertEquals(MediaSegmentType.INTRO, seg!!.type)
    }

    @Test
    fun detectChapterSegment_outroChapterName_returnsOutroSegment() {
        val state = VideoPlayerUiState(
            currentPosition = 3_550_000L,  // near end, within credits chapter
            duration = 3_600_000L,
            chapters = listOf(
                ChapterInfo(name = "Main", startPositionTicks = 0L),
                ChapterInfo(name = "Credits", startPositionTicks = 35_400_000_000L),
            ),
        )
        val seg = state.activeSegment
        assertNotNull(seg)
        assertEquals(MediaSegmentType.OUTRO, seg!!.type)
    }

    @Test
    fun detectChapterSegment_unrecognizedName_returnsNull() {
        val state = VideoPlayerUiState(
            currentPosition = 30_000L,
            duration = 3_600_000L,
            chapters = listOf(
                ChapterInfo(name = "Act 1", startPositionTicks = 0L),
                ChapterInfo(name = "Act 2", startPositionTicks = 600_000_000L),
            ),
        )
        assertNull(state.activeSegment)
    }

    @Test
    fun detectChapterSegment_usesNextChapterStartAsEnd() {
        val state = VideoPlayerUiState(
            currentPosition = 5_000L,
            duration = 3_600_000L,
            chapters = listOf(
                ChapterInfo(name = "Intro", startPositionTicks = 0L),
                ChapterInfo(name = "Main", startPositionTicks = 300_000_000L), // 30 seconds ticks
            ),
        )
        val seg = state.activeSegment
        assertNotNull(seg)
        assertEquals(300_000_000L, seg!!.endTicks)
    }

    @Test
    fun detectChapterSegment_lastChapter_usesDurationAsEnd() {
        val duration = 3_600_000L
        val state = VideoPlayerUiState(
            currentPosition = 3_550_000L,
            duration = duration,
            chapters = listOf(
                ChapterInfo(name = "Main", startPositionTicks = 0L),
                ChapterInfo(name = "Credits", startPositionTicks = 35_400_000_000L),
            ),
        )
        val seg = state.activeSegment
        assertNotNull(seg)
        assertEquals(duration * 10_000, seg!!.endTicks)
    }

    @Test
    fun detectChapterSegment_noChapters_returnsNull() {
        val state = VideoPlayerUiState(currentPosition = 30_000L, chapters = emptyList())
        assertNull(state.activeSegment)
    }

    // ─── isInSegmentType ──────────────────────────────────────────────────────

    @Test
    fun isInSegmentType_introWithShowBehavior_trueWhenInIntro() {
        val state = VideoPlayerUiState(
            currentPosition = 5_000L,
            duration = 3_600_000L,
            chapters = listOf(
                ChapterInfo(name = "Intro", startPositionTicks = 0L),
                ChapterInfo(name = "Main", startPositionTicks = 300_000_000L),
            ),
            segmentState = SegmentState(
                segmentBehaviors = mapOf(MediaSegmentType.INTRO to SegmentBehavior.SHOW_BUTTON),
            ),
        )
        assertTrue(state.isInSegmentType(MediaSegmentType.INTRO))
    }

    @Test
    fun isInSegmentType_introWithIgnoreBehavior_falseEvenWhenInIntro() {
        val state = VideoPlayerUiState(
            currentPosition = 5_000L,
            duration = 3_600_000L,
            chapters = listOf(
                ChapterInfo(name = "Intro", startPositionTicks = 0L),
                ChapterInfo(name = "Main", startPositionTicks = 300_000_000L),
            ),
            segmentState = SegmentState(
                segmentBehaviors = mapOf(MediaSegmentType.INTRO to SegmentBehavior.IGNORE),
            ),
        )
        assertFalse(state.isInSegmentType(MediaSegmentType.INTRO))
    }

    @Test
    fun isInSegmentType_outro_falseWhenInIntro() {
        val state = VideoPlayerUiState(
            currentPosition = 5_000L,
            duration = 3_600_000L,
            chapters = listOf(
                ChapterInfo(name = "Intro", startPositionTicks = 0L),
                ChapterInfo(name = "Main", startPositionTicks = 300_000_000L),
            ),
            segmentState = SegmentState(
                segmentBehaviors = mapOf(MediaSegmentType.INTRO to SegmentBehavior.SHOW_BUTTON),
            ),
        )
        assertFalse(state.isInSegmentType(MediaSegmentType.OUTRO))
    }

    // ─── segmentEndTicksForType ────────────────────────────────────────────────

    @Test
    fun segmentEndTicksForType_introType_returnsEndTicks() {
        val segment = MediaSegment(
            id = "intro1", itemId = "item1",
            type = MediaSegmentType.INTRO,
            startTicks = 0L, endTicks = 300_000_000L,
        )
        val state = VideoPlayerUiState(
            currentPosition = 5_000L,
            segmentState = SegmentState(
                segments = listOf(segment),
                segmentBehaviors = mapOf(MediaSegmentType.INTRO to SegmentBehavior.SHOW_BUTTON),
            ),
        )
        assertEquals(300_000_000L, state.segmentEndTicksForType(MediaSegmentType.INTRO))
    }

    @Test
    fun segmentEndTicksForType_outroType_returnsNullWhenActiveIsIntro() {
        val segment = MediaSegment(
            id = "intro1", itemId = "item1",
            type = MediaSegmentType.INTRO,
            startTicks = 0L, endTicks = 300_000_000L,
        )
        val state = VideoPlayerUiState(
            currentPosition = 5_000L,
            segmentState = SegmentState(
                segments = listOf(segment),
                segmentBehaviors = mapOf(MediaSegmentType.INTRO to SegmentBehavior.SHOW_BUTTON),
            ),
        )
        assertNull(state.segmentEndTicksForType(MediaSegmentType.OUTRO))
    }

    @Test
    fun segmentEndTicksForType_noActiveSegment_returnsNull() {
        val state = VideoPlayerUiState(currentPosition = 30_000L, segmentState = SegmentState(segments = emptyList()))
        assertNull(state.segmentEndTicksForType(MediaSegmentType.INTRO))
    }

    // ─── segmentEndTicks(segment) ─────────────────────────────────────────────

    @Test
    fun segmentEndTicks_hasSegmentTrue_returnsEndTicks() {
        val seg = MediaSegment(
            id = "s1", itemId = "i1",
            type = MediaSegmentType.INTRO,
            startTicks = 0L, endTicks = 600_000_000L,
        )
        val state = VideoPlayerUiState(segmentState = SegmentState(segments = listOf(seg)))
        assertEquals(600_000_000L, state.segmentEndTicks(seg))
    }

    @Test
    fun segmentEndTicks_hasSegmentFalse_returnsNull() {
        val seg = MediaSegment(
            id = "s1", itemId = "i1",
            type = MediaSegmentType.INTRO,
            startTicks = 0L, endTicks = 0L,
        )
        val state = VideoPlayerUiState()
        assertNull(state.segmentEndTicks(seg))
    }

    // ─── isOutroNearEnd ───────────────────────────────────────────────────────

    @Test
    fun isOutroNearEnd_outroEndsNearDuration_true() {
        // Outro ends 10 seconds before the end (10s < 30s threshold)
        val duration = 3_600_000L   // 60-minute video in ms
        val outroEndTicks = (duration - 10_000L) * 10_000L // 10 seconds before end in ticks
        val segment = MediaSegment(
            id = "outro", itemId = "item",
            type = MediaSegmentType.OUTRO,
            startTicks = (duration - 60_000L) * 10_000L,
            endTicks = outroEndTicks,
        )
        val state = VideoPlayerUiState(
            currentPosition = duration - 30_000L,
            duration = duration,
            segmentState = SegmentState(
                segments = listOf(segment),
                segmentBehaviors = mapOf(MediaSegmentType.OUTRO to SegmentBehavior.SHOW_BUTTON),
            ),
        )
        assertTrue(state.isOutroNearEnd)
    }

    @Test
    fun isOutroNearEnd_outroEndsLongBeforeDuration_false() {
        // Outro ends 5 minutes before the end
        val duration = 3_600_000L
        val outroEndTicks = (duration - 300_000L) * 10_000L
        val segment = MediaSegment(
            id = "outro", itemId = "item",
            type = MediaSegmentType.OUTRO,
            startTicks = (duration - 600_000L) * 10_000L,
            endTicks = outroEndTicks,
        )
        val state = VideoPlayerUiState(
            currentPosition = duration - 350_000L,
            duration = duration,
            segmentState = SegmentState(
                segments = listOf(segment),
                segmentBehaviors = mapOf(MediaSegmentType.OUTRO to SegmentBehavior.SHOW_BUTTON),
            ),
        )
        assertFalse(state.isOutroNearEnd)
    }

    @Test
    fun isOutroNearEnd_zeroDuration_false() {
        val state = VideoPlayerUiState(duration = 0L)
        assertFalse(state.isOutroNearEnd)
    }

    // ─── hdrType ──────────────────────────────────────────────────────────────

    @Test
    fun hdrType_noStreams_returnsNull() {
        val state = VideoPlayerUiState(mediaStreams = emptyList())
        assertNull(state.hdrType)
    }

    @Test
    fun hdrType_sdrVideoStream_returnsNull() {
        val state = VideoPlayerUiState(
            mediaStreams = listOf(MediaStream(index = 0, type = StreamType.VIDEO, videoRange = "SDR"))
        )
        assertNull(state.hdrType)
    }

    @Test
    fun hdrType_hdrVideoStream_returnsRange() {
        val state = VideoPlayerUiState(
            mediaStreams = listOf(MediaStream(index = 0, type = StreamType.VIDEO, videoRange = "HDR10"))
        )
        assertEquals("HDR10", state.hdrType)
    }

    @Test
    fun hdrType_noVideoStream_returnsNull() {
        val state = VideoPlayerUiState(
            mediaStreams = listOf(MediaStream(index = 0, type = StreamType.AUDIO))
        )
        assertNull(state.hdrType)
    }

    // ─── videoFrameRate ───────────────────────────────────────────────────────

    @Test
    fun videoFrameRate_noVideoStream_returnsNull() {
        val state = VideoPlayerUiState(mediaStreams = emptyList())
        assertNull(state.videoFrameRate)
    }

    @Test
    fun videoFrameRate_videoStreamWithRate_returnsRate() {
        val state = VideoPlayerUiState(
            mediaStreams = listOf(MediaStream(index = 0, type = StreamType.VIDEO, realFrameRate = 23.976f))
        )
        assertEquals(23.976f, state.videoFrameRate!!, 0.001f)
    }

    // ─── behaviorForType ──────────────────────────────────────────────────────

    @Test
    fun behaviorForType_defaultsToIgnore_forUnspecifiedType() {
        val state = VideoPlayerUiState(segmentState = SegmentState(segmentBehaviors = emptyMap()))
        assertEquals(SegmentBehavior.IGNORE, state.behaviorForType(MediaSegmentType.COMMERCIAL))
    }

    @Test
    fun behaviorForType_customBehavior_returnsCustom() {
        val state = VideoPlayerUiState(
            segmentState = SegmentState(
                segmentBehaviors = mapOf(MediaSegmentType.COMMERCIAL to SegmentBehavior.AUTO_SKIP)
            )
        )
        assertEquals(SegmentBehavior.AUTO_SKIP, state.behaviorForType(MediaSegmentType.COMMERCIAL))
    }

    @Test
    fun behaviorForType_showSkipButton_returnsShowSkipButton() {
        val state = VideoPlayerUiState(
            segmentState = SegmentState(
                segmentBehaviors = mapOf(MediaSegmentType.INTRO to SegmentBehavior.SHOW_BUTTON)
            )
        )
        assertEquals(SegmentBehavior.SHOW_BUTTON, state.behaviorForType(MediaSegmentType.INTRO))
    }
}

/**
 * SyncPlay state tests. The group-display slice (name/count/sync status/
 * repeat/shuffle) moved to SyncPlayUiState, owned by SyncPlayBridge;
 * the session flag stays on VideoPlayerUiState because it feeds the
 * segment-overlay projection.
 */
class VideoPlayerUiStateSyncPlayTest {

    @Test
    fun syncPlay_defaults_groupNameIsNull() {
        val state = com.raulshma.jellyplay.feature.player.video.state.SyncPlayUiState()
        assertNull(state.syncPlayGroupName)
    }

    @Test
    fun syncPlay_defaults_participantCountIsZero() {
        val state = com.raulshma.jellyplay.feature.player.video.state.SyncPlayUiState()
        assertEquals(0, state.syncPlayParticipantCount)
    }

    @Test
    fun syncPlay_defaults_isSyncedFalse() {
        val state = com.raulshma.jellyplay.feature.player.video.state.SyncPlayUiState()
        assertFalse(state.isSyncPlaySynced)
    }

    @Test
    fun syncPlay_defaults_repeatModeIsNone() {
        val state = com.raulshma.jellyplay.feature.player.video.state.SyncPlayUiState()
        assertEquals(SyncPlayRepeatMode.REPEAT_NONE, state.syncPlayRepeatMode)
    }

    @Test
    fun syncPlay_defaults_shuffleModeIsSorted() {
        val state = com.raulshma.jellyplay.feature.player.video.state.SyncPlayUiState()
        assertEquals(SyncPlayShuffleMode.SORTED, state.syncPlayShuffleMode)
    }

    @Test
    fun syncPlay_defaults_isInSessionFalse() {
        val state = VideoPlayerUiState()
        assertFalse(state.isInSyncPlaySession)
    }

    @Test
    fun shouldShowUpNext_falseWhenInSyncPlaySession() {
        val state = VideoPlayerUiState(
            isInSyncPlaySession = true,
            episodes = EpisodeBrowserState(
                nextEpisode = com.raulshma.jellyplay.core.model.MediaItem(
                    id = "ep2", name = "Episode 2",
                    mediaType = com.raulshma.jellyplay.core.model.MediaType.EPISODE
                ),
            ),
            seriesId = "series1",
            duration = 3_600_000L,
            currentPosition = 3_570_000L, // would normally show up-next
        )
        assertFalse(state.shouldShowUpNext)
    }

    @Test
    fun shouldShowUpNext_falseWhenNoNextEpisode() {
        val state = VideoPlayerUiState(
            isInSyncPlaySession = false,
            episodes = EpisodeBrowserState(nextEpisode = null),
            seriesId = "series1",
            duration = 3_600_000L,
            currentPosition = 3_570_000L,
        )
        assertFalse(state.shouldShowUpNext)
    }

    @Test
    fun shouldShowUpNext_falseWhenNoSeriesId() {
        val state = VideoPlayerUiState(
            isInSyncPlaySession = false,
            episodes = EpisodeBrowserState(
                nextEpisode = com.raulshma.jellyplay.core.model.MediaItem(
                    id = "ep2", name = "Episode 2",
                    mediaType = com.raulshma.jellyplay.core.model.MediaType.EPISODE
                ),
            ),
            seriesId = null,
            duration = 3_600_000L,
            currentPosition = 3_570_000L,
        )
        assertFalse(state.shouldShowUpNext)
    }

    @Test
    fun syncPlay_copy_updatesGroupName() {
        val state = com.raulshma.jellyplay.feature.player.video.state.SyncPlayUiState()
            .copy(syncPlayGroupName = "Group 1")
        assertEquals("Group 1", state.syncPlayGroupName)
    }

    @Test
    fun syncPlay_copy_updatesParticipantCount() {
        val state = com.raulshma.jellyplay.feature.player.video.state.SyncPlayUiState()
            .copy(syncPlayParticipantCount = 3)
        assertEquals(3, state.syncPlayParticipantCount)
    }

    @Test
    fun syncPlay_copy_updatesSynced() {
        val state = com.raulshma.jellyplay.feature.player.video.state.SyncPlayUiState()
            .copy(isSyncPlaySynced = true)
        assertTrue(state.isSyncPlaySynced)
    }
}
