package com.raulshma.jellyplay.core.ui.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Decoder returns a packed IntArray; w*h is asserted via size (dims are inputs). */
class BlurHashDecoderTest {

    @Test
    fun `decode returns null for too short hash`() {
        val result = BlurHashDecoder.decode("abc", 4, 4)
        assertNull(result)
    }

    @Test
    fun `decode returns pixels for valid hash`() {
        val result = BlurHashDecoder.decode("LEHV6nWB2yk8pyo0adR*.7kCMdnj", 4, 4)
        assertNotNull(result)
        assertEquals(16, result!!.size)
    }

    @Test
    fun `decode returns pixels for different dimensions`() {
        val result = BlurHashDecoder.decode("LEHV6nWB2yk8pyo0adR*.7kCMdnj", 8, 8)
        assertNotNull(result)
        assertEquals(64, result!!.size)
    }

    @Test
    fun `decode returns pixels for single component hash`() {
        val result = BlurHashDecoder.decode("00FF00", 4, 4)
        assertNotNull(result)
    }

    @Test
    fun `decode returns non-null pixels for another valid hash`() {
        val result = BlurHashDecoder.decode("LKO2?U%2Tw=w]~RBVZRi};RPxuwH", 4, 3)
        assertNotNull(result)
        assertEquals(12, result!!.size)
    }

    @Test
    fun `decoded pixels are opaque ARGB`() {
        val result = BlurHashDecoder.decode("LEHV6nWB2yk8pyo0adR*.7kCMdnj", 4, 4)
        assertNotNull(result)
        assertTrue(result!!.all { (it ushr 24) == 0xFF }, "all pixels must be fully opaque")
    }


    @Test
    fun `decode returns null for hash shorter than expected`() {
        val result = BlurHashDecoder.decode("L", 4, 4)
        assertNull(result)
    }

    @Test
    fun `decode with 1x1 dimensions produces 1 pixel`() {
        val result = BlurHashDecoder.decode("LEHV6nWB2yk8pyo0adR*.7kCMdnj", 1, 1)
        assertNotNull(result)
        assertEquals(1, result!!.size)
    }

}
