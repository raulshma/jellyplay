package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.feature.player.video.TrackOption
import com.raulshma.jellyplay.feature.player.video.TrackSelectionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The side-loaded id-resolution contract across BOTH mpv hosts: the catalog's
 * registry stamping ([MpvTrackCatalog.mediaTracks]) must produce [MediaTrack]s
 * whose ids feed `TrackSelectionPolicy`'s `offline:`/`external:` resolution
 * through the exact [TrackOption] mapping `TrackSelectionHelper` uses
 * (`TrackOption(t.index, t.label, t.language, t.isSelected, t.streamIndex,
 * t.badges, id = t.id)`). This is what the desktop engine gained when its
 * bare sub-add loop converged onto the shared plan + catalog — before that,
 * its synthetic `mpv_sub_{id}` ids made offline-restore selection
 * unresolvable there.
 */
class MpvTrackCatalogTrackSelectionContractTest {

    private val policy = TrackSelectionPolicy()

    /** The shared helper's MediaTrack → TrackOption projection, verbatim. */
    private fun toOption(track: MediaTrack) = TrackOption(
        track.index,
        track.label,
        track.language,
        track.isSelected,
        track.streamIndex,
        track.badges,
        id = track.id,
    )

    @Test
    fun offlineSubtitle_restoreResolvesThroughTheCatalogStamp() {
        val tracks = MpvTrackCatalog.mediaTracks(
            entries = listOf(
                MpvTrackCatalog.MpvTrackEntry(type = "audio", id = 1, title = "English", lang = "eng"),
                MpvTrackCatalog.MpvTrackEntry(type = "sub", id = 1, title = "English", lang = "eng"),
                MpvTrackCatalog.MpvTrackEntry(
                    type = "sub", id = 2, title = "My Sidecar", external = true,
                ),
            ),
            sideLoadedSubtitleIds = mapOf("My Sidecar" to "offline:2"),
        ).map(::toOption)

        assertEquals(2, policy.resolveByOfflineSubtitleId(tracks, index = 2)?.index)
    }

    @Test
    fun externalSubtitle_streamIndexContract_resolvesExactId() {
        val tracks = MpvTrackCatalog.mediaTracks(
            entries = listOf(
                MpvTrackCatalog.MpvTrackEntry(type = "sub", id = 3, title = "Foreign", external = true),
            ),
            sideLoadedSubtitleIds = mapOf("Foreign" to "external:11"),
        ).map(::toOption)

        val resolved = policy.resolveByStreamIndex(tracks, streamIndex = 11, targetStream = null)
        assertEquals(3, resolved?.index)
    }

    @Test
    fun desktopStyleSyntheticIds_stillResolveToNull_notWrongTracks() {
        // A side-loaded track the registry never saw (id-less legacy source):
        // the offline restore must miss (caller falls back to positional
        // matching) rather than match a wrong track.
        val tracks = MpvTrackCatalog.mediaTracks(
            entries = listOf(
                MpvTrackCatalog.MpvTrackEntry(type = "sub", id = 1, title = "Sidecar", external = true),
            ),
            sideLoadedSubtitleIds = emptyMap(),
        ).map(::toOption)

        assertNull(policy.resolveByOfflineSubtitleId(tracks, index = 2))
    }
}
