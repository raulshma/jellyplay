package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.normalizeServerAddress
import com.raulshma.jellyplay.core.network.config.SelfSignedTrustMatcher

// Placement note (the ServerDiscoveryRepositoryImpl precedent): the interface
// in commonMain is the feature-visible seam; this impl is the one place that
// seam touches core:network — `SelfSignedTrustMatcher` is commonMain of
// :shared:core:network, so the impl could sit beside the interface, but every
// repository impl in this package lives in jvmShared next to its Koin wiring,
// and keeping that uniform leaves room for JVM-only trust concerns to land
// here without a source-set move.

/**
 * Thin module-boundary view of [SelfSignedTrustMatcher] (core:network stays
 * hidden from feature modules). Pure and stateless: the granted set arrives
 * per call (the caller owns the observed state), and every decision funnels
 * through the matcher — the single home of the matching rules — so the
 * answers here are by construction the answers a TLS handshake gets.
 */
class SelfSignedTrustRepositoryImpl : SelfSignedTrustRepository {

    override fun isSelfSignedTrustGranted(grants: Set<String>, address: String): Boolean =
        SelfSignedTrustMatcher.isAddressGranted(grants, normalizeServerAddress(address))

    override fun selfSignedTrustGrantsCovering(grants: Set<String>, addresses: Collection<String>): Set<String> =
        grants.filter { grant ->
            addresses.any { address ->
                SelfSignedTrustMatcher.isAddressGranted(setOf(grant), normalizeServerAddress(address))
            }
        }.toSet()
}
