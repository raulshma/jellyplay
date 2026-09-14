package com.raulshma.jellyplay.core.model

/**
 * The one server-address normalization policy: trim surrounding whitespace,
 * strip trailing '/' characters (all of them), and default a missing scheme
 * to `https://`. An explicit `http://` or `https://` prefix is preserved
 * as-is.
 *
 * Every address-entry point normalizes through this so the stored, probed
 * and trust-granted forms of one address agree byte-for-byte: the auth
 * repository's address add / login / Quick Connect lookups, both API
 * clients' probe paths (JVM and wasm — the wasm failover probing included),
 * the Add Server TLS-trust prompt, and the settings self-signed-trust
 * toggle. Sites that only trim (no scheme defaulting — they match
 * already-schemed stored addresses) are a deliberately different policy and
 * stay site-local.
 */
fun normalizeServerAddress(address: String): String = address.trim().trimEnd('/').let {
    if (it.startsWith("http://") || it.startsWith("https://")) it
    else "https://$it"
}

/**
 * Strips a trailing legacy `/emby` or `/mediabrowser` route prefix from a
 * schemed server address, or returns null when none is present.
 *
 * Jellyfin served its API under both aliases through 10.x; Jellyfin 12
 * removed them, so an address carrying one keeps working only when a
 * reverse proxy consumes the prefix before traffic reaches the server.
 * Connect-time probes use this to retry the bare address (and adopt it when
 * a real server identity answers) before declaring the server unreachable.
 *
 * Only a prefix that spans the whole path qualifies — anything deeper
 * (`/jellyfin/emby`, `/emby/sub`, a query string) belongs to the deployment
 * and is left alone.
 */
fun stripLegacyRoutePrefix(address: String): String? {
    val schemeEnd = address.indexOf("://")
    if (schemeEnd < 0) return null
    val pathStart = address.indexOf('/', schemeEnd + 3)
    if (pathStart < 0) return null
    val path = address.substring(pathStart).trimEnd('/')
    if (!path.equals("/emby", ignoreCase = true) && !path.equals("/mediabrowser", ignoreCase = true)) return null
    return address.substring(0, pathStart)
}
