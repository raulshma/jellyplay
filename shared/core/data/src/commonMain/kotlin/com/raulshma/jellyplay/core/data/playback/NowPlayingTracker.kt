package com.raulshma.jellyplay.core.data.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Deep module for the now-playing metadata: the sole writer of the six
 * `MutableStateFlow`s (item id, title, artist, artist id, album, album art
 * url) the player UI renders. Pre-extraction [AudioPlaybackManager] inlined
 * this write sequence in four places — detail-fetch success, queue-only
 * local fallback, track transition, crossfade transition — plus the stop
 * reset, and each site hand-picked which fields to write, leaving the
 * divergences implicit (transitions never refresh the artist id because
 * [AudioQueueItem] carries none; the local fallback also leaves the album
 * art url; the reset clears five fields but not the artist id). Here each
 * publish method IS the divergence, pinned by its KDoc. The flows are
 * read-only to everyone else; the manager re-exposes them by reference so
 * consumers are unchanged.
 */
class NowPlayingTracker {
    private val _currentPlayingItemId = MutableStateFlow<String?>(null)
    val currentPlayingItemId: StateFlow<String?> = _currentPlayingItemId.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _artist = MutableStateFlow("")
    val artist: StateFlow<String> = _artist.asStateFlow()

    private val _artistId = MutableStateFlow<String?>(null)
    val artistId: StateFlow<String?> = _artistId.asStateFlow()

    private val _album = MutableStateFlow("")
    val album: StateFlow<String> = _album.asStateFlow()

    private val _albumArtUrl = MutableStateFlow("")
    val albumArtUrl: StateFlow<String> = _albumArtUrl.asStateFlow()

    /**
     * Detail-fetch success path (the `play()` round-trip): the ONE publisher
     * that writes all six fields — the only source that knows the artist id
     * and the server image url.
     */
    fun publishDetail(
        itemId: String,
        title: String,
        artist: String,
        artistId: String?,
        album: String,
        albumArtUrl: String,
    ) {
        _currentPlayingItemId.value = itemId
        _title.value = title
        _artist.value = artist
        _artistId.value = artistId
        _album.value = album
        _albumArtUrl.value = albumArtUrl
    }

    /**
     * Track-transition path (auto-advance and crossfade hand-off). Writes
     * five fields from the queue item and deliberately leaves [artistId]
     * untouched: [AudioQueueItem] carries no artist id, so the previous
     * track's survives until the next detail fetch refreshes it. Recorded
     * divergence, not an accident — the pre-extraction transitions behaved
     * identically. Null album / image url coalesce to "" exactly as the
     * inlined sequences did (`item.album ?: ""`, `item.imageUrl ?: ""`).
     */
    fun publishQueueItem(item: AudioQueueItem) {
        _currentPlayingItemId.value = item.id
        _title.value = item.name
        _artist.value = item.artist
        _album.value = item.album ?: ""
        _albumArtUrl.value = item.imageUrl ?: ""
    }

    /**
     * Queue-only local fallback (server detail fetch failed but a completed
     * download exists on disk). Writes four fields and deliberately leaves
     * [artistId] AND [albumArtUrl] untouched: the local source resolves
     * neither, so the previous track's values survive until the next detail
     * fetch. Recorded divergence, not an accident.
     */
    fun publishLocalFile(
        itemId: String,
        title: String,
        artist: String,
        album: String,
    ) {
        _currentPlayingItemId.value = itemId
        _title.value = title
        _artist.value = artist
        _album.value = album
    }

    /**
     * Stop/release reset: returns the five display fields to their initial
     * values. Deliberately does NOT reset [artistId] — recorded divergence:
     * the pre-extraction `stopAndRelease()` reset never cleared it either.
     */
    fun clear() {
        _currentPlayingItemId.value = null
        _title.value = ""
        _artist.value = ""
        _album.value = ""
        _albumArtUrl.value = ""
    }
}
