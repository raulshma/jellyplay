package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
import com.raulshma.jellyplay.feature.player.video.subtitle.TimedCue
import org.junit.Test
import org.junit.Assert.*

class EngineCapabilitiesDefaultTest {

    @Test
    fun defaultCapabilities_allDisabled() {
        val caps = EngineCapabilities()
        assertFalse(caps.supportsPip)
        assertFalse(caps.supportsMiniMode)
        assertFalse(caps.supportsCues)
        assertFalse(caps.supportsAudioDelay)
        assertFalse(caps.supportsSubtitleDelay)
        assertFalse(caps.supportsAudioPassthrough)
        assertFalse(caps.supportsSubtitleStyle)
        assertFalse(caps.supportsDialogueBoost)
        assertFalse(caps.supportsNightMode)
        assertFalse(caps.supportsAudioNormalization)
        assertFalse(caps.supportsChannelMixing)
    }

    @Test
    fun capabilities_dataClassCopy() {
        val caps = EngineCapabilities()
        val modified = caps.copy(supportsPip = true)
        assertFalse(caps.supportsPip)
        assertTrue(modified.supportsPip)
        assertFalse(modified.supportsMiniMode)
    }
}
