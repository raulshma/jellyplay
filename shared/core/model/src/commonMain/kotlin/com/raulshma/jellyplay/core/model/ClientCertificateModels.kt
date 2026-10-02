package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable

/**
 * User-facing view of the imported client certificate (mTLS).
 *
 * All fields are already stringified/primitive — the parsing itself is JVM
 * (`java.security`) and lives in `core:network`'s jvmShared
 * `ClientCertificateManager` behind the `ClientCertificateFacade` seam
 * (re-exposed to features through `core:data`'s `ClientCertificateRepository`,
 * keeping the feature-layer core:network embargo intact); UI consumers
 * (the Server Management screen) only ever see this serializable summary.
 *
 * [subject]/[issuer] are the RFC 2253 distinguished-name strings of the
 * imported leaf certificate; `null` on every field but [enabled] when no
 * import exists.
 */
@Immutable
data class ClientCertificateStatus(
    /**
     * Whether the imported certificate is presented on TLS handshakes.
     * `false` when nothing is imported, the user toggled it off, or the
     * on-disk material vanished.
     */
    val enabled: Boolean = false,
    /** Whether normalized material (`client.crt` + `client.key`) exists on disk. */
    val materialPresent: Boolean = false,
    /** RFC 2253 subject DN of the imported leaf certificate, or `null` (also when unreadable). */
    val subject: String? = null,
    /** RFC 2253 issuer DN of the imported leaf certificate, or `null`. */
    val issuer: String? = null,
    /** Epoch millis of the leaf's not-before bound, or `null` when unknown. */
    val notValidBeforeMs: Long? = null,
    /** Epoch millis of the leaf's not-after bound, or `null` when unknown. */
    val notValidAfterMs: Long? = null,
    /** Whether a custom server CA override (`certs/server-ca.pem`) is installed. */
    val customCaConfigured: Boolean = false,
) {
    val isImported: Boolean get() = materialPresent
}

/**
 * One import attempt handed to the client-certificate import operation.
 *
 * Either [pkcs12Bytes] (a `.p12`/`.pfx` bundle carrying key + certificate
 * chain) or the [certificatePemBytes] + [privateKeyPemBytes] pair must be
 * supplied; [serverCaPemBytes] is the optional trust-anchor override.
 * [passphrase] opens password-protected PKCS#12 bundles (empty/unset for
 * password-less material).
 */
class ClientCertificateImport(
    val pkcs12Bytes: ByteArray? = null,
    val certificatePemBytes: ByteArray? = null,
    val privateKeyPemBytes: ByteArray? = null,
    val serverCaPemBytes: ByteArray? = null,
    val passphrase: CharArray? = null,
)
