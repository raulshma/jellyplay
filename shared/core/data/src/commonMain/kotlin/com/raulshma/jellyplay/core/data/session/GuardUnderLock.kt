package com.raulshma.jellyplay.core.data.session

/**
 * Runs [block] while holding [lock]'s monitor:
 *  - JVM actual (android + desktop): `synchronized(lock) { block() }` — the
 *    exact pre-promotion shape of [SessionCacheRegistry]'s registration-map
 *    sections, byte-for-byte the same monitor discipline.
 *
 * Non-inline by construction (expect functions cannot be `inline`), so each
 * section allocates its lambda on the JVM where the inline `synchronized`
 * previously did not — registration is a per-startup event and the collector
 * copy runs per identity transition; the cost is negligible.
 */
internal expect fun <R> guardUnderLock(lock: Any, block: () -> R): R
