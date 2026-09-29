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

    // ─── trackEntry (the ONE raw-row normalization both mpv hosts run) ────────

    @Test
    fun trackEntry_parsesThePlainMapRow() {
        // The desktop MpvLib.readNode shape verbatim: String/Boolean/Long values.
        val entry = MpvTrackCatalog.trackEntry(
            mapOf(
                "type" to "sub",
                "id" to 3L,
                "title" to "My Sidecar",
                "lang" to "eng",
                "codec" to "subrip",
                "selected" to true,
                "external" to true,
                "ff-index" to 4L,
                "forced" to true,
                "default" to true,
                "hearing-impaired" to true,
            ),
        )
        assertEquals(
            MpvTrackCatalog.MpvTrackEntry(
                type = "sub", id = 3, title = "My Sidecar", lang = "eng", codec = "subrip",
                selected = true, external = true, ffIndex = 4,
                forced = true, default = true, hearingImpaired = true,
            ),
            entry,
        )
    }

    @Test
    fun trackEntry_absentBooleansDefaultToFalse_numbersCoerceThroughNumber() {
        val entry = MpvTrackCatalog.trackEntry(
            mapOf(
                "type" to "audio",
                "id" to 7, // Int form (an adapter that narrowed the node value)
            ),
        )
        assertEquals(7, entry?.id)
        assertNull(entry?.ffIndex)
        assertEquals(false, entry?.selected)
        assertEquals(false, entry?.external)
        assertEquals(false, entry?.forced)
        assertEquals(false, entry?.default)
        assertEquals(false, entry?.hearingImpaired)
    }

    @Test
    fun trackEntry_stringIdFallback_stillParses() {
        // The defensive fallback both former per-engine parsers carried.
        val entry = MpvTrackCatalog.trackEntry(mapOf("type" to "sub", "id" to "5"))
        assertEquals(5, entry?.id)
        val ff = MpvTrackCatalog.trackEntry(mapOf("type" to "sub", "id" to 1L, "ff-index" to "9"))
        assertEquals(9, ff?.ffIndex)
    }

    @Test
    fun trackEntry_nonMapRows_andRowsWithoutTypeOrId_areDropped() {
        assertNull(MpvTrackCatalog.trackEntry(null))
        assertNull(MpvTrackCatalog.trackEntry("not a map"))
        assertNull(MpvTrackCatalog.trackEntry(mapOf<String, Any?>()))
        assertNull(MpvTrackCatalog.trackEntry(mapOf("id" to 1L)), "missing type")
        assertNull(MpvTrackCatalog.trackEntry(mapOf("type" to "sub")), "missing id")
        assertNull(MpvTrackCatalog.trackEntry(mapOf("type" to "sub", "id" to "not-a-number")), "unparsable id")
    }

    // ─── existingSubtitleLabels (the raw-title dedupe key) ────────────────────

    @Test
    fun existingSubtitleLabels_returnsRawSubTitlesOnly() {
        val labels = MpvTrackCatalog.existingSubtitleLabels(
            listOf(
                MpvTrackCatalog.MpvTrackEntry(type = "audio", id = 1, title = "English 5.1"),
                MpvTrackCatalog.MpvTrackEntry(type = "sub", id = 2, title = "Full English"),
                MpvTrackCatalog.MpvTrackEntry(type = "sub", id = 3, title = null),
                MpvTrackCatalog.MpvTrackEntry(type = "video", id = 0, title = "Video"),
            ),
        )
        assertEquals(setOf("Full English"), labels)
    }

    @Test
    fun existingSubtitleLabels_emptyListYieldsEmptySet() {
        assertTrue(MpvTrackCatalog.existingSubtitleLabels(emptyList()).isEmpty())
    }
}
