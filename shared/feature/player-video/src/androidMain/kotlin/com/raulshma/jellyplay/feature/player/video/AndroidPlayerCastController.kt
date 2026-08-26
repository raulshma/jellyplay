package com.raulshma.jellyplay.feature.player.video

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.raulshma.jellyplay.core.data.cast.CastManager
import com.raulshma.jellyplay.core.data.cast.CastMediaOptions
import com.raulshma.jellyplay.core.data.cast.CastSessionEvent
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.TrackLabelFormatter
import com.raulshma.jellyplay.feature.player.video.engine.TrackLabelInfo
import com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleMimeMapper
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.MimeTypes
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the cast-to-device workflow: handing the current item off to a remote
 * receiver (Google Cast / DLNA / "Play On"), transporting the active track +
 * quality selections + subtitle URLs into the cast session, and surfacing the
 * cast playback state (position / duration / playing / volume) + connection
 * flow to the screen.
 *
 * Extracted from [VideoPlayerViewModel], continuing the collaborator pattern
 * established by [SubtitleManager] / [SleepTimerController]. Delegates all
 * transport to [CastManager]; this class owns only the *handoff glue* that
 * previously lived inline in the VM:
 *  - [castToDevice]: builds the cast [MediaItem] (URL, artwork, subtitle
 *    configs) from the engine's current position + the session's media source,
 *    carries the active track + bitrate selections, hands it to [CastManager],
 *    then pauses the local engine.
 *  - [buildCastOptions] / [buildCastSubtitleConfigurations]: pure builders.
 *  - the one-line transport delegators ([castPlay] / [castPause] /
 *    [castSeekTo] / [setCastVolume] / [disconnect]) and the disconnect resume
 *    ([onCastDisconnected]).
 *  - the 9 cast-state pass-through flows + `isBackgroundCasting` /
 *    `backgroundCastingEnabled` (the background-cast transport controls live in
 *    the VM because they own the system [MediaSession]).
 *
 * NOT owned here: [VideoPlayerViewModel.detachForBackgroundCast] /
 * [reattachFromBackgroundCast] — those rebuild the system [MediaSession] around
 * the cast / local player through the media-session controller; they stay in
 * the VM until media-session ownership is itself extracted.
 *
 * (Wave 8C: renamed from `PlayerCastController` — the commonMain
 * [PlayerCastController] seam interface took the old name; this class is its
 * Android actual. `disconnect(context)` and `castSessionEvents` carry
 * Android/legacy types and stay class-local — the screen reaches them
 * through the androidMain `androidCast` ViewModel extension.)
 *
 * Engine + session + uiState access is via lambdas so this class reads the
 * *current* engine (the VM swaps engines on retry) without a hard ViewModel
 * reference.
 */
internal class AndroidPlayerCastController(
    private val castManager: CastManager,
    private val playbackRepository: PlaybackRepository,
    private val adaptiveBitrateManager: AdaptiveBitrateManager,
    private val syncPlayCastStore: com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore,
    private val getEngine: () -> MediaEngine?,
    private val getCurrentPlaybackMode: () -> PlaybackMode,
    private val getSessionState: () -> PlayerSessionState,
) : PlayerCastController {

    // ----- Cast state pass-through (screen reads these directly) -----

    override     val isCastAvailable: Boolean get() = castManager.isCastAvailable
    override     val isCastConnected: Boolean get() = castManager.isConnected
    override     val castPositionMs: StateFlow<Long> get() = castManager.castPositionMs
    override     val castDurationMs: StateFlow<Long> get() = castManager.castDurationMs
    override     val castIsPlaying: StateFlow<Boolean> get() = castManager.castIsPlaying
    override     val castVolumeFlow: StateFlow<Float> get() = castManager.castVolume
    override     val isConnectedFlow: StateFlow<Boolean> get() = castManager.isConnectedFlow
    override     val isConnectingFlow: StateFlow<Boolean> get() = castManager.isConnectingFlow
    val castSessionEvents: SharedFlow<CastSessionEvent> get() = castManager.sessionEvents
    override     val isBackgroundCasting: Boolean get() = castManager.isBackgroundCasting
    override val backgroundCastingEnabled: Boolean
        get() = syncPlayCastStore.syncPlayCast.value.backgroundCastingEnabled

    // ----- Transport delegators -----

    override     fun castPlay() = castManager.play()
    override     fun castPause() = castManager.pause()
    override     fun castSeekTo(positionMs: Long) = castManager.seekTo(positionMs)
    override     fun setCastVolume(volume: Float) = castManager.setVolume(volume)
    fun disconnect(context: android.content.Context) = castManager.disconnect(context)

    /**
     * Resume local playback when a cast session disconnects mid-item — only if
     * the local engine is currently paused (the handoff paused it in
     * [castToDevice]). No-op if the engine is already playing or gone.
     */
    override     fun onCastDisconnected() {
        val engine = getEngine() ?: return
        if (!engine.isPlaying.value) engine.play()
    }

    /**
     * Pick the cast strategy when an engine binds. DLNA is sticky (a user who
     * chose DLNA keeps it across engine swaps); otherwise default to Google
     * Cast for the freshly-bound engine.
     */
    override     fun updateCastStrategyForEngine(engine: MediaEngine) {
        if (castManager.currentStrategyName != CastManager.STRATEGY_DLNA) {
            castManager.setActiveStrategy(CastManager.STRATEGY_GOOGLE)
        }
    }

    // ----- Handoff -----

    /**
     * Hand the current item off to the active cast receiver: resolve a stream
     * URL at the engine's current position, build artwork + subtitle configs,
     * carry the active track + bitrate selections, load it on [CastManager],
     * then pause the local engine. No-op if no engine / no item / blank URL.
     */
    @OptIn(UnstableApi::class)
    override     fun castToDevice() {
        val engine = getEngine() ?: return
        val sessionState = getSessionState()
        val currentItemId = sessionState.currentItemId ?: return

        val positionMs = engine.currentPositionMs
        val startTimeTicks = positionMs * 10_000
        val sourceId = sessionState.currentMediaSource?.id ?: ""
        val url = playbackRepository.getStreamUrl(currentItemId, sourceId, startTimeTicks)
        if (url.isBlank()) return

        val artworkUri = try {
            Uri.parse(playbackRepository.getImageUrl(currentItemId, maxWidth = 300))
        } catch (_: Exception) { null }

        val subtitleConfigs = buildCastSubtitleConfigurations(
            itemId = currentItemId,
            mediaSourceId = sourceId,
            mediaStreams = sessionState.mediaStreams,
        )

        val mediaItem = MediaItem.Builder()
            .setMediaId(currentItemId)
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(sessionState.title)
                    .setSubtitle(sessionState.subtitle)
                    .setArtworkUri(artworkUri)
                    .build()
            )
            .setSubtitleConfigurations(subtitleConfigs)
            .build()
        // Carry the active track + quality selections into the cast session so
        // the handoff does not silently drop audio/subtitle/quality.
        castManager.loadMedia(mediaItem, positionMs, object : Player.Listener {}, buildCastOptions(sourceId))
        engine.pause()
    }

    /**
     * Builds the cast playback intent from the engine's currently-selected
     * tracks and the active streaming-quality preference. Track indices come
     * straight from the engine's `availableTracks` (`isSelected`); the bitrate
     * ceiling mirrors the local `setMaxVideoBitrate` computation so the cast
     * session respects the same cap (no cap when forcing direct play or when
     * the quality is `AUTO`).
     */
    private fun buildCastOptions(mediaSourceId: String): CastMediaOptions {
        val tracks = getEngine()?.availableTracks?.value.orEmpty()
        val audioIndex = tracks.firstOrNull { it.isSelected && it.type == TrackType.AUDIO }?.index
        val subtitleIndex = tracks.firstOrNull { it.isSelected && it.type == TrackType.SUBTITLE }?.index
        val maxBitrate = if (getCurrentPlaybackMode() == PlaybackMode.FORCE_DIRECT_PLAY) {
            null
        } else {
            adaptiveBitrateManager.resolveEffectiveMaxBitrate()?.toInt()
        }
        return CastMediaOptions(
            mediaSourceId = mediaSourceId.takeIf { it.isNotBlank() },
            audioStreamIndex = audioIndex,
            subtitleStreamIndex = subtitleIndex,
            maxVideoBitrate = maxBitrate,
        )
    }

    private fun buildCastSubtitleConfigurations(
        itemId: String,
        mediaSourceId: String,
        mediaStreams: List<MediaStream>,
    ): List<MediaItem.SubtitleConfiguration> {
        return mediaStreams
            .filter { it.type == StreamType.SUBTITLE }
            .mapNotNull { stream ->
                val subUrl = when {
                    !stream.deliveryUrl.isNullOrBlank() ->
                        playbackRepository.getSubtitleDeliveryUrl(stream.deliveryUrl!!)
                    stream.isExternal ->
                        playbackRepository.buildSubtitleDeliveryUrl(
                            itemId, mediaSourceId, stream.index, "vtt",
                        )
                    else -> null
                }
                if (subUrl.isNullOrBlank()) return@mapNotNull null

                // Cast defaults unknown codecs to VTT — the most broadly supported.
                val mimeType = SubtitleMimeMapper.mapCodecToMime(stream.codec) ?: MimeTypes.TEXT_VTT

                MediaItem.SubtitleConfiguration.Builder(Uri.parse(subUrl))
                    .setMimeType(mimeType)
                    .setLabel(
                        stream.displayTitle?.takeIf { it.isNotBlank() }
                            ?: TrackLabelFormatter.primary(
                                TrackLabelInfo(
                                    title = stream.title,
                                    language = stream.language,
                                    codec = stream.codec,
                                    isForced = stream.isForced,
                                    isDefault = stream.isDefault,
                                )
                            )
                    )
                    .setLanguage(stream.language)
                    .build()
            }
    }
}
