package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class EngineCapabilitiesTest {

    @Test
    fun defaultCapabilities_allFalse() {
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
    }

    @Test
    fun allCapabilitiesEnabled() {
        val caps = EngineCapabilities(
            supportsPip = true,
            supportsMiniMode = true,
            supportsCues = true,
            supportsAudioDelay = true,
            supportsSubtitleDelay = true,
            supportsAudioPassthrough = true,
            supportsSubtitleStyle = true,
            supportsDialogueBoost = true,
            supportsNightMode = true,
        )
        assertTrue(caps.supportsPip)
        assertTrue(caps.supportsMiniMode)
        assertTrue(caps.supportsCues)
        assertTrue(caps.supportsAudioDelay)
        assertTrue(caps.supportsSubtitleDelay)
        assertTrue(caps.supportsAudioPassthrough)
        assertTrue(caps.supportsSubtitleStyle)
        assertTrue(caps.supportsDialogueBoost)
        assertTrue(caps.supportsNightMode)
    }

    @Test
    fun partialCapabilities() {
        val caps = EngineCapabilities(
            supportsAudioDelay = true,
            supportsSubtitleStyle = true,
        )
        assertTrue(caps.supportsAudioDelay)
        assertFalse(caps.supportsAudioPassthrough)
        assertTrue(caps.supportsSubtitleStyle)
        assertFalse(caps.supportsDialogueBoost)
    }

    @Test
    fun dataClass_equality() {
        val caps1 = EngineCapabilities(supportsAudioDelay = true, supportsCues = true)
        val caps2 = EngineCapabilities(supportsAudioDelay = true, supportsCues = true)
        val caps3 = EngineCapabilities(supportsAudioDelay = true, supportsCues = false)

        assertTrue(caps1 == caps2)
        assertFalse(caps1 == caps3)
    }

    @Test
    fun dataClass_copy() {
        val caps = EngineCapabilities()
        val modified = caps.copy(supportsAudioDelay = true)

        assertFalse(caps.supportsAudioDelay)
        assertTrue(modified.supportsAudioDelay)
    }
}
