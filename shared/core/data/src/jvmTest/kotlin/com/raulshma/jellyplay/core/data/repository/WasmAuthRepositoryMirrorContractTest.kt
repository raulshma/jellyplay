package com.raulshma.jellyplay.core.data.repository

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The wasm↔JVM repository mirror contract (the drift ratchet for the
 * session-seam port): [WasmAuthRepository] (wasmJsMain) is a hand-ported
 * counterpart of the jvmShared [AuthRepositoryImpl], and the wasmJsTest lane
 * never executes for this module (the target is compile-only — see
 * core:data's build script), so a surface drift (an [AuthRepository] member
 * left unimplemented on wasm, a stray extra override, or — worst — a
 * RealtimeConnection member sneaking in where the platform declares "no
 * realtime") is invisible until a web user hits it. This test reads both
 * source files at runtime and pins the port:
 *
 *  - WasmAuthRepository implements EXACTLY the [AuthRepository] member set
 *    (every property and function of the interface has an override, and
 *    every override corresponds to an interface member — no extras);
 *  - the class implements AuthRepository and does NOT implement
 *    [RealtimeConnection] (the declared web divergence: no websocket client
 *    on wasm);
 *  - the port keeps the shared choreography markers — the load-bearing
 *    private helpers the JVM impl's establishment spine runs through
 *    (adoptPersistedSession / storedTokenRejected / persistSession) exist
 *    on the wasm side too, so a wholesale body rewrite that quietly drops
 *    the 401 guard or the persisted-session spine fails the JVM build.
 *
 * Source-scanning like the core:network `WasmMirrorContractTest` (same
 * module-root walk + comment stripping); reflection cannot reach wasmJsMain
 * from jvmTest, so the source scan IS the lane (the repo's mirror-contract
 * rule for compile-only wasm targets).
 */
class WasmAuthRepositoryMirrorContractTest {

    // ── Source access (the network mirror-contract precedent) ────────────

    /** This module's root (the nearest ancestor holding our source sets). */
    private val moduleRoot: File by lazy {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir.parentFile != null && !File(dir, "src/commonMain").isDirectory) dir = dir.parentFile
        assertTrue(
            File(dir, "src/commonMain").isDirectory,
            "could not locate src/commonMain from ${System.getProperty("user.dir")}",
        )
        dir
    }

    private fun source(relativePath: String): String =
        File(moduleRoot, relativePath).let { file ->
            assertTrue(file.isFile, "missing source file ${file.absolutePath}")
            stripComments(file.readText(Charsets.UTF_8))
        }

    private val interfaceText by lazy {
        source("src/commonMain/kotlin/com/raulshma/jellyplay/core/data/repository/AuthRepository.kt")
    }
    private val wasmText by lazy {
        source("src/wasmJsMain/kotlin/com/raulshma/jellyplay/core/data/repository/WasmAuthRepository.kt")
    }

    /**
     * Removes // and /* */ comments while preserving string literals — the
     * diverged-member assertions must not read identifiers out of KDoc
     * (WasmAuthRepository's class KDoc NAMES RealtimeConnection in its
     * declared-divergence list, which is documentation, not a supertype).
     * Kotlin block comments nest, so loop until stable.
     */
    private fun stripComments(source: String): String {
        var stripped = source
        while (true) {
            val next = stripped.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            if (next == stripped) break
            stripped = next
        }
        return stripped.replace(Regex("(?m)//[^\n]*"), "")
    }

    // ── Extraction ────────────────────────────────────────────────────────

    /**
     * The interface's member names — `val` properties and (optionally
     * suspend) functions declared directly in `interface AuthRepository`.
     * The file declares no nested types and no default-arg overloads, so a
     * name-level scan is exact.
     */
    private fun interfaceMembers(text: String): Set<String> =
        Regex("(?m)^\\s{4}(?:suspend\\s+)?fun\\s+(\\w+)\\s*\\(").findAll(text).map { it.groupValues[1] }.toSet() +
            Regex("(?m)^\\s{4}val\\s+(\\w+)\\s*:").findAll(text).map { it.groupValues[1] }.toSet()

    /** The wasm impl's override member names (properties and functions). */
    private fun wasmOverrides(text: String): Set<String> =
        Regex("(?m)^\\s{4}override\\s+(?:suspend\\s+)?fun\\s+(\\w+)\\s*\\(").findAll(text).map { it.groupValues[1] }.toSet() +
            Regex("(?m)^\\s{4}override\\s+val\\s+(\\w+)\\s*:").findAll(text).map { it.groupValues[1] }.toSet()

    /**
     * The realtime seam's member names — extracted from
     * [RealtimeConnection] so the NOT-implemented side tracks the interface
     * itself instead of a hand-kept list.
     */
    private fun realtimeMembers(text: String): Set<String> =
        Regex("(?m)^\\s{4}(?:suspend\\s+)?fun\\s+(\\w+)\\s*\\(").findAll(text).map { it.groupValues[1] }.toSet() +
            Regex("(?m)^\\s{4}val\\s+(\\w+)\\s*:?").findAll(text).map { it.groupValues[1] }.toSet()

    // ── The contract ──────────────────────────────────────────────────────

    @Test
    fun `the source scans found the real member sets (guard against a broken scan)`() {
        val members = interfaceMembers(interfaceText)
        val overrides = wasmOverrides(wasmText)
        assertTrue(
            members.size >= 20 && overrides.size >= 20,
            "scan is broken — interface members=${members.size}, wasm overrides=${overrides.size} " +
                "(an empty scan would vacuously pass every parity assertion below)",
        )
    }

    @Test
    fun `WasmAuthRepository implements exactly the AuthRepository surface`() {
        val expected = interfaceMembers(interfaceText)
        val actual = wasmOverrides(wasmText)
        val missing = expected - actual
        val extra = actual - expected
        assertTrue(
            missing.isEmpty() && extra.isEmpty(),
            "WasmAuthRepository diverged from the AuthRepository interface — " +
                "unimplemented=$missing (the web shell silently lacks these establishment paths), " +
                "extra=$extra (overrides that belong to no interface member).",
        )
    }

    @Test
    fun `WasmAuthRepository implements AuthRepository only, never RealtimeConnection`() {
        val supertypesClause = Regex("\\)\\s*:\\s*([^{]+)\\{").find(wasmText)?.groupValues?.get(1).orEmpty()
        assertTrue(
            "AuthRepository" in supertypesClause,
            "WasmAuthRepository must declare the AuthRepository supertype explicitly (found: $supertypesClause)",
        )
        assertTrue(
            "RealtimeConnection" !in wasmText,
            "RealtimeConnection must NOT appear in WasmAuthRepository — the declared web divergence " +
                "(no websocket client on wasm; the socket seam stays jvmShared). If web gains realtime, " +
                "update the declared-divergence KDoc AND this ratchet in the same commit.",
        )
        val realtime = realtimeMembers(
            source("src/commonMain/kotlin/com/raulshma/jellyplay/core/data/repository/RealtimeConnection.kt"),
        )
        // `disconnect` is ALSO an AuthApiClient suspend member used by the
        // shared choreography (logout/adopt/restore teardown) — it is not a
        // RealtimeConnection override (that one is non-suspend and not an
        // override at all here), so only the unambiguous names are pinned.
        val unambiguousRealtime = realtime - setOf("disconnect")
        val leaked = wasmOverrides(wasmText) intersect unambiguousRealtime
        assertTrue(
            leaked.isEmpty(),
            "WasmAuthRepository overrides RealtimeConnection member(s) $leaked — the class implements " +
                "AuthRepository only (declared divergence).",
        )
    }

    @Test
    fun `the establishment-choreography spine is present on the wasm side`() {
        // The load-bearing private helpers of the JVM impl's session spine:
        // losing any of them means the port quietly dropped a guard (the
        // stored-token 401 check, the atomic adoption path, or the persisted
        // session write) while keeping the interface green.
        for (marker in listOf("adoptPersistedSession", "storedTokenRejected", "persistSession", "validateRestoredSession", "selectReachableAddressDefensively")) {
            assertTrue(
                marker in wasmText,
                "WasmAuthRepository lost the choreography helper `$marker` — the port must keep the " +
                    "JVM impl's establishment spine (adopt/401-guard/persist/validate/failover), not a rewrite.",
            )
        }
    }
}
