package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [exoTargetBufferBytesFromMb] — the playback-slice MB pref → Media3
 * target-buffer-bytes conversion (the AUTO sentinel, the exact MiB math and
 * the Int-overflow saturation). Pure commonMain, so the fold's sentinel
 * semantics cannot drift from [LoadControlNumbers]' `-1` prose.
 */
class ExoBufferBytesTest {

    @Test
    fun autoSentinel_mapsToTheMedia3DeriveFromTracksValue() {
        // The store's 0 is AUTO — it must reach Media3 as -1, never a raw 0
        // (which would be an absolute zero-byte target: playback starves).
        assertEquals(EXO_TARGET_BUFFER_AUTO_BYTES, exoTargetBufferBytesFromMb(0))
        assertEquals(-1, exoTargetBufferBytesFromMb(0))
    }

    @Test
    fun negativeMb_degradesToAuto_notToAGarbageByteTarget() {
        // Negatives never persist (the store setter clamps), but a
        // hand-edited/stale blob must fold to the engine default, not to a
        // wrapped/negative byte target.
        assertEquals(-1, exoTargetBufferBytesFromMb(-1))
        assertEquals(-1, exoTargetBufferBytesFromMb(Int.MIN_VALUE))
    }

    @Test
    fun oneMb_isExactlyOneMebibyte() {
        assertEquals(1_048_576, exoTargetBufferBytesFromMb(1))
    }

    @Test
    fun rangeMaximum_isExact_notSaturated() {
        // 512 MiB — the declared pref ceiling — must be exact (536,870,912),
        // proving the conversion is not lossy inside the declared range.
        assertEquals(512 * 1_048_576, exoTargetBufferBytesFromMb(512))
        assertEquals(536_870_912, exoTargetBufferBytesFromMb(512))
    }

    @Test
    fun largestExactIntValue_stillExact() {
        // 2047 MiB = 2,146,435,072 — the largest MiB count that fits Int;
        // 2048 would overflow by exactly one.
        assertEquals(2_146_435_072, exoTargetBufferBytesFromMb(2047))
    }

    @Test
    fun overflowSaturatesAtIntMax_insteadOfWrappingNegative() {
        // 2048 MiB = 2^31 — one past Int.MAX_VALUE. A silent Int multiply
        // would wrap negative (Media3 would read it as an absolute byte
        // target); saturation is the documented repair.
        assertEquals(Int.MAX_VALUE, exoTargetBufferBytesFromMb(2048))
        assertEquals(Int.MAX_VALUE, exoTargetBufferBytesFromMb(Int.MAX_VALUE))
    }
}
