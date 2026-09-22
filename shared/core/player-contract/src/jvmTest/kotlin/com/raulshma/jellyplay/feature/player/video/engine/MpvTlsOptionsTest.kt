package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Exhaustive matrix for [MpvTlsOptions.from]: the ONLY translation
 * from [PlaybackRequest.tls] to mpv's `tls-*` option writes, shared by the
 * Android and desktop engines so the emitted set cannot drift. Pins:
 *  - full triple (cert + key + CA) when every path is present;
 *  - CA omitted from the WRITE set when absent (mpv keeps its default — no
 *    empty tls-ca-file write that some builds treat as an error);
 *  - the RESET shape when no certificate is active: every option explicitly
 *    cleared — mpv options persist on the context, so the previous item's
 *    credentials must be actively dropped, never inherited (the
 *    http-header-fields discipline).
 */
class MpvTlsOptionsTest {

    @Test
    fun `full triple maps to the three tls options`() {
        val writes = MpvTlsOptions.from(
            PlaybackTls(
                clientCertificatePath = "C:/certs/client.crt",
                clientKeyPath = "C:/certs/client.key",
                caPath = "C:/certs/server-ca.pem",
            ),
        )
        assertEquals(
            listOf(
                "tls-cert-file" to "C:/certs/client.crt",
                "tls-key-file" to "C:/certs/client.key",
                "tls-ca-file" to "C:/certs/server-ca.pem",
            ),
            writes,
        )
    }

    @Test
    fun `ca-less certificate maps to the cert pair only`() {
        val writes = MpvTlsOptions.from(
            PlaybackTls(clientCertificatePath = "/data/certs/client.crt", clientKeyPath = "/data/certs/client.key"),
        )
        assertEquals(
            listOf(
                "tls-cert-file" to "/data/certs/client.crt",
                "tls-key-file" to "/data/certs/client.key",
            ),
            writes,
        )
    }

    @Test
    fun `null tls resets all three options`() {
        assertEquals(
            listOf(
                "tls-cert-file" to "",
                "tls-key-file" to "",
                "tls-ca-file" to "",
            ),
            MpvTlsOptions.from(null),
        )
    }

    @Test
    fun `every emitted option name is one mpv documents`() {
        // The three names this feature relies on (the CURRENT mpv surface —
        // verified against the bundled libmpv v0.40; see the mapper's KDoc
        // for the naming history), pinned so a rename in the mapper cannot
        // silently emit an unknown option mpv would ignore.
        val names = (MpvTlsOptions.from(PlaybackTls("a", "b", "c")) + MpvTlsOptions.from(null))
            .map { it.first }
            .toSet()
        assertEquals(setOf("tls-cert-file", "tls-key-file", "tls-ca-file"), names)
    }
}
