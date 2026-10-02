package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.ClientCertificateImport
import com.raulshma.jellyplay.core.model.ClientCertificateStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Feature-visible seam over the app-level client certificate (mTLS):
 * import + normalize, enable/disable, remove, and a live status snapshot.
 *
 * The `SelfSignedTrustRepository` shape applied to the certificate half of
 * the Server Management screen: every type crossing this boundary is a
 * pure-primitive `core:model` value ([ClientCertificateStatus] /
 * [ClientCertificateImport]) — the JVM parsing (`java.security`), on-disk
 * normalization and TLS key-manager plumbing stay in `core:network`'s
 * `ClientCertificateManager`, which implements the `ClientCertificateFacade`
 * this repository delegates to. Feature modules bind THIS interface and
 * never import a `core:network` type — the feature-layer core:network
 * embargo.
 *
 * The certificate is deliberately APP-LEVEL (one active cert, matching the
 * mpv-shim behavior this feature ports); per-server selection is deferred.
 */
interface ClientCertificateRepository {

    /** Live status of the imported certificate (updates on import/toggle/remove). */
    val status: StateFlow<ClientCertificateStatus>

    /**
     * Parses + normalizes an import and ENABLES the certificate. Fails
     * (Result.failure) with a user-presentable message on unparsable or
     * incomplete material — nothing is written in that case.
     */
    suspend fun import(input: ClientCertificateImport): Result<ClientCertificateStatus>

    /** Presents (true) / withholds (false) the imported certificate. No-op when none imported. */
    fun setEnabled(enabled: Boolean)

    /** Deletes the imported material, the CA override, and the stored passphrase. */
    fun remove()
}
