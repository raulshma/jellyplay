package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.ClientCertificateImport
import com.raulshma.jellyplay.core.model.ClientCertificateStatus
import com.raulshma.jellyplay.core.network.config.ClientCertificateFacade
import kotlinx.coroutines.flow.StateFlow

// Placement note (the SelfSignedTrustRepositoryImpl precedent): the interface
// in commonMain is the feature-visible seam; this impl is the one place that
// seam touches core:network — the facade is commonMain of :shared:core:network,
// so the impl could sit beside the interface, but every repository impl in
// this package lives in jvmShared next to its Koin wiring, and keeping that
// uniform leaves room for JVM-only certificate concerns to land here without
// a source-set move.

/**
 * Thin module-boundary view of [ClientCertificateFacade] (core:network stays
 * hidden from feature modules). Pure delegation: the manager owns the
 * material, the normalization contract and the live status flow — this seam
 * only renames the boundary so feature code can depend on core:data alone.
 */
class ClientCertificateRepositoryImpl(
    private val facade: ClientCertificateFacade,
) : ClientCertificateRepository {

    override val status: StateFlow<ClientCertificateStatus> = facade.status

    override suspend fun import(input: ClientCertificateImport): Result<ClientCertificateStatus> =
        facade.import(input)

    override fun setEnabled(enabled: Boolean) = facade.setEnabled(enabled)

    override fun remove() = facade.remove()
}
