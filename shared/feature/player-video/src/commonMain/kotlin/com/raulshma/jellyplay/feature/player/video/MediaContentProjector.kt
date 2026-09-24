package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.feature.player.video.state.MediaContentState

/**
 * Owns the [MediaContentState] slice's writes — the single writer seam the
 * `EpisodeNavigator.updateEpisodes` shape established. Every site that used
 * to hand-copy `it.copy(media = it.media.copy(…))` inside the ViewModel now
 * routes through one command here: the session-state mirror, the detail
 * apply, the refreshed-detail (subtitle download/upload) path, the resolved
 * stream URL and the companion-lyrics writes.
 *
 * It is also the fold target of the session-state collector's residue
 * (the title/subtitle mirror, the stored-selection seed and the
 * item/series-change choreography) — see [onSessionState].
 *
 * Compose-free and ViewModel-free: the slice write flows through the
 * [updateMedia] seam and the VM-bound collaborators stay behind constructor
 * lambdas. [onDetailRefreshed] deliberately owns the refresh ORDER —
 * detail apply → session-manager re-sync → source/stream write →
 * post-stream fan-outs — because that order was previously only implicit in
 * the VM body (see applyMediaDetailAndSourceState's comment): the session
 * manager must re-sync FIRST so mode/quality/stream-index reloads rebuild
 * the side-loaded subtitle set from the refreshed detail, and so the session
 * collector re-publishes the refreshed streams instead of reverting the
 * slice write back to the stale ones; the track rebuild runs LAST so the
 * picker reads the already-refreshed streams. Pinned by
 * [MediaContentProjectorTest]. The cross-controller fan-outs themselves
 * (aspect detection, PiP aspect, track rebuild + the armed subtitle
 * selection, the lyrics repo fetch) stay VM-side, passed in as lambdas.
 *
 * The session-fold seams below follow the [SubtitleStyleController]
 * narrow-mirror pattern (named per concern, never a generic state
 * transformer): they fire from [onSessionState] only, with the exact
 * cadence/order the VM's inline collector had.
 */
internal class MediaContentProjector(
    /** The media-slice write seam (the VM's `_uiState` mirror update). */
    private val updateMedia: ((MediaContentState) -> MediaContentState) -> Unit,
    /** Full detail apply (slice write + chapters + navigator + lyrics fetch). */
    private val applyDetail: (MediaDetail) -> Unit,
    /** Session-manager re-sync with a refreshed detail ([PlayerSessionManager.applyRefreshedDetail]). */
    private val applyRefreshedDetail: (detail: MediaDetail, attachToEngine: Boolean) -> Unit,
    /** Resolves the playing version's source for a refreshed detail ([PlayerSessionManager.matchedMediaSource]). */
    private val matchMediaSource: (MediaDetail) -> MediaSource?,
    /** Post-stream fan-outs: aspect + PiP mirrors, track rebuild, armed subtitle selection. */
    private val onStreamsRefreshed: (streams: List<MediaStream>, newSubtitleStreamIndex: Int?) -> Unit,
    /** Top-level title/subtitle mirror (the VM's residual uiState write) — EVERY emission, unguarded. */
    private val setTitleSubtitle: (title: String, subtitle: String) -> Unit,
    /** Seeds the track slice's stored-selection flags every emission ([TrackSelectionHelper.onStoredSelectionChanged]). */
    private val onStoredSelectionChanged: (MediaStreamSelection?) -> Unit,
    /** Derives the stored per-item stream selection from the cached aggregate (the SettingsProjector getItemId read pattern). */
    private val getStoredSelection: (itemId: String?) -> MediaStreamSelection?,
    /** Re-resolves the per-item/series playback preferences on an item/series change ([TrackSelectionHelper.refreshPlaybackPreferences]). */
    private val refreshPlaybackPreferences: () -> Unit,
    /** Re-resolves the session's render override for a new item ([RenderControls.onSessionItemChanged]). */
    private val onSessionItemChanged: suspend (itemId: String?, seriesId: String?) -> Unit,
    /** Launches the render poke fire-and-forget on the VM scope — never awaited, exactly like the inline collector's `launch`. */
    private val launchAsync: (block: suspend () -> Unit) -> Unit,
) {

    /** The collector-fold residue: the last item/series ids seen by [onSessionState] (VM lifetime, never reset per item). */
    private var lastItemId: String? = null
    private var lastSeriesId: String? = null

    /** Mirrors the resolved stream URL once the load spine resolves it. */
    fun onStreamUrl(url: String) {
        updateMedia { it.copy(streamUrl = url) }
    }

    /**
     * Folds ONE session-state emission — the whole body the VM's collector
     * used to run inline, cadence- and order-identical (pinned by
     * [MediaContentProjectorTest]):
     *
     *  1. title/subtitle mirror — EVERY emission, unguarded (they are
     *     top-level uiState fields, so they ride [setTitleSubtitle], not
     *     the media slice);
     *  2. the media-slice mirror below;
     *  3. [onStoredSelectionChanged] — EVERY emission, seeded from
     *     [getStoredSelection] (the per-item override flags live in the
     *     track slice);
     *  4. on an item/series CHANGE only ([lastItemId]/[lastSeriesId] fold
     *     state, VM lifetime — starts null so the first emission fires):
     *     [refreshPlaybackPreferences] FIRST, then the [onSessionItemChanged]
     *     render poke launched fire-and-forget through [launchAsync] (never
     *     awaited — the row fetch + re-resolution + config rebuild are the
     *     controller's choreography).
     *
     * [seriesId] is the caller-derived `mediaDetail?.item?.seriesId` —
     * passed in because the VM's collector derives it for the call.
     */
    fun onSessionState(session: PlayerSessionState, seriesId: String?) {
        val itemId = session.currentItemId
        setTitleSubtitle(session.title, session.subtitle)
        updateMedia { it.copy(
            currentMediaSource = session.currentMediaSource,
            mediaStreams = session.mediaStreams,
            playMethod = session.playMethodString,
            transcodeReasons = session.transcodeReasons,
            isDirectPlayForced = session.isDirectPlayForced,
            // Mirror the session's series id so the per-series
            // "remember subtitle/audio" toggle row renders for episode
            // playback (footer is gated on seriesId != null). The detail
            // apply also sets this on the initial load, but this fires on
            // every session transition (e.g. next-episode autoplay) and
            // must keep it in sync even when the detail refresh lags or is
            // skipped.
            seriesId = seriesId,
        ) }
        onStoredSelectionChanged(getStoredSelection(itemId))
        // Re-resolve per-item/series language preference when the current
        // item or series changes. The cached value feeds the track slice
        // and the series-pref toggles.
        if (itemId != lastItemId || seriesId != lastSeriesId) {
            lastItemId = itemId
            lastSeriesId = seriesId
            refreshPlaybackPreferences()
            launchAsync { onSessionItemChanged(itemId, seriesId) }
        }
    }

    /**
     * Applies a detail's media fields (overview, people, artwork, series id).
     * [artworkUrl] is resolved by the caller (the image-url provider is a VM
     * dependency); chapters and the episode-navigator/lyrics fan-outs stay
     * with [applyDetail].
     */
    fun onDetail(detail: MediaDetail, artworkUrl: String) {
        updateMedia { it.copy(
            overview = detail.item.overview ?: "",
            people = detail.people,
            artworkUrl = artworkUrl,
            seriesId = detail.item.seriesId,
        ) }
    }

    /**
     * The refreshed-detail choreography (subtitle download/upload added a new
     * stream): detail apply → session-manager re-sync → source/stream write →
     * fan-outs, in that order — see the class KDoc for why the session
     * manager goes first and the track rebuild last.
     */
    fun onDetailRefreshed(refresh: MediaDetailRefresh) {
        applyDetail(refresh.detail)
        applyRefreshedDetail(refresh.detail, refresh.attachToEngine)
        // Match the session's source id so multi-version items publish the
        // playing version's streams; offline falls back to the detail's first.
        val source = matchMediaSource(refresh.detail)
        val streams = source?.mediaStreams ?: emptyList()
        updateMedia { it.copy(
            currentMediaSource = source,
            mediaStreams = streams,
        ) }
        onStreamsRefreshed(streams, refresh.newSubtitleStreamIndex)
    }

    /** Writes the companion-lyrics lines (empty clears them — non-audio items). */
    fun onLyrics(lines: List<LyricsLine>) {
        updateMedia { it.copy(lyricsLines = lines) }
    }
}
