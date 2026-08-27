package com.raulshma.jellyplay.core.data.session

/**
 * Runs [block] while holding [lock]'s monitor. Wave 15B seam for the
 * wasmJs target (`synchronized` does not exist there):
 *  - JVM actual (android + desktop): `synchronized(lock) { block() }` — the
 *    exact pre-promotion shape of [SessionCacheRegistry]'s registration-map
 *    sections, byte-for-byte the same monitor discipline.
 *  - wasmJs actual: pass-through — Kotlin/wasm is single-threaded today (no
 *    SharedArrayBuffer worker threads), so there is no other thread to
 *    exclude. Mirrors core:model's `withMapMonitor` seam for [TtlCache].
 *
 * Non-inline by construction (expect functions cannot be `inline`), so each
 * section allocates its lambda on the JVM where the inline `synchronized`
 * previously did not — registration is a per-startup event and the collector
 * copy runs per identity transition; the cost is negligible.
 */
internal expect fun <R> guardUnderLock(lock: Any, block: () -> R): R
