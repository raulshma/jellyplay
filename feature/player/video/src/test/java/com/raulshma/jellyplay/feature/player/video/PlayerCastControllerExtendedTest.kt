package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.cast.CastManager
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastSlice
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test

class PlayerCastControllerExtendedTest {

    private lateinit var castManager: CastManager
    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var adaptiveBitrateManager: AdaptiveBitrateManager
    private lateinit var syncPlayCastStore: SyncPlayCastStore
    private lateinit var castController: PlayerCastController
    private lateinit var engine: MediaEngine

    @Before
    fun setUp() {
        castManager = mockk(relaxed = true)
        playbackRepository = mockk(relaxed = true)
        adaptiveBitrateManager = mockk(relaxed = true)
        syncPlayCastStore = mockk(relaxed = true)
        engine = mockk(relaxed = true)

        every { syncPlayCastStore.syncPlayCast } returns MutableStateFlow(SyncPlayCastSlice())
        every { engine.isPlaying } returns MutableStateFlow(false)

        castController = PlayerCastController(
            castManager = castManager,
            playbackRepository = playbackRepository,
            adaptiveBitrateManager = adaptiveBitrateManager,
            syncPlayCastStore = syncPlayCastStore,
            getEngine = { engine },
            getCurrentPlaybackMode = { PlaybackMode.AUTO },
            getSessionState = { PlayerSessionState() },
        )
    }

    @Test
    fun castPlay_delegatesToCastManager() {
        castController.castPlay()
        verify { castManager.play() }
    }

    @Test
    fun castPause_delegatesToCastManager() {
        castController.castPause()
        verify { castManager.pause() }
    }

    @Test
    fun castSeekTo_delegatesToCastManager() {
        castController.castSeekTo(12_000L)
        verify { castManager.seekTo(12_000L) }
    }

    @Test
    fun setCastVolume_delegatesToCastManager() {
        castController.setCastVolume(0.8f)
        verify { castManager.setVolume(0.8f) }
    }

    @Test
    fun onCastDisconnected_resumesEnginePlayWhenNotPlaying() {
        castController.onCastDisconnected()
        verify { engine.play() }
    }

    @Test
    fun updateCastStrategyForEngine_setsActiveStrategyOnCastManager() {
        castController.updateCastStrategyForEngine(engine)
        verify { castManager.setActiveStrategy(CastManager.STRATEGY_GOOGLE) }
    }
}

