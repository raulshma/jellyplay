package com.raulshma.jellyplay.feature.player.video.session

import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.feature.player.video.CinemaIntroUiState
import com.raulshma.jellyplay.feature.player.video.PlayerStateHandles
import com.raulshma.jellyplay.feature.player.video.StillWatchingGate
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_direct_play_fallback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.getString

/**
 * The playback session's ui-mirror seams as ONE named holder — the derived
 * reads/writes over the [PlayerStateHandles] bundle + the live cached
 * aggregate that used to sit as ten loose lambdas in the session's body
 * (the coherent noun cluster with no name: streaming quality, playback-mode
 * mirror, incognito gate, pending-stream seed, cinema-intro state, playhead
 * seed, pass-out hours, the overlay upgrade, the fallback notice, the
 * playback-mode read). Pure data + one construction site inside the session
 * — NOT a behavior seam (no interface, no second implementation; the
 * ADR-0012 bar): the session consumes the holder's members exactly where it
 * consumed the bare lambdas.
 *
 * The two members that reach outside (handles + aggregate) arrive as
 * constructor inputs — the track helper's pending-streams forward and the
 * injectable FORCE_DIRECT_PLAY fallback notice (tests capture the raw text).
 * The aggregate read is a GETTER, not the value: the holder is constructed
 * before the session's `cachedAggregate` property initializes, and the
 * mirrors must see every later aggregate refresh. The lambdas run only from
 * session/engine call chains — long after every collaborator initializes —
 * so the forward reads stay safe (the same discipline the inline lambdas
 * had).
 */
internal class SessionUiMirrors(
    handlesBundle: PlayerStateHandles,
    aggregate: () -> VideoPlayerAggregate,
    /** The track helper's pending-stream seed, forwarded from the session. */
    setPendingStreamsForward: (MediaStreamSelection?) -> Unit,
    /** Injectable FORCE_DIRECT_PLAY fallback notice; production resolves the resource. */
    directPlayFallbackNotice: (suspend (String) -> String)?,
) {
    // Constructor params are not in accessor scope — unpack once, here.
    private val handles = handlesBundle

    private val uiState get() = handles.uiState
    private val positionMs get() = handles.positionMs

    /** Current in-memory streaming quality (the ui-prefs mirror). */
    val getStreamingQuality = { uiState.value.uiPrefs.streamingQuality }

    /** Writes the in-memory playback-mode mirror. */
    val setUiPlaybackMode = { mode: PlaybackMode ->
        uiState.update { it.copy(uiPrefs = it.uiPrefs.copy(playbackMode = mode)) }
    }

    /** Incognito gate for the session-owned stop/start reports. */
    val getIncognitoModeEnabled = { aggregate().videoPlayer.incognitoModeEnabled }

    /** Feeds the track-selection helper's pending stream selection before a stream-change reload. */
    val setPendingStreams = setPendingStreamsForward

    /** Synchronous playback-mode read feeding the coordinator's fallback latch policy. */
    val getPlaybackMode = { uiState.value.uiPrefs.playbackMode }

    /** Localized FORCE_DIRECT_PLAY fallback notice for the session's InformUser events. */
    val resolveDirectPlayFallbackNotice = directPlayFallbackNotice
        ?: { errorText: String -> getString(Res.string.player_direct_play_fallback, errorText) }

    /** Pass-out protection hours; values <= 0 disable the poller. */
    val passOutHours: Flow<Int> =
        uiState.flow.map { it.uiPrefs.passOutProtectionHours }.distinctUntilChanged()

    /**
     * Whether the still-watching mode includes HOURS (feature 1.3): a tripped
     * pass-out pause then arrives as the still-watching prompt (the confirm
     * overlay) instead of the bare pass-out toast.
     */
    val upgradesPassOutToOverlay = {
        StillWatchingGate.upgradesPassOutToOverlay(aggregate().videoPlayer.stillWatchingMode)
    }

    /**
     * The uiState `cinemaIntroState` write seam: every write the cinema
     * sequencing needs flows through this setter — the same seam shows
     * (loadCinemaIntro) and clears (advanceCinemaIntro) the intro.
     */
    val setCinemaIntroState = { state: CinemaIntroUiState? ->
        uiState.update { it.copy(cinemaIntroState = state) }
    }

    /**
     * Writes the high-frequency position display flow — the seam behind
     * preSeedPlayhead.
     */
    val seedDisplayedPositionMs = { seed: Long ->
        positionMs.value = seed
    }
}
