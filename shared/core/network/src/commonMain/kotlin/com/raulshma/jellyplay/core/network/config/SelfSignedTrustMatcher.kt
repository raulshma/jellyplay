package com.raulshma.jellyplay.core.network.config

/**
 * Pure, string-only host-matching decision for the self-signed-certificate
 * trust feature — the **commonMain single home** of "does a granted entry
 * cover this peer?". Lives beside [OkHttpConfig] so non-JVM callers (e.g. the
 * Server Management view model, which renders the per-server trust toggle)
 * can ask exactly the question the handshake-time trust layer answers,
 * without the JVM-only trust manager types: the jvmShared
 * `SelfSignedTrustHosts` facade (and through it the trust manager / hostname
 * verifier in `SelfSignedTrust.kt`) delegates here, so the two answers can
 * never drift.
 *
 * ## Matching
 *
 * Entries are canonical `scheme://host[:port]` strings (the
 * `ServerAddressRouter.addressString` form). [isGranted] matches a handshake
 * peer `(host, port)` when:
 *  - the entry's host equals the peer host, case-insensitively (IPv6 literals
 *    compare bracket-stripped); and
 *  - the entry carries an explicit port → it must equal the peer port; an
 *    entry without a port (the `https://host` form, i.e. port 443 implied)
 *    matches the host on ANY port — a deliberate, documented trade: the user
 *    trusted the *host*, so alternate ports of the same server (the Server
 *    Management "add address" flow) share the grant instead of each
 *    re-prompting. Trust never crosses to a different host.
 *  - when the peer port is unknown (`<= 0` — never happens on the trust
 *    manager path, tolerated for the hostname-verifier fallback), only the
 *    host is compared. This cannot widen trust: the trust manager has already
 *    gated the same handshake with the real port.
 *
 * Unparseable entries never grant anything (fail closed).
 */
object SelfSignedTrustMatcher {

    /** Canonical pieces of one granted entry (or address). */
    internal data class ParsedEntry(val host: String, val port: Int?)

    /** Default TLS port, assumed when an address carries no explicit port. */
    private const val DEFAULT_TLS_PORT = 443

    /**
     * Whether the handshake peer `(host, port)` is covered by a granted entry.
     * See the object-level KDoc for the matching rules; `port <= 0` means
     * "unknown" (host-only comparison).
     */
    fun isGranted(entries: Set<String>, host: String?, port: Int): Boolean {
        if (host.isNullOrBlank() || entries.isEmpty()) return false
        val peerHost = normalizeHost(host) ?: return false
        return entries.any { entry ->
            val parsed = parseEntry(entry) ?: return@any false
            parsed.host == peerHost && (parsed.port == null || port <= 0 || parsed.port == port)
        }
    }

    /**
     * Whether a granted entry covers [address] — an entry-shaped
     * `scheme://host[:port]` server address (parsed tolerantly by
     * [parseEntry]; a bare authority works too). A portless address checks
     * the scheme-default TLS port (443): the toggle for `https://host` is ON
     * exactly when a grant covers `host:443`, while a portless GRANT still
     * covers a ported address (the any-port rule above). This is the
     * display-side twin of the handshake-time [isGranted] decision — the
     * Server Management toggle can no longer show OFF for a grant the network
     * layer honors.
     */
    fun isAddressGranted(entries: Set<String>, address: String): Boolean {
        val parsed = parseEntry(address) ?: return false
        return isGranted(entries, parsed.host, parsed.port ?: DEFAULT_TLS_PORT)
    }

    /**
     * Parses `scheme://host[:port]` tolerantly: scheme optional (missing
     * scheme is treated as a bare authority), userinfo/path/query/fragment and
     * trailing slashes stripped, host lowercased, IPv6 brackets stripped.
     * Returns null for anything unparseable — an unparseable entry is never
     * granted (fail closed).
     */
    internal fun parseEntry(entry: String): ParsedEntry? {
        val raw = entry.trim()
        if (raw.isEmpty()) return null
        val afterScheme = if (raw.contains("://")) raw.substringAfter("://") else raw
        val authority = afterScheme
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        val hostPort = authority.substringAfterLast('@')
        if (hostPort.isNotEmpty()) {
            // Bracketed IPv6: [::1] or [::1]:8920
            if (hostPort.startsWith("[")) {
                val close = hostPort.indexOf(']')
                if (close < 0) return null
                val host = hostPort.substring(1, close).lowercase()
                if (host.isEmpty()) return null
                val portPart = hostPort.substring(close + 1)
                if (portPart.isEmpty()) return ParsedEntry(host, null)
                val port = portPart.removePrefix(":").toIntOrNull() ?: return null
                return if (port in 1..65535) ParsedEntry(host, port) else null
            }
            if (hostPort.count { it == ':' } == 1) {
                val host = hostPort.substringBefore(':').lowercase()
                if (host.isEmpty()) return null
                val port = hostPort.substringAfter(':').toIntOrNull() ?: return null
                return if (port in 1..65535) ParsedEntry(host, port) else null
            }
            if (hostPort.count { it == ':' } == 0) {
                return ParsedEntry(hostPort.lowercase(), null)
            }
            // Unbracketed IPv6 or garbage — fail closed.
            return null
        }
        return null
    }

    /** Lowercases + bracket-strips a peer host; null when blank. */
    internal fun normalizeHost(host: String): String? {
        val trimmed = host.trim().lowercase()
        if (trimmed.isEmpty()) return null
        return if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length > 2) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }
    }
}
