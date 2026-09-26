package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.TrackType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the shared mpv track-list → [MediaTrack] catalog: side-loaded id
 * stamping (the `offline:`/`external:` resolution contract), synthetic ids
 * for demuxed tracks, [TrackLabelFormatter] labels/badges and the audio/sub
 * filter.
 */
class MpvTrackCatalogTest {

    @Test
    fun demuxedTracks_getSyntheticIdsAndFormattedLabels() {
        val tracks = MpvTrackCatalog.mediaTracks(
            entries = listOf(
                MpvTrackCatalog.MpvTrackEntry(
                    type = "audio", id = 1,
                    title = "English 5.1", lang = "eng", codec = "aac",
                    selected = true, ffIndex = 1,
                ),
                MpvTrackCatalog.MpvTrackEntry(
                    type = "sub", id = 3,
                    title = "English SDH", lang = "eng", codec = "subrip",
                    ffIndex = 4, forced = false, default = true,
                ),
            ),
        )
        assertEquals(2, tracks.size)

        val audio = tracks[0]
        assertEquals("mpv_audio_1", audio.id)
        assertEquals(1, audio.index)
        assertEquals(TrackType.AUDIO, audio.type)
        assertEquals("eng", audio.language)
        assertEquals(1, audio.streamIndex)
        assertTrue(audio.isSelected)
        assertTrue(audio.badges.isEmpty())

        val sub = tracks[1]
        assertEquals("mpv_sub_3", sub.id)
        assertEquals(TrackType.SUBTITLE, sub.type)
        // "SDH" in the title marks the hearing-impaired badge, which suppresses
        // the Default badge (TrackLabelFormatter.badges).
        assertEquals(listOf(TrackBadge.SDH), sub.badges)
    }

    @Test
    fun externalSubtitle_withRegisteredLabel_getsTheCallerId() {
        val tracks = MpvTrackCatalog.mediaTracks(
            entries = listOf(
                MpvTrackCatalog.MpvTrackEntry(type = "sub", id = 1, title = "My Sidecar", external = true),
            ),
            sideLoadedSubtitleIds = mapOf("My Sidecar" to "offline:2"),
        )
        assertEquals(1, tracks.size)
        // The offline-restore contract: TrackSelectionPolicy resolves
        // "offline:{index}" against this id (both hosts).
        assertEquals("offline:2", tracks[0].id)
    }

    @Test
    fun demuxedSubtitle_withCollidingTitle_neverInheritsTheSidecarId() {
        val tracks = MpvTrackCatalog.mediaTracks(
            entries = listOf(
                MpvTrackCatalog.MpvTrackEntry(type = "sub", id = 1, title = "English", external = false),
                MpvTrackCatalog.MpvTrackEntry(type = "sub", id = 2, title = "English", external = true),
            ),
            sideLoadedSubtitleIds = mapOf("English" to "external:7"),
        )
        assertEquals("mpv_sub_1", tracks[0].id)
        assertEquals("external:7", tracks[1].id)
    }

    @Test
    fun externalSubtitle_withoutRegistryEntry_fallsBackToSyntheticId() {
        val tracks = MpvTrackCatalog.mediaTracks(
            entries = listOf(
                MpvTrackCatalog.MpvTrackEntry(type = "sub", id = 5, title = "Unknown sidecar", external = true),
            ),
            sideLoadedSubtitleIds = emptyMap(),
        )
        assertEquals("mpv_sub_5", tracks[0].id)
    }

    @Test
    fun videoAndUnmappedTypes_areDropped() {
        val tracks = MpvTrackCatalog.mediaTracks(
            entries = listOf(
                MpvTrackCatalog.MpvTrackEntry(type = "video", id = 0),
                MpvTrackCatalog.MpvTrackEntry(type = "audio", id = 1),
            ),
        )
        assertEquals(listOf(TrackType.AUDIO), tracks.map { it.type })
    }

    @Test
    fun blankEverything_fallsBackToFormatterUnknownLabel() {
        val tracks = MpvTrackCatalog.mediaTracks(
            entries = listOf(MpvTrackCatalog.MpvTrackEntry(type = "audio", id = 2)),
        )
        assertEquals("Unknown", tracks[0].label)
        assertNull(tracks[0].language)
        assertNull(tracks[0].streamIndex)
    }
}
