package com.raulshma.jellyplay.core.datastore.videoplayer

import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineSlice
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single read seam for surfaces that span the whole player preference space —
 * the video player, its session manager, track selection, and the settings
 * projector that mirrors player-relevant prefs into UI state. Those consumers
 * touch fields across seven stores (playback, video-player, audio,
 * audio-effects, subtitle, engine, security), so rather than inject seven
 * stores and combine them at each call site they collect this one aggregate.
 *
 * The aggregate holds the seven slices verbatim — it does not project them
 * into a flattened shape. Callers destructure the slices they need
 * (`aggregate.videoPlayer.videoDefaultSpeed`). A flattened projection would
 * duplicate the per-screen shapes already owned by `PreferenceProjections`;
 * the player stack's reads are ad-hoc enough that holding the slices is
 * simpler and lower-cardinality than inventing another projection type.
 */
@Singleton
class VideoPlayerAggregateStore @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val playbackStore: PlaybackStore,
    private val videoPlayerStore: VideoPlayerStore,
    private val audioStore: AudioStore,
    private val audioEffectsStore: AudioEffectsStore,
    private val subtitleStore: SubtitleLanguageStore,
    private val engineStore: PlayerEngineStore,
    private val securityStore: SecurityStore,
) {
    val aggregate: StateFlow<VideoPlayerAggregate> = combine(
        combine(
            playbackStore.playback,
            videoPlayerStore.videoPlayer,
            audioStore.audio,
            audioEffectsStore.audioEffects,
            subtitleStore.subtitle,
            ::VideoPlayerAggregateGroup1,
        ),
        combine(engineStore.playerEngine, securityStore.security) { engine, security ->
            VideoPlayerAggregateGroup2(engine, security)
        },
    ) { g1, g2 ->
        VideoPlayerAggregate(
            playback = g1.playback,
            videoPlayer = g1.videoPlayer,
            audio = g1.audio,
            audioEffects = g1.audioEffects,
            subtitle = g1.subtitle,
            engine = g2.engine,
            security = g2.security,
        )
    }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), VideoPlayerAggregate())

    private data class VideoPlayerAggregateGroup1(
        val playback: PlaybackSlice,
        val videoPlayer: VideoPlayerSlice,
        val audio: AudioSlice,
        val audioEffects: AudioEffectsSlice,
        val subtitle: SubtitleSlice,
    )

    private data class VideoPlayerAggregateGroup2(
        val engine: PlayerEngineSlice,
        val security: SecuritySlice,
    )
}

/**
 * Snapshot of the seven preference slices a video-player surface reads.
 * Defaults are the slice defaults so the aggregate has a sensible cold-start
 * value. Plain data class (Compose-free).
 */
data class VideoPlayerAggregate(
    val playback: PlaybackSlice = PlaybackSlice(),
    val videoPlayer: VideoPlayerSlice = VideoPlayerSlice(),
    val audio: AudioSlice = AudioSlice(),
    val audioEffects: AudioEffectsSlice = AudioEffectsSlice(),
    val subtitle: SubtitleSlice = SubtitleSlice(),
    val engine: PlayerEngineSlice = PlayerEngineSlice(),
    val security: SecuritySlice = SecuritySlice(),
)
