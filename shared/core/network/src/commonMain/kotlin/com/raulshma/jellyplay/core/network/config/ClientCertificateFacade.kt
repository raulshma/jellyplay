package com.raulshma.jellyplay.core.network.config

import com.raulshma.jellyplay.core.model.ClientCertificateImport
import com.raulshma.jellyplay.core.model.ClientCertificateStatus
import kotlinx.coroutines.flow.StateFlow

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
 *
 * Module-boundary note: [ClientCertificateStatus]/[ClientCertificateImport]
 * live in `core:model` (pure primitives, UI-safe) and feature modules reach
 * these operations through `core:data`'s `ClientCertificateRepository` —
 * this interface stays the `core:network`-internal contract
 * [ClientCertificateManager] implements for the handshake layer.
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
