package com.raulshma.jellyplay.core.ui.image

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BlurHashDecoderTest {

    @Test
    fun `decode returns null for too short hash`() {
        val result = BlurHashDecoder.decode("abc", 4, 4)
        assertNull(result)
    }

    @Test
    fun `decode returns bitmap for valid hash`() {
        val result = BlurHashDecoder.decode("LEHV6nWB2yk8pyo0adR*.7kCMdnj", 4, 4)
        assertNotNull(result)
        assertEquals(4, result!!.width)
        assertEquals(4, result.height)
    }

    @Test
    fun `decode returns bitmap for different dimensions`() {
        val result = BlurHashDecoder.decode("LEHV6nWB2yk8pyo0adR*.7kCMdnj", 8, 8)
        assertNotNull(result)
        assertEquals(8, result!!.width)
        assertEquals(8, result.height)
    }

    @Test
    fun `decode returns bitmap for single component hash`() {
        val result = BlurHashDecoder.decode("00FF00", 4, 4)
        assertNotNull(result)
    }

    @Test
    fun `decode returns non-null bitmap for another valid hash`() {
        val result = BlurHashDecoder.decode("LKO2?U%2Tw=w]~RBVZRi};RPxuwH", 4, 3)
        assertNotNull(result)
        assertEquals(4, result!!.width)
        assertEquals(3, result.height)
    }

    @Test
    fun `decode returns ARGB_8888 bitmap`() {
        val result = BlurHashDecoder.decode("LEHV6nWB2yk8pyo0adR*.7kCMdnj", 4, 4)
        assertNotNull(result)
        assertEquals(Bitmap.Config.ARGB_8888, result!!.config)
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
        assertEquals(1, result!!.width)
        assertEquals(1, result.height)
    }
}
