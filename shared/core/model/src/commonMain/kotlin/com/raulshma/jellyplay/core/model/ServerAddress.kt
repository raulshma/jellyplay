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
