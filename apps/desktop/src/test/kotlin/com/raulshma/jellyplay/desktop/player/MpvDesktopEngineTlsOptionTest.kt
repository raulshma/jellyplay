package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.desktop.player.mpv.MpvLib
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackTls
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * The mTLS slice of the desktop engine: `load()` must push the request's
 * [PlaybackTls] onto the live mpv context as the `tls-*` file-path options —
 * and actively CLEAR them when the request carries no certificate (options
 * persist on the context; the previous item's credentials must never leak
 * into this item's server, the http-header-fields discipline).
 *
 * Real libmpv, headless (`vo=null`/`ao=null`), a nonexistent local path as
 * the load target (the open fails asynchronously after the option writes —
 * irrelevant to the assertion, which reads the option storage). Skips on
 * machines without libmpv (the [MpvDesktopEngineTest] harness pattern).
 */
class MpvDesktopEngineTlsOptionTest {

    private fun libmpvAvailable(): Boolean = try {
        MpvLib.mpv
        true
    } catch (_: Throwable) {
        false
    }

    @Test
    fun `load with tls writes the three tls options`() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val engine = MpvDesktopEngine(extraOptions = mapOf("vo" to "null", "ao" to "null"))
        try {
            engine.load(
                PlaybackRequest(
                    uri = "file:///nonexistent-jellyplay-tls-test.bin",
                    title = "tls",
                    tls = PlaybackTls(
                        clientCertificatePath = "C:/certs/client.crt",
                        clientKeyPath = "C:/certs/client.key",
                        caPath = "C:/certs/server-ca.pem",
                    ),
                ),
            )
            val handle = checkNotNull(engine.liveMpvHandle())
            assertEquals("C:/certs/client.crt", MpvLib.getPropertyString(handle, "tls-cert-file"))
            assertEquals("C:/certs/client.key", MpvLib.getPropertyString(handle, "tls-key-file"))
            assertEquals("C:/certs/server-ca.pem", MpvLib.getPropertyString(handle, "tls-ca-file"))
        } finally {
            engine.release()
        }
    }

    @Test
    fun `load without tls clears previously set tls options`() {
        assumeTrue(libmpvAvailable(), { "libmpv not available on this machine" })
        val engine = MpvDesktopEngine(extraOptions = mapOf("vo" to "null", "ao" to "null"))
        try {
            engine.load(
                PlaybackRequest(
                    uri = "file:///nonexistent-jellyplay-tls-test.bin",
                    title = "tls",
                    tls = PlaybackTls(clientCertificatePath = "cert.crt", clientKeyPath = "cert.key"),
                ),
            )
            // Second load WITHOUT tls: the reset trio must land even though
            // the first load set cert/key (and left tls-ca-file default "").
            engine.load(
                PlaybackRequest(uri = "file:///nonexistent-jellyplay-tls-test-2.bin", title = "tls"),
            )
            val handle = checkNotNull(engine.liveMpvHandle())
            assertEquals("", MpvLib.getPropertyString(handle, "tls-cert-file"))
            assertEquals("", MpvLib.getPropertyString(handle, "tls-key-file"))
            assertEquals("", MpvLib.getPropertyString(handle, "tls-ca-file"))
        } finally {
            engine.release()
        }
    }
}
