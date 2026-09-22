package com.raulshma.jellyplay.core.network.config

import kotlinx.coroutines.flow.StateFlow

/**
 * User-facing view of the imported client certificate (mTLS).
 *
 * All fields are already stringified/primitive — the parsing itself is JVM
 * (`java.security`) and lives behind the jvmShared
 * [ClientCertificateManager][com.raulshma.jellyplay.core.network.config.ClientCertificateManager];
 * commonMain consumers (the Server Management UI) only ever see this
 * serializable summary.
 *
 * [subject]/[issuer] are the RFC 2253 distinguished-name strings of the
 * imported leaf certificate; `null` on every field but [enabled] when no
 * import exists.
 */
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
 * One import attempt handed to [ClientCertificateFacade.import].
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

/**
 * CommonMain seam over the app-level client certificate: import +
 * normalize, enable/disable, remove, and a live status snapshot. The
 * production implementation is the jvmShared
 * [ClientCertificateManager][com.raulshma.jellyplay.core.network.config.ClientCertificateManager]
 * (both JVM shells resolve the same Koin single), which also feeds the TLS
 * handshake layer through [ClientCertificateProvider].
 *
 * The certificate is deliberately APP-LEVEL (one active cert, matching the
 * mpv-shim behavior this feature ports); per-server selection is deferred.
 */
interface ClientCertificateFacade {

    /** Live status of the imported certificate (updates on import/toggle/remove). */
    val status: StateFlow<ClientCertificateStatus>

    /**
     * Parses + normalizes an import: the material is written as a PEM pair
     * (`client.crt` / `client.key`, unencrypted PKCS#8) under the app's
     * `certs` dir because mpv/ffmpeg need file paths, and OkHttp consumes the
     * same normalized pair through the handshake layer's key managers. The
     * optional CA override lands as `server-ca.pem`. The import ENABLES the
     * certificate; a passphrase (when supplied) is stored in secure storage.
     *
     * Fails (Result.failure) with a user-presentable message on unparsable or
     * incomplete material — nothing is written in that case.
     */
    suspend fun import(input: ClientCertificateImport): Result<ClientCertificateStatus>

    /** Presents (true) / withholds (false) the imported certificate. No-op when none imported. */
    fun setEnabled(enabled: Boolean)

    /** Deletes the imported material, the CA override, and the stored passphrase. */
    fun remove()
}
