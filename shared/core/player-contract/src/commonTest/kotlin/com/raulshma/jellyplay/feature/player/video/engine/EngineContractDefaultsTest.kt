package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleCallbacks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins the invariants of the two contract-level seams that must stay
 * type-erased and dependency-free:
 *
 *  - [EngineDrmSessionManagerProvider] is a `fun interface` returning `Any?`:
 *    a clear-content item resolves to `null` (the ONLY way to say "no DRM"),
 *    a DRM-protected item resolves to an opaque, non-null object the Android
 *    engine casts. No DRM framework type may appear on this seam.
 *  - [PlayerLifecycleCallbacks] default-implements both activity callbacks,
 *    so an engine (or any holder) may implement the interface without
 *    overriding anything and receive no-op behaviour on both pause and resume.
 */
class EngineContractDefaultsTest {

    // ── EngineDrmSessionManagerProvider ──────────────────────────────────────

    @Test
    fun `a clear item resolves to null`() {
        val provider = EngineDrmSessionManagerProvider { null }
        assertNull(provider.provide())
    }

    @Test
    fun `a drm item resolves to the opaque manager`() {
        val manager = Any()
        val provider = EngineDrmSessionManagerProvider { manager }
        assertSame(manager, provider.provide())
    }

    @Test
    fun `each invocation is fresh — the provider is a factory, not a constant`() {
        var calls = 0
        val provider = EngineDrmSessionManagerProvider {
            calls++
            Any()
        }
        val first = provider.provide()
        val second = provider.provide()
        assertTrue(first !== second)
        assertEquals(2, calls)
    }

    // ── PlayerLifecycleCallbacks ─────────────────────────────────────────────

    @Test
    fun `a bare implementation no-ops both lifecycle callbacks`() {
        val callbacks = object : PlayerLifecycleCallbacks {}
        // Must not throw — default bodies are empty.
        callbacks.onActivityPause()
        callbacks.onActivityResume()
    }

    @Test
    fun `overriding the callbacks intercepts the calls`() {
        val seen = mutableListOf<String>()
        val callbacks = object : PlayerLifecycleCallbacks {
            override fun onActivityPause() {
                seen += "pause"
            }

            override fun onActivityResume() {
                seen += "resume"
            }
        }
        callbacks.onActivityResume()
        callbacks.onActivityPause()
        assertEquals(listOf("resume", "pause"), seen)
    }
}
