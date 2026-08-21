package com.raulshma.jellyplay.feature.player.video.engine

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [selectedTextTrackIsAss] — the observed-selection signal that flips the
 * engine between the native SubtitleView and the libass AssSubtitleView.
 *
 * Regression: on a transcode the side-loaded ASS track is auto-selected by
 * DefaultTrackSelector (preferredTextLanguage match) without engine.selectTrack
 * ever running, so the flag must derive from the observed Tracks state —
 * otherwise the track plays while both renderers stay hidden.
 *
 * Tracks.Group infers its type from the TrackGroup's first format mime
 * (text/x-ssa and application/x-subrip → TRACK_TYPE_TEXT, audio mimes →
 * AUDIO), mirroring how real side-loaded SubtitleConfigurations surface.
 */
class SelectedTextTrackIsAssTest {

    @Test
    fun selectedSsaTrack_isAss() {
        val tracks = tracks(
            group(selected = true, mime = MimeTypes.TEXT_SSA),
        )
        assertTrue(selectedTextTrackIsAss(tracks))
    }

    @Test
    fun selectedSourceParsedTrackWithSsaCodecs_isAss() {
        // The real Jellyfin HLS transcode case: Media3 parses the side-loaded
        // ASS at the source level, so the selected track surfaces with
        // sampleMimeType = application/x-media3-cues and the ORIGINAL ASS mime
        // moved into `codecs` (observed in logcat:
        // Format(1:external:2, …, application/x-media3-cues, text/x-ssa, …)).
        val tracks = tracks(
            group(selected = true, mime = MimeTypes.APPLICATION_MEDIA3_CUES, codecs = MimeTypes.TEXT_SSA),
        )
        assertTrue(selectedTextTrackIsAss(tracks))
    }

    @Test
    fun unselectedSsaTrack_isNotAss() {
        // The side-load exists but nothing is selected yet: must NOT claim
        // libass owns rendering (the native view stays shown).
        val tracks = tracks(
            group(selected = false, mime = MimeTypes.TEXT_SSA),
        )
        assertFalse(selectedTextTrackIsAss(tracks))
    }

    @Test
    fun selectedNonSsaTextTrack_isNotAss() {
        val tracks = tracks(
            group(selected = true, mime = MimeTypes.APPLICATION_SUBRIP),
        )
        assertFalse(selectedTextTrackIsAss(tracks))
    }

    @Test
    fun selectionAmongMultipleTextGroups_detected() {
        // Two side-loads: the SRT is selected, the ASS is not.
        val srtSelected = tracks(
            group(selected = true, mime = MimeTypes.APPLICATION_SUBRIP),
            group(selected = false, mime = MimeTypes.TEXT_SSA),
        )
        assertFalse(selectedTextTrackIsAss(srtSelected))

        // Selection flips to the ASS — the auto-switch case on a transcode.
        val assSelected = tracks(
            group(selected = false, mime = MimeTypes.APPLICATION_SUBRIP),
            group(selected = true, mime = MimeTypes.TEXT_SSA),
        )
        assertTrue(selectedTextTrackIsAss(assSelected))
    }

    @Test
    fun audioSelectionWithUnselectedSsa_isNotAss() {
        // Audio-only selection (subtitle Off): an unselected SSA side-load
        // must not keep the libass overlay visible.
        val tracks = tracks(
            group(selected = true, mime = MimeTypes.AUDIO_AAC),
            group(selected = false, mime = MimeTypes.TEXT_SSA),
        )
        assertFalse(selectedTextTrackIsAss(tracks))
    }

    private fun tracks(vararg groups: Tracks.Group) = Tracks(groups.toList())

    private fun group(
        selected: Boolean,
        mime: String,
        codecs: String? = null,
    ): Tracks.Group = Tracks.Group(
        TrackGroup(
            Format.Builder()
                .setId("external:2")
                .setSampleMimeType(mime)
                .setCodecs(codecs)
                .setLanguage("en")
                .build(),
        ),
        /* adaptiveSupported = */ false,
        IntArray(1) { C.FORMAT_HANDLED },
        booleanArrayOf(selected),
    )
}
