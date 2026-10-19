package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.playback.VideoQueueItem
import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType

/**
 * Pure play-path decision policies for the media-detail screen, extracted
 * from the hand-built [DetailContentCallbacks] adapter in `MediaDetailScreen`:
 * the play/chapter lambdas keep only dispatch, and neither re-encodes the
 * subtitle-selection or confirmation rules these folds own.
 */
internal object DetailPlayPolicies {

    /**
     * Which subtitle index a play dispatch should carry: for a local origin
     * ([DetailOrigin.isLocal]) the server stream index is meaningless, so the
     * chosen local-manifest index rides along instead — the player's offline
     * wiring (`TrackSelectionPolicy.resolveByOfflineSubtitleId`) resolves the
     * right side-loaded subtitle from it. A remote (or still-unknown) origin
     * keeps the server subtitle index. The audio index is not policy — it is
     * threaded untouched (the local audio inventory is not selectable here).
     */
    fun resolvePlayStreamSelection(
        origin: DetailOrigin?,
        localSubtitleIndex: Int?,
        remoteSubtitleIndex: Int?,
    ): Int? = if (origin?.isLocal == true) localSubtitleIndex else remoteSubtitleIndex

    /**
     * Folds a play dispatch's whole stream tail in one place so the screen's
     * play and chapter lambdas read the ViewModel-selected indexes and hand
     * the result to the player route without re-encoding the origin rule
     * (see [resolvePlayStreamSelection]). The audio index is not policy —
     * threaded untouched.
     *
     * [forceCinemaMode] (D14 "Play (with pre-rolls)"): the per-invocation
     * override that plays the item with server intros regardless of the
     * global cinema-mode pref. The screen effects it by arming
     * [com.raulshma.jellyplay.core.data.playback.CinemaModeOverride] before
     * dispatching; the flag here is the policy record (the pipeline still
     * applies its remaining vetoes — fresh start, external player, SyncPlay,
     * item type).
     */
    fun resolveDetailPlayDispatch(
        origin: DetailOrigin?,
        localSubtitleIndex: Int?,
        remoteSubtitleIndex: Int?,
        audioStreamIndex: Int?,
        forceCinemaMode: Boolean = false,
    ): DetailPlayDispatch = DetailPlayDispatch(
        subtitleStreamIndex = resolvePlayStreamSelection(origin, localSubtitleIndex, remoteSubtitleIndex),
        audioStreamIndex = audioStreamIndex,
        forceCinemaMode = forceCinemaMode,
    )

    /**
     * A series mark-played recurses into every episode and clears every resume
     * position, so it requires confirmation first; single movies/episodes flip
     * immediately (trivially reversible via the same button). Season actions
     * set [isSeasonAction] — a season mark is always series-scoped, so it
     * confirms regardless of the parent item's resolved type.
     */
    fun requiresMarkPlayedConfirmation(
        mediaType: MediaType?,
        isSeasonAction: Boolean = false,
    ): Boolean = isSeasonAction || mediaType == MediaType.SERIES

    /**
     * Dispatches exactly one of [confirm] or [action] per the
     * [requiresMarkPlayedConfirmation] table (mediaType × season action):
     * SERIES → confirm (series dialog); MOVIE / EPISODE / SEASON /
     * unresolved → direct; any season action → confirm (season dialog) —
     * which makes the season direct branch unreachable, exactly as the
     * screen's original per-callback bodies behaved. Played vs unplayed is
     * NOT a gate input: the two directions differ only in the dialog verb /
     * message, which the caller tracks alongside.
     */
    fun dispatchMarkPlayedAction(
        mediaType: MediaType?,
        isSeasonAction: Boolean,
        confirm: () -> Unit,
        action: () -> Unit,
    ) {
        if (requiresMarkPlayedConfirmation(mediaType, isSeasonAction)) confirm() else action()
    }
}

/**
 * The stream tail of a detail play dispatch: which subtitle index to carry
 * (origin-resolved) plus the pass-through audio index — and, since D14, the
 * per-invocation cinema-mode override record
 * ([DetailPlayPolicies.resolveDetailPlayDispatch]).
 */
internal data class DetailPlayDispatch(
    val subtitleStreamIndex: Int?,
    val audioStreamIndex: Int?,
    val forceCinemaMode: Boolean = false,
)

/**
 * D3: where one [DetailUiState.TrailerEntry] dispatches:
 *
 *  - [PlayerSession] — a local (server-hosted) trailer plays through the
 *    full player session (the playbackRepository path; the libmpv engine on
 *    desktop), from the start;
 *  - [YouTubeEmbed] — a remote video opens the inline YouTube dialog keyed
 *    on [YouTubeEmbed.videoKey]; the dialog's embed-failure fallback opens
 *    [YouTubeEmbed.externalUrl] externally (the video's own URL when the
 *    key is missing — an embed could never load).
 */
internal sealed interface TrailerEntryDispatch {
    data class PlayerSession(val mediaItem: MediaItem) : TrailerEntryDispatch
    data class YouTubeEmbed(
        val video: com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo,
        val videoKey: String,
        val externalUrl: String,
    ) : TrailerEntryDispatch
}

internal fun resolveTrailerEntryDispatch(
    entry: DetailUiState.TrailerEntry,
): TrailerEntryDispatch = when (entry) {
    is DetailUiState.TrailerEntry.Local -> TrailerEntryDispatch.PlayerSession(entry.mediaItem)
    is DetailUiState.TrailerEntry.Remote -> {
        val key = entry.video.key
        TrailerEntryDispatch.YouTubeEmbed(
            video = entry.video,
            videoKey = key.orEmpty(),
            externalUrl = entry.video.url
                ?: key?.let { "https://www.youtube.com/watch?v=$it" }
                ?: "https://www.youtube.com",
        )
    }
}

/**
 * D14 trailer-then-feature: the local trailer to sequence BEFORE the feature
 * for the item about to play, or null for a plain feature start. All gates:
 *
 *  - the global `playTrailerBeforeMovie` toggle (default off),
 *  - MOVIE plays only (the feature name; intros/pre-rolls are not a series
 *    concept),
 *  - fresh starts only (`startPositionTicks == 0` — the same fresh-start rule
 *    the cinema gate applies; a resume is never preceded by a trailer),
 *  - never during SyncPlay (the group's server-side queue owns the order —
 *    the same suppression the manual queue's advance carries),
 *  - a LOCAL trailer must exist: a remote (YouTube) entry cannot ride the
 *    player session at all, so remote-only items degrade to a plain feature
 *    start (silent, today's behavior).
 *
 * Pure — the composition seeds the queue and starts the trailer session off
 * the returned entry (the documented two-session fallback: the manual queue
 * drains AFTER the current item, so seeding the trailer at the head would
 * play it after the feature; seeding the FEATURE at the head and starting
 * the trailer session produces the named trailer → feature order).
 */
internal fun resolveTrailerPreroll(
    mediaType: MediaType?,
    trailers: List<DetailUiState.TrailerEntry>,
    playTrailerBeforeMovie: Boolean,
    startPositionTicks: Long,
    isSyncPlayActive: Boolean,
): DetailUiState.TrailerEntry.Local? {
    if (!playTrailerBeforeMovie) return null
    if (mediaType != MediaType.MOVIE) return null
    if (startPositionTicks != 0L) return null
    if (isSyncPlayActive) return null
    return trailers.filterIsInstance<DetailUiState.TrailerEntry.Local>().firstOrNull()
}

/**
 * D14: the play click's ORDERED EFFECTS, decided once — the pure composition
 * of [DetailPlayPolicies.resolveDetailPlayDispatch] (the stream tail) and
 * [resolveTrailerPreroll] (the trailer-then-feature gate) that
 * `MediaDetailScreen`'s `onPlayClick` lambda used to hand-sequence inline
 * with zero JVM surface. The lambda now executes the returned effects in
 * order; every decision lives here and is pinned by [DetailPlaySequencerTest].
 *
 * The sequenced order (the documented two-session fallback): the manual
 * queue drains AFTER the current item, so seeding the trailer first would
 * play it after the feature — instead the FEATURE is seeded at the queue
 * head ([DetailPlayEffect.EnqueueNext]) and the session STARTS on the
 * trailer ([DetailPlayEffect.PlaySession] at position 0, no stream tail);
 * the queue-first advance plays the feature when the trailer ends. The
 * stream selection ([DetailPlayDispatch]) applies only to the direct-play
 * arm — a prerolled trailer session carries none, and the seeded feature's
 * selections ride the queue advance, not this dispatch.
 */
internal object DetailPlaySequencer {

    /** One play click's effects, in execution order. */
    sealed interface DetailPlayEffect {
        /** Seed the manual up-next queue's head — the feature, per the two-session fallback. */
        data class EnqueueNext(val item: VideoQueueItem) : DetailPlayEffect

        /** Start a player session: the trailer (preroll arm) or the requested item (direct arm). */
        data class PlaySession(
            val itemId: String,
            val mediaSourceId: String?,
            val startPositionTicks: Long,
            val subtitleStreamIndex: Int?,
            val audioStreamIndex: Int?,
        ) : DetailPlayEffect
    }

    /**
     * Everything one play click decides from: the request (item/source/start —
     * the [com.raulshma.jellyplay.feature.details.PlaybackCallbacks.onPlayClick]
     * arguments), the loaded item the preroll gate and the queue seed read,
     * the stream-selection inputs, and the two dispatch-time reads the former
     * lambda pulled from process-wide seams (the player-prefs toggle and the
     * SyncPlay state).
     */
    data class DetailPlayContext(
        val requestedItemId: String,
        val requestedMediaSourceId: String?,
        val requestedStartPositionTicks: Long,
        /** The loaded detail item — null while the detail is unresolved (direct arm, always). */
        val currentItem: MediaItem?,
        val origin: DetailOrigin?,
        val localSubtitleIndex: Int?,
        val remoteSubtitleIndex: Int?,
        val audioStreamIndex: Int?,
        val trailers: List<DetailUiState.TrailerEntry>,
        /** The queued feature card's poster (resolved by the caller's URL provider). */
        val currentItemPosterUrl: String,
        val playTrailerBeforeMovie: Boolean,
        val isSyncPlayActive: Boolean,
    )

    /**
     * Folds [context] into the ordered effects. The preroll arm fires only
     * when [resolveTrailerPreroll] found a LOCAL trailer AND the request
     * targets the loaded item itself (a play of some OTHER item — an extra,
     * a version-picked source re-dispatch — is never preceded by the current
     * item's trailer); every other request takes the direct arm with the
     * resolved stream tail.
     */
    fun sequence(context: DetailPlayContext): List<DetailPlayEffect> {
        val dispatch = DetailPlayPolicies.resolveDetailPlayDispatch(
            origin = context.origin,
            localSubtitleIndex = context.localSubtitleIndex,
            remoteSubtitleIndex = context.remoteSubtitleIndex,
            audioStreamIndex = context.audioStreamIndex,
        )
        val preroll = resolveTrailerPreroll(
            mediaType = context.currentItem?.mediaType,
            trailers = context.trailers,
            playTrailerBeforeMovie = context.playTrailerBeforeMovie,
            startPositionTicks = context.requestedStartPositionTicks,
            isSyncPlayActive = context.isSyncPlayActive,
        )
        return if (preroll != null && context.requestedItemId == context.currentItem?.id) {
            listOf(
                DetailPlayEffect.EnqueueNext(
                    VideoQueueItem(
                        itemId = context.currentItem.id,
                        title = context.currentItem.name ?: "",
                        subtitle = context.currentItem.year?.toString(),
                        imageUrl = context.currentItemPosterUrl,
                        runTimeTicks = context.currentItem.runTimeTicks,
                    ),
                ),
                DetailPlayEffect.PlaySession(
                    itemId = preroll.mediaItem.id,
                    mediaSourceId = null,
                    startPositionTicks = 0L,
                    subtitleStreamIndex = null,
                    audioStreamIndex = null,
                ),
            )
        } else {
            listOf(
                DetailPlayEffect.PlaySession(
                    itemId = context.requestedItemId,
                    mediaSourceId = context.requestedMediaSourceId,
                    startPositionTicks = context.requestedStartPositionTicks,
                    subtitleStreamIndex = dispatch.subtitleStreamIndex,
                    audioStreamIndex = dispatch.audioStreamIndex,
                ),
            )
        }
    }
}
