package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.model.MediaItem

/**
 * A track paired with the album name to fall back to when the track's own
 * `album` is null. Travels as one type instead of a raw `Pair` so the
 * multi-album batch path ([AudioQueueFacade.playTracks] pairs overload) can't
 * mix up the two halves — the fallback is per-track there because each track
 * belongs to a different album.
 */
data class TrackWithAlbumFallback(
    val track: MediaItem,
    val albumFallback: String?,
)

/**
 * Outcome of a facade play/enqueue operation. The "one constant" callers map
 * to their own localized message — `core/data` carries no string resources by
 * design, so `Empty` / `Failed(cause)` are the typed concept each feature
 * resolves once at the edge.
 */
sealed interface AudioQueueOutcome {
    /**
     * Playback (or enqueue) happened. Exposes the built queue so callers can
     * keep side effects like the mix scroll-to-first-track event
     * (`queue.first().id`). `startIndex` is the index playback starts at for
     * play operations; `-1` for pure enqueues (nothing starts playing).
     */
    data class Started(val queue: List<AudioQueueItem>, val startIndex: Int) : AudioQueueOutcome

    /** Nothing to play: empty input list, or instant mix came back empty. */
    data object Empty : AudioQueueOutcome

    /** Caller's guard vetoed the start (navigation drift). Silent by design. */
    data object Suppressed : AudioQueueOutcome

    /** Mix fetch or lookup failed. Callers map to their own message. */
    data class Failed(val cause: Throwable) : AudioQueueOutcome
}

/**
 * Normalizes the facade outcome to the [InstantMixStateHolder]'s pure outcome
 * shape — the one fold the album, artist, and media-detail mix starts used to
 * carry as three private copies. [AudioQueueOutcome.Started] keeps the queue
 * head for the one-shot navigation (null on an empty queue).
 */
fun AudioQueueOutcome.toInstantMixOutcome(): InstantMixOutcome = when (this) {
    is AudioQueueOutcome.Started -> InstantMixOutcome.Started(queue.firstOrNull()?.id)
    AudioQueueOutcome.Empty -> InstantMixOutcome.EmptyMix
    AudioQueueOutcome.Suppressed -> InstantMixOutcome.Suppressed
    is AudioQueueOutcome.Failed -> InstantMixOutcome.Failed(cause)
}
