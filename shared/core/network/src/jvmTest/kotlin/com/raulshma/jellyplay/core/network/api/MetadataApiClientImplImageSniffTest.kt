package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.network.failover.ServerAddressRouter
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Table-driven coverage for [MetadataApiClientImpl.sniffImageMediaType] —
 * every magic-number branch plus the fallback. The webp case pins the
 * WEBP-fourcc guard (a bare RIFF header — WAV/AVI territory — must NOT
 * label as image/webp), added after the review.
 */
class MetadataApiClientImplImageSniffTest {

    // The sniff is a pure byte function; the engine is inert here (mockk
    // pattern from JellyfinApiEngineSessionTest; same-package ctor deps
    // need no imports).
    private val client = MetadataApiClientImpl(
        JellyfinApiEngine(
            LazyProvider { mockk<Jellyfin>(relaxed = true) },
            LazyProvider { OkHttpClient() },
            DeviceProfileProvider(DesktopDeviceCodecCapabilities()),
            ServerAddressRouter(),
        ),
    )

    @Test
    fun `magic numbers map to concrete image mimes`() {
        val table = listOf(
            // PNG: 89 50 4E 47
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A) to "image/png",
            // JPEG: FF D8 FF
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) to "image/jpeg",
            // WEBP: RIFF....WEBP (fourcc at bytes 8-11)
            "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray(Charsets.ISO_8859_1) to "image/webp",
            // GIF: GIF8
            "GIF89a".toByteArray() to "image/gif",
            // BMP: BM
            byteArrayOf(0x42, 0x4D, 0x00, 0x00) to "image/bmp",
        )
        table.forEach { (bytes, expected) ->
            assertEquals(expected, client.sniffImageMediaType(bytes), "bytes=${bytes.joinToString()}")
        }
    }

    @Test
    fun `bare RIFF without WEBP fourcc is not webp`() {
        // WAV magic: RIFF....WAVE — falls through to the png fallback.
        val wav = "RIFF\u0000\u0000\u0000\u0000WAVEfmt ".toByteArray(Charsets.ISO_8859_1)
        assertEquals("image/png", client.sniffImageMediaType(wav))
    }

    @Test
    fun `unknown magic falls back to png`() {
        assertEquals("image/png", client.sniffImageMediaType(byteArrayOf(0x00, 0x01, 0x02)))
        assertEquals("image/png", client.sniffImageMediaType(ByteArray(0)))
    }
}
