package com.raulshma.jellyplay.web

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.koin.dsl.koinApplication

/**
 * The web module's first wasmJs test source-set canary (wave: first
 * `wasmJsTest` run). Deliberately MINIMAL: pure-logic helpers only, executed
 * on the Kotlin-provided Node runner — no browser, no CDP lane (that remains
 * tools/e2e/web-verify.mjs's job).
 *
 * Why these two subjects:
 *  - [CoilStats.axStatsLine]'s exact format is load-bearing ("COIL_STATS: …"
 *    is parsed by tools/e2e/web-soak.mjs — Main.kt's comment), and
 *    [CoilStats.axCacheLine] documents its no-cache degradation — both are
 *    pure string builders, so they are the one piece of the web shell that a
 *    browser-free unit test can pin for real.
 *  - [webDetailsPlatformModule] must at minimum CONSTRUCT and register into a
 *    fresh Koin application — the compilation canary that proves the test
 *    binary links the real wasmJsMain module (Koin DSL, the narrow repository
 *    type graph) and runs it on wasm.
 *
 * Deliberately ABSENT: WebConnectFlow's transport-failure classifier
 * (`isLikelyCorsOrTransport` et al.) — it is the ideal pure-logic subject but
 * is `private` in WebConnectFlow.kt, and main-source changes are out of scope
 * for a canary wave. When it becomes testable (internal + parameterized
 * browser-online flag), move the coverage there.
 */
class WebShellPureHelpersTest {

    @AfterTest
    fun resetCoilStats() {
        // The counters are process-global plain Ints; leave them pristine for
        // any later test wave sharing this object.
        CoilStats.requests = 0
        CoilStats.hits = 0
        CoilStats.misses = 0
        CoilStats.net = 0
        CoilStats.fail = 0
        CoilStats.success = 0
    }

    @Test
    fun axStatsLineMatchesTheSoakParserContract() {
        // Known counts in, exact wire format out — tools/e2e/web-soak.mjs
        // greps this line, so key order and spacing are part of the contract.
        CoilStats.hits = 3
        CoilStats.misses = 1
        CoilStats.net = 4
        CoilStats.fail = 2
        assertEquals(
            "COIL_STATS: hits=3 misses=1 net=4 fail=2",
            CoilStats.axStatsLine(),
        )
    }

    @Test
    fun axCacheLineDegradesToNoneBeforeTheCacheSingletonResolves() {
        // main() never runs in this test binary, so the lazily-built cache is
        // unset — the line must say so rather than crash.
        assertNullCache()
        assertEquals("COIL_CACHE: none", CoilStats.axCacheLine())
    }

    private fun assertNullCache() {
        // CoilStats.cache has an internal setter; a fresh test process never
        // built it, but assert-and-normalize in case a prior wave seeds one.
        CoilStats.cache = null
    }

    @Test
    fun webDetailsPlatformModuleConstructsAndRegistersIntoAFreshKoinApp() {
        // Registration only (no resolution — the narrow repository's
        // LibraryApiClient dependency is not bound here and must not be
        // instantiated). A malformed module definition throws at register time,
        // which is exactly what this canary pins.
        val app = koinApplication { modules(webDetailsPlatformModule()) }
        assertNotNull(app.koin, "the platform module must register into a fresh Koin app")
    }
}
