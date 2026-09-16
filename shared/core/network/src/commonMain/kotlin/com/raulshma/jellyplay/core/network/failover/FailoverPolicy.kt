package com.raulshma.jellyplay.core.network.failover

import com.raulshma.jellyplay.core.model.stripLegacyRoutePrefix

/**
 * The transport-agnostic failover decision core — the pure tables both probe
 * transports run. Today those transports are:
 *  - the jvmShared OkHttp prober ([ServerAddressRouter]) — android + desktop;
 *  - the wasmJs Ktor prober ([com.raulshma.jellyplay.core.network.api.KtorWasmAuthApiClient]).
 *
 * Everything here decides; nothing transports. A probe is an unauthenticated
 * `GET {address}/System/Info/Public`; ANY HTTP response — including a
 * non-2xx — means the endpoint is reachable (something answering HTTP is
 * there), only transport failures (DNS, connect, timeout) mean unreachable.
 * The mapping wire-response → [ProbeOutcome] is the transport's job and stays
 * platform-side, as does cancellation (a probe's injected prober lets
 * CancellationException propagate; this core never catches — there is
 * deliberately no cancellation test here, the contract is "passes through
 * untouched").
 *
 * DECLARED DIVERGENCES the JVM router keeps (it consumes the decisions below,
 * not the orchestration):
 *  - latency: the JVM probe captures per-probe `latencyMs` on its own
 *    [AddressProbeResult] for health checks/validation scoring. [ProbeOutcome]
 *    carries none by design.
 *  - probe fan-out: the router probes the primary alone first, then the
 *    alternates concurrently ([selectPreferredAddress]'s precomputed overload
 *    decides over the results); wasm probes strictly sequentially via the
 *    suspend overload.
 *  - all-down fallback: the router keeps its CURRENT active address (the app
 *    is simply offline; cached content keeps working); the common selection
 *    falls back to the primary for its stateless callers.
 */
data class ProbeOutcome(
    val reachable: Boolean,
    val serverId: String? = null,
    val serverName: String? = null,
    val error: Exception? = null,
    /**
     * The address that actually answered when the probe had to rewrite the
     * input: set when a legacy `/emby` or `/mediabrowser` route prefix was
     * stripped to reach the server (Jellyfin 12 removed those prefixes);
     * null when the probed address answered unchanged — or nothing answered.
     */
    val resolvedAddress: String? = null,
)

/**
 * The identity bar an answer must clear before its address is ADOPTED:
 * reachable AND carrying a real server id. This is why the strip-retry
 * ladders check `serverId` even though any HTTP response counts "reachable" —
 * a 404 body answers without one.
 */
fun answersWithIdentity(outcome: ProbeOutcome): Boolean =
    outcome.reachable && outcome.serverId != null

/**
 * The strip-retry decision: given the probed [address] and its [outcome],
 * the legacy-prefix-stripped address to re-probe once, or null when the
 * ladder stops here.
 *
 * The retry triggers whenever the first probe failed to produce a server
 * identity — Jellyfin 12 removed the legacy `/emby` and `/mediabrowser`
 * route prefixes, so a 10.x server upgraded in place 404s them (any HTTP
 * response still counts "reachable", hence the identity check), and a
 * transport death on the prefixed form qualifies just the same. A prefix
 * that spans anything deeper than the whole path (`/jellyfin/emby`,
 * `/emby/sub`) belongs to the deployment — [stripLegacyRoutePrefix] returns
 * null and the address is left alone.
 */
fun legacyPrefixRetryCandidate(address: String, outcome: ProbeOutcome): String? {
    if (answersWithIdentity(outcome)) return null
    return stripLegacyRoutePrefix(address)
}

/**
 * The adoption decision: when the stripped-address retry answered with a
 * real server identity, the STRIPPED address becomes the resolved form
 * ([resolvedAddress] — callers persist it so the next probe goes straight
 * to the working address); reverse proxies that consume the prefix answer
 * with an identity on the original address and never reach the retry. Any
 * other outcome leaves [original] standing unchanged.
 */
fun resolveLegacyPrefixAnswer(
    original: ProbeOutcome,
    retried: ProbeOutcome,
    strippedAddress: String,
): ProbeOutcome =
    if (answersWithIdentity(retried)) retried.copy(resolvedAddress = strippedAddress) else original

/**
 * The full connect-time probe ladder both transports share: normalize, probe,
 * stop on a direct identity answer, otherwise strip-retry the legacy route
 * prefix once and adopt the stripped address only when a real identity
 * answers. Cancellation passes straight through [probe] — never caught here.
 */
suspend fun probeResolvedAddress(
    address: String,
    probe: suspend (String) -> ProbeOutcome,
): ProbeOutcome {
    val normalized = address.trim().trimEnd('/')
    if (normalized.isEmpty()) {
        return ProbeOutcome(reachable = false, error = IllegalArgumentException("Blank address"))
    }
    val outcome = probe(normalized)
    val stripped = legacyPrefixRetryCandidate(normalized, outcome) ?: return outcome
    val retried = probe(stripped)
    return resolveLegacyPrefixAnswer(outcome, retried, stripped)
}

/**
 * Address selection, sequential form (the wasm transport): probe the primary,
 * then the alternates in order, returning the first reachable address — the
 * primary is always preferred while available. When nothing answers, the
 * primary is kept (the caller's stateless fallback; the JVM router instead
 * keeps its current active address — see the class-level divergence notes).
 */
suspend fun selectPreferredAddress(
    primary: String,
    alternates: List<String>,
    probe: suspend (String) -> ProbeOutcome,
): String {
    if (probe(primary).reachable) return primary
    for (alternate in alternates) {
        if (probe(alternate).reachable) return alternate
    }
    return primary
}

/**
 * Address selection, precomputed form (the JVM router): the same
 * primary-then-alternates order decided over already-collected probe
 * results, so the router can keep its primary-alone-first + concurrent
 * alternate fan-out transport. All down → the primary, mirroring the
 * sequential overload.
 */
fun selectPreferredAddress(
    primary: String,
    alternates: List<String>,
    results: Map<String, ProbeOutcome>,
): String =
    (listOf(primary) + alternates).firstOrNull { results[it]?.reachable == true } ?: primary
