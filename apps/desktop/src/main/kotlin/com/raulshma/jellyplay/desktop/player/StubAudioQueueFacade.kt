package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.AudioQueueOutcome
import com.raulshma.jellyplay.core.data.playback.TrackWithAlbumFallback
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.PlaylistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * v1 desktop stub for [AudioQueueFacade] — music on desktop is browse-live,
 * playback-degrades (Wave wC). The real implementation is Android-only: it
 * rides the media3 `AudioPlaybackManager`, which reaches Koin on Android
 * through a Hilt interop single. This stub is the desktop-Koin binding ONLY
 * (one framework per type — no Android file changes); it exists so the music
 * ViewModels' `audioQueueFacade` ctor dep resolves and the whole
 * [com.raulshma.jellyplay.feature.music.navigation.musicSection] can go live.
 *
 * Every play/enqueue attempt returns [AudioQueueOutcome.Failed], never
 * [AudioQueueOutcome.Started]: `Started` would fake success — the mix
 * scroll-to-first-track side effects (`queue.first().id`) would fire and the
 * user would hear nothing — while `Failed` routes into the call sites'
 * existing error states (e.g. AlbumDetailViewModel maps the outcome into its
 * in-screen error), so the degradation is visible and honest. The two
 * outcomes that are NOT about playback capability keep their interface
 * contract: empty inputs still yield [AudioQueueOutcome.Empty] ("nothing to
 * play" is true regardless of engine), and a caller [guard] veto still yields
 * [AudioQueueOutcome.Suppressed] (silent by design — an error would be wrong
 * after navigation drift).
 *
 * Future-work pointer for the real desktop impl — it needs two things the
 * desktop stack lacks today:
 *  1. queue + auto-advance semantics: `MediaEngine` (MpvDesktopEngine) is a
 *     single-item load/transport contract with no queue concept;
 *  2. stream resolution: [com.raulshma.jellyplay.core.data.playback.AudioQueueItem]
 *     carries metadata only (id/name/artist/album/artwork — no stream URI),
 *     because Android's media3 manager resolves playback from the item id
 *     internally; a desktop queue would have to resolve a stream URL per item.
 */
internal class StubAudioQueueFacade : AudioQueueFacade {

    override suspend fun playTracks(
        tracks: List<MediaItem>,
        startIndex: Int,
        shuffled: Boolean,
        albumFallback: String?,
        imageMaxWidth: Int?,
    ): AudioQueueOutcome = if (tracks.isEmpty()) AudioQueueOutcome.Empty else playbackUnavailable()

    override suspend fun playTracks(
        pairs: List<TrackWithAlbumFallback>,
        startIndex: Int,
        shuffled: Boolean,
        imageMaxWidth: Int?,
    ): AudioQueueOutcome = if (pairs.isEmpty()) AudioQueueOutcome.Empty else playbackUnavailable()

    override suspend fun enqueueTracks(
        tracks: List<MediaItem>,
        albumFallback: String?,
        imageMaxWidth: Int?,
    ): AudioQueueOutcome = if (tracks.isEmpty()) AudioQueueOutcome.Empty else playbackUnavailable()

    override suspend fun enqueueTrack(
        track: MediaItem,
        albumFallback: String?,
        imageMaxWidth: Int?,
    ): AudioQueueOutcome = playbackUnavailable()

    override suspend fun startInstantMix(
        seedItemId: String,
        albumFallback: String?,
        guard: () -> Boolean,
    ): AudioQueueOutcome =
        // Same order as the real facade's veto: the guard runs on the main
        // thread before any outcome — a caller that navigated away stays
        // silent (Suppressed), everything else fails honestly.
        if (!withContext(Dispatchers.Main) { guard() }) {
            AudioQueueOutcome.Suppressed
        } else {
            playbackUnavailable()
        }

    override suspend fun playPlaylist(items: List<PlaylistItem>, startIndex: Int): AudioQueueOutcome =
        if (items.isEmpty()) AudioQueueOutcome.Empty else playbackUnavailable()

    override suspend fun enqueuePlaylistItem(item: PlaylistItem) {
        // Unit-returning shape has no outcome channel, so this degrades
        // silently — the one facade call desktop cannot surface a failure for.
        // Deliberately a no-op: there is no queue to append to.
    }

    private fun playbackUnavailable(): AudioQueueOutcome.Failed = AudioQueueOutcome.Failed(
        UnsupportedOperationException(
            "Audio playback is not available on desktop yet (music v1 is browse-only)",
        ),
    )
}
