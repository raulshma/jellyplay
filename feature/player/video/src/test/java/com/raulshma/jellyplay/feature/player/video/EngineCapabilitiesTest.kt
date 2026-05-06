package com.raulshma.jellyplay.feature.player.video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineCapabilitiesTest {

    @Test
    fun defaultCapabilities_allFalse() {
        val caps = EngineCapabilities()
        assertFalse(caps.audioDelay)
        assertFalse(caps.audioPassthrough)
        assertFalse(caps.subtitleStyle)
        assertFalse(caps.dialogueBoost)
        assertFalse(caps.nightMode)
        assertFalse(caps.ocr)
        assertFalse(caps.cues)
    }

    @Test
    fun allCapabilitiesEnabled() {
        val caps = EngineCapabilities(
            audioDelay = true,
            audioPassthrough = true,
            subtitleStyle = true,
            dialogueBoost = true,
            nightMode = true,
            ocr = true,
            cues = true,
        )
        assertTrue(caps.audioDelay)
        assertTrue(caps.audioPassthrough)
        assertTrue(caps.subtitleStyle)
        assertTrue(caps.dialogueBoost)
        assertTrue(caps.nightMode)
        assertTrue(caps.ocr)
        assertTrue(caps.cues)
    }

    @Test
    fun partialCapabilities() {
        val caps = EngineCapabilities(
            audioDelay = true,
            subtitleStyle = true,
        )
        assertTrue(caps.audioDelay)
        assertFalse(caps.audioPassthrough)
        assertTrue(caps.subtitleStyle)
        assertFalse(caps.dialogueBoost)
    }

    @Test
    fun dataClass_equality() {
        val caps1 = EngineCapabilities(audioDelay = true, ocr = true)
        val caps2 = EngineCapabilities(audioDelay = true, ocr = true)
        val caps3 = EngineCapabilities(audioDelay = true, ocr = false)

        assertTrue(caps1 == caps2)
        assertFalse(caps1 == caps3)
    }

    @Test
    fun dataClass_copy() {
        val caps = EngineCapabilities()
        val modified = caps.copy(audioDelay = true)

        assertFalse(caps.audioDelay)
        assertTrue(modified.audioDelay)
    }
}
