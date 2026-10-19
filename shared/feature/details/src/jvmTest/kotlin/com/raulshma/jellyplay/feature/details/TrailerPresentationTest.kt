package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the merged-trailer vocabulary and the pre-roll policies:
 * the merge order (pref on/off), the backdrop autoplay pick, the
 * per-entry playback dispatch (local session vs YouTube embed vs the
 * embed-failure external fallback), the trailer-then-feature gate table
 * (pref × media type × fresh start × SyncPlay × local availability) and
 * the ⋮ menu's "Play (with pre-rolls)" gate (SERIES/offline suppression).
 */
class TrailerPresentationTest {

    private fun local(id: String) = MediaItem(id = id, name = "Local $id", mediaType = MediaType.MOVIE)
    private fun remote(key: String?, name: String = "Trailer $key", url: String? = null) =
        SeerrRelatedVideo(key = key, name = name, site = "YouTube", type = "Trailer", url = url)

    // ── mergeTrailerEntries: the merge order ──────────────────────────

    @Test
    fun merge_prefOff_isRemoteFirst_localsAppended() {
        val merged = mergeTrailerEntries(
            localTrailers = listOf(local("l1"), local("l2")),
            remoteVideos = listOf(remote("r1"), remote("r2")),
            preferLocalTrailers = false,
        )

        assertEquals(
            listOf("r1", "r2", "l1", "l2"),
            merged.map {
                when (it) {
                    is DetailUiState.TrailerEntry.Local -> it.mediaItem.id
                    is DetailUiState.TrailerEntry.Remote -> it.video.key
                }
            },
        )
    }

    @Test
    fun merge_prefOn_isLocalFirst_remotesAppended() {
        val merged = mergeTrailerEntries(
            localTrailers = listOf(local("l1"), local("l2")),
            remoteVideos = listOf(remote("r1"), remote("r2")),
            preferLocalTrailers = true,
        )

        assertEquals(
            listOf("l1", "l2", "r1", "r2"),
            merged.map {
                when (it) {
                    is DetailUiState.TrailerEntry.Local -> it.mediaItem.id
                    is DetailUiState.TrailerEntry.Remote -> it.video.key
                }
            },
        )
    }

    @Test
    fun merge_emptySources_stayEmpty() {
        assertTrue(mergeTrailerEntries(emptyList(), emptyList(), preferLocalTrailers = true).isEmpty())
    }

    @Test
    fun localTrailerItems_extractsOnlyTheLocalLeg_inOrder() {
        val merged = mergeTrailerEntries(
            localTrailers = listOf(local("l1"), local("l2")),
            remoteVideos = listOf(remote("r1")),
            preferLocalTrailers = true,
        )

        assertEquals(listOf("l1", "l2"), merged.localTrailerItems().map { it.id })
        // The remote-only shape (remote-only content) extracts nothing.
        assertTrue(mergeTrailerEntries(emptyList(), listOf(remote("r1")), false).localTrailerItems().isEmpty())
    }

    // ── pickBackdropTrailerEntry: the backdrop autoplay pick ─────────────

    @Test
    fun backdropPick_prefOn_picksTheLocalClipFirst() {
        val trailers = mergeTrailerEntries(
            localTrailers = listOf(local("l1")),
            remoteVideos = listOf(remote("r1")),
            preferLocalTrailers = true,
        )

        assertIs<DetailUiState.TrailerEntry.Local>(pickBackdropTrailerEntry(trailers, preferLocalTrailers = true))
    }

    @Test
    fun backdropPick_prefOffOrNoLocal_keepsTheYouTubeTrailerRule() {
        val trailers = mergeTrailerEntries(
            localTrailers = listOf(local("l1")),
            remoteVideos = listOf(
                remote("clip", name = "Clip", url = null).copy(type = "clip"),
                remote("trailer"),
            ),
            preferLocalTrailers = false,
        )

        // Pref off: the local entry never wins even though it exists.
        val pick = assertIs<DetailUiState.TrailerEntry.Remote>(
            pickBackdropTrailerEntry(trailers, preferLocalTrailers = false),
        )
        assertEquals("trailer", pick.video.key)

        // Pref on but no local entry → same remote rule.
        val remoteOnly = mergeTrailerEntries(emptyList(), listOf(remote("trailer")), true)
        assertEquals(
            "trailer",
            assertIs<DetailUiState.TrailerEntry.Remote>(
                pickBackdropTrailerEntry(remoteOnly, preferLocalTrailers = true),
            ).video.key,
        )
    }

    @Test
    fun backdropPick_nothingQualified_isNull() {
        // Non-YouTube remotes were never mounted; a local-only list under the
        // embed-only mount picks nothing either.
        val nonYouTube = listOf(DetailUiState.TrailerEntry.Remote(SeerrRelatedVideo(key = "k", site = "Vimeo")))
        assertNull(pickBackdropTrailerEntry(nonYouTube, preferLocalTrailers = false))
        assertNull(pickBackdropTrailerEntry(emptyList(), preferLocalTrailers = true))
    }

    // ── resolveTrailerEntryDispatch: local vs remote vs embed fallback ────

    @Test
    fun dispatch_localEntry_playsAFullPlayerSession() {
        val dispatch = resolveTrailerEntryDispatch(DetailUiState.TrailerEntry.Local(local("l1")))

        val session = assertIs<TrailerEntryDispatch.PlayerSession>(dispatch)
        assertEquals("l1", session.mediaItem.id)
    }

    @Test
    fun dispatch_remoteEntry_opensTheYouTubeEmbed() {
        val dispatch = resolveTrailerEntryDispatch(DetailUiState.TrailerEntry.Remote(remote("abc123")))

        val embed = assertIs<TrailerEntryDispatch.YouTubeEmbed>(dispatch)
        assertEquals("abc123", embed.videoKey)
        assertEquals("https://www.youtube.com/watch?v=abc123", embed.externalUrl)
    }

    @Test
    fun dispatch_remoteEntry_embedFailureFallsBackToTheExternalUrl() {
        // The video's own URL wins; a keyless entry still builds a watch URL;
        // with neither, the fallback degrades to YouTube's root (the dialog's
        // onEmbedFailed handler opens whatever the policy resolved).
        assertEquals(
            "https://youtu.be/xyz",
            assertIs<TrailerEntryDispatch.YouTubeEmbed>(
                resolveTrailerEntryDispatch(DetailUiState.TrailerEntry.Remote(remote(null, url = "https://youtu.be/xyz"))),
            ).externalUrl,
        )
        assertEquals(
            "https://www.youtube.com/watch?v=keyless",
            assertIs<TrailerEntryDispatch.YouTubeEmbed>(
                resolveTrailerEntryDispatch(DetailUiState.TrailerEntry.Remote(remote("keyless"))),
            ).externalUrl,
        )
    }

    // ── resolveTrailerPreroll: the trailer-then-feature gate table ────────

    @Test
    fun preroll_happyPath_returnsTheFirstLocalTrailer() {
        val trailers = mergeTrailerEntries(
            localTrailers = listOf(local("l1"), local("l2")),
            remoteVideos = listOf(remote("r1")),
            preferLocalTrailers = true,
        )

        val preroll = resolveTrailerPreroll(
            mediaType = MediaType.MOVIE,
            trailers = trailers,
            playTrailerBeforeMovie = true,
            startPositionTicks = 0L,
            isSyncPlayActive = false,
        )
        assertEquals("l1", preroll?.mediaItem?.id)
    }

    @Test
    fun preroll_vetoes_prefOff_seriesResumeSyncPlayAndRemoteOnly() {
        val localFirst = mergeTrailerEntries(listOf(local("l1")), listOf(remote("r1")), true)
        val remoteOnly = mergeTrailerEntries(emptyList(), listOf(remote("r1")), true)

        // The global toggle is off (the default).
        assertNull(
            resolveTrailerPreroll(
                mediaType = MediaType.MOVIE,
                trailers = localFirst,
                playTrailerBeforeMovie = false,
                startPositionTicks = 0L,
                isSyncPlayActive = false,
            ),
        )
        // SERIES suppression — intros/pre-rolls are not a series concept.
        assertNull(
            resolveTrailerPreroll(
                mediaType = MediaType.SERIES,
                trailers = localFirst,
                playTrailerBeforeMovie = true,
                startPositionTicks = 0L,
                isSyncPlayActive = false,
            ),
        )
        // A resume is never preceded by a trailer (the cinema fresh-start rule).
        assertNull(
            resolveTrailerPreroll(
                mediaType = MediaType.MOVIE,
                trailers = localFirst,
                playTrailerBeforeMovie = true,
                startPositionTicks = 1L,
                isSyncPlayActive = false,
            ),
        )
        // SyncPlay suppression — the group's server queue owns the order.
        assertNull(
            resolveTrailerPreroll(
                mediaType = MediaType.MOVIE,
                trailers = localFirst,
                playTrailerBeforeMovie = true,
                startPositionTicks = 0L,
                isSyncPlayActive = true,
            ),
        )
        // A remote (YouTube) trailer cannot ride the player session — a
        // remote-only item degrades to the plain feature start.
        assertNull(
            resolveTrailerPreroll(
                mediaType = MediaType.MOVIE,
                trailers = remoteOnly,
                playTrailerBeforeMovie = true,
                startPositionTicks = 0L,
                isSyncPlayActive = false,
            ),
        )
        // Nothing resolved at all.
        assertNull(
            resolveTrailerPreroll(
                mediaType = MediaType.MOVIE,
                trailers = emptyList(),
                playTrailerBeforeMovie = true,
                startPositionTicks = 0L,
                isSyncPlayActive = false,
            ),
        )
    }

    // ── offersPlayWithPrerolls: the ⋮ menu gate (step 1) ─────────────

    @Test
    fun menuGate_movieAndEpisodeOffer_offlineAndSyncPlaySuppress() {
        assertTrue(offersPlayWithPrerolls(MediaType.MOVIE, isOffline = false, canPlay = true))
        assertTrue(offersPlayWithPrerolls(MediaType.EPISODE, isOffline = false, canPlay = true))

        // SERIES suppression — intros are episode-scoped.
        assertFalse(offersPlayWithPrerolls(MediaType.SERIES, isOffline = false, canPlay = true))
        // Server read — never offline.
        assertFalse(offersPlayWithPrerolls(MediaType.MOVIE, isOffline = true, canPlay = true))
        // The host-level SyncPlay veto rides the caller-resolved canPlay.
        assertFalse(offersPlayWithPrerolls(MediaType.MOVIE, isOffline = false, canPlay = false))
        // No media type resolved — nothing to attach the play to.
        assertFalse(offersPlayWithPrerolls(null, isOffline = false, canPlay = true))
    }
}
