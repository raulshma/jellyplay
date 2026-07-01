package com.raulshma.jellyplay.feature.player.video.engine

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the [MediaEngine] role-interface split (recommendation #3): a concrete
 * engine must be assignable to every role so consumers can depend on the
 * narrow type. Uses [NoOpEngine] because it is JVM-instantiable; the structural
 * composition applies identically to ExoPlayer/MPV/LibVLC (they all declare
 * `: MediaEngine`).
 */
class MediaEngineRoleInterfacesTest {

    private val engine: MediaEngine = NoOpEngine()

    @Test
    fun mediaEngine_composesAllRoles() {
        assertTrue(engine is PlaybackLifecycle)
        assertTrue(engine is PlaybackControl)
        assertTrue(engine is PlaybackState)
        assertTrue(engine is EngineConfigurable)
        assertTrue(engine is TrackControl)
        assertTrue(engine is SubtitleStyling)
        assertTrue(engine is VideoSurfaceBinding)
    }

    @Test
    fun trackControlRole_exposesTrackSurface() {
        val trackControl: TrackControl = engine
        assertTrue(trackControl.availableTracks.value.isEmpty())
    }

    @Test
    fun subtitleStylingRole_exposesCuesAndStyling() {
        val styling: SubtitleStyling = engine
        assertTrue(styling.currentCues.value.isEmpty())
    }

    @Test
    fun engineConfigurableRole_exposesCapabilitiesAndConfig() {
        val configurable: EngineConfigurable = engine
        assertTrue(configurable.capabilities === EngineCapabilityMatrix.EXTERNAL)
        configurable.updateConfig(EngineConfig())
    }

    @Test
    fun videoSurfaceBindingRole_exposesSurfaceCreation() {
        val binding: VideoSurfaceBinding = engine
        // setAspectRatio must be callable through the narrow role.
        binding.setAspectRatio(mode = 0)
    }
}
