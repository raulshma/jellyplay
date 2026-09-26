package com.raulshma.jellyplay.core.data.repository

/**
 * Feature-visible seam for the self-signed-certificate trust DECISION: does a
 * granted trust entry cover this server address?
 *
 * Split out of [AuthRepository] on purpose — the `AuthRepositorySurfaceTest`
 * ratchet pins that surface at 26 members and names the narrow-collaborator
 * route as the way a genuinely new auth capability lands (same rationale as
 * the [ServerDiscoveryRepository] / [RealtimeConnection] splits). Matching
 * semantics live in exactly one place, `core:network`'s
 * `SelfSignedTrustMatcher` — the SAME pure matcher the handshake-time trust
 * manager consults — so what this seam answers can never drift from what a
 * TLS handshake honors (a portless grant covers ANY port of its host, an
 * unparseable grant covers nothing). The impl is the single module-boundary
 * view of that matcher: feature modules (the Server Management screen's
 * toggle, its revoke sweep, the orphan-grant prune) reach the decision
 * through `core:data` and never import a `core:network` type — the
 * feature-layer core:network embargo.
 *
 * The granted set is passed BY THE CALLER, not read here: the caller owns
 * the observed state (the `NetworkOfflineStore.networkOffline` slice it
 * collects), so display-side recomposition invalidation keeps flowing from
 * the caller's own state read. Grant WRITES stay on `NetworkOfflineStore`
 * (`addSelfSignedTrustHost` / `removeSelfSignedTrustHost`) — this seam is
 * read-only.
 */
interface SelfSignedTrustRepository {

    /**
     * Whether the user-accepted self-signed trust decision ([grants], the
     * granted entries the caller observes) covers [address] — a stored server
     * address (a primary or an alternate), normalized here the same way
     * `connectToServer` normalizes before probing so the stored entry and
     * this read agree byte-for-byte.
     */
    fun isSelfSignedTrustGranted(grants: Set<String>, address: String): Boolean

    /**
     * The subset of [grants] covering ANY of [addresses] (each normalized as
     * above). The revoke answer: every returned entry must be dropped for
     * the addresses to lose trust, and a grant covering none of them must
     * survive. Returns the GRANTS (revoke targets for
     * `NetworkOfflineStore.removeSelfSignedTrustHost`), never the addresses.
     */
    fun selfSignedTrustGrantsCovering(grants: Set<String>, addresses: Collection<String>): Set<String>
}
