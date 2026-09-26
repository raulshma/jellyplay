package com.raulshma.jellyplay.core.network.config

import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManager
/**
 * Handshake-time source for the app-level client certificate + optional
 * custom server CA (mTLS). Implemented by
 * [ClientCertificateManager] (Koin single, jvmShared — one instance serves
 * Android and Desktop) and consumed exclusively by
 * [applyTls]/[ServerTrustConfig]: the SAME call site covers the base OkHttp
 * client (API + WS + images + ExoPlayer streams + downloads) and the
 * `ServerAddressRouter` probe client, so every JVM TLS path in the app
 * presents the certificate.
 *
 * ## Live-read contract (mirrors the self-signed grants layer)
 *
 * Both members are consulted AT HANDSHAKE TIME, never cached by the caller:
 * `keyManagers()` is funneled through a delegating `X509KeyManager` installed
 * once per client, so toggling the certificate on/off takes effect on the next
 * handshake without rebuilding any OkHttpClient (the exact live-config
 * contract the self-signed layer's granted-set read already has).
 *
 * ## Fail-closed contract
 *
 * `null` means "no certificate configured" (disabled or never imported) — the
 * handshake proceeds without client auth. When a certificate IS enabled but
 * its on-disk material is missing or corrupt, `keyManagers()` must THROW
 * [ClientCertificateMaterialException] instead of quietly returning `null`:
 * a server that requires the certificate must see a TLS failure, never a
 * silent fallback to a certificate-less handshake. The same holds for
 * [customCa] with a corrupt `server-ca.pem`.
 */
interface ClientCertificateProvider {

    /**
     * Key managers presenting the enabled client certificate, or `null` when
     * none is configured. Throws [ClientCertificateMaterialException] when a
     * certificate is enabled but its material cannot be loaded (fail-closed).
     */
    fun keyManagers(): Array<KeyManager>?

    /**
     * The custom server CA override, or `null` when the platform trust anchors
     * apply. Throws [ClientCertificateMaterialException] when the override is
     * installed but unreadable/corrupt (fail-closed — never silently falls
     * back to platform trust).
     */
    fun customCa(): X509Certificate?

    companion object {
        /** No-op provider: no client certificate, platform trust anchors. */
        val NONE: ClientCertificateProvider = object : ClientCertificateProvider {
            override fun keyManagers(): Array<KeyManager>? = null
            override fun customCa(): X509Certificate? = null
        }
    }
}

/**
 * Raised by [ClientCertificateProvider] members when enabled/installed
 * certificate material is missing or unparsable. Extends
 * [CertificateException] so (a) the JSSE surfaces it inside the handshake as
 * a TLS failure, and (b) the app's [isTlsTrustFailure] classifier recognizes
 * it — an mTLS server must see a TLS error, never a silent
 * certificate-less connection or an opaque crash.
 */
class ClientCertificateMaterialException(message: String, cause: Throwable? = null) :
    CertificateException(message, cause)

/**
 * Parsed, normalized client-certificate material as stored on disk by
 * [ClientCertificateManager]: the leaf + its chain and the private key.
 */
internal class ParsedClientCertificate(
    val certificateChain: List<X509Certificate>,
    val privateKey: PrivateKey,
) {
    val leaf: X509Certificate get() = certificateChain.first()

    init {
        require(certificateChain.isNotEmpty()) { "empty certificate chain" }
    }
}
