package com.raulshma.jellyplay.core.database

import com.raulshma.jellyplay.core.database.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * PROOF LANE — the only test that exercises Room on wasmJs through the
 * REAL production wiring: [di.webDatabaseModule]'s WebWorkerSQLiteDriver over
 * the vendored OPFS worker (webworker/worker.js), the same construction the
 * web shell's startKoin performs. Karma serves the webpack bundle from
 * localhost (a secure context), so the worker's OPFS-backed OpfsDb is live —
 * this is not a mock or a memory-database stand-in: a row travels
 * Kotlin -> WebWorkerSQLiteDriver protocol -> worker postMessage ->
 * @sqlite.org/sqlite-wasm -> OPFS file and back.
 *
 * Runs on wasmJsBrowserTest only (the module has no nodejs() target — OPFS
 * is browser-only); jvmTest keeps the exhaustive DAO/migration suites on
 * BundledSQLiteDriver. Karma launches a fresh profile per run, so the OPFS
 * store starts empty and the counts below are deterministic.
 */
class WebDatabaseRoundtripTest {

    private lateinit var database: JellyPlayDatabase

    @BeforeTest
    fun setup() {
        // Exactly the webDatabaseModule single's construction (the module
        // itself has no browser-only dependencies beyond Koin, so the test
        // re-states the builder rather than booting a Koin application).
        database = androidx.room3.Room.databaseBuilder<JellyPlayDatabase>("jellyplay.db")
            .setDriver(
                androidx.sqlite.driver.web.WebWorkerSQLiteDriver(
                    createWebDatabaseWorkerForTest(),
                )
            )
            .build()
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    @Test
    fun insert_then_read_back_through_worker_opfs() = runTest {
        val dao = database.searchHistoryDao()
        dao.clearAll("roundtrip-user")

        dao.insert(
            SearchHistoryEntity(query = "batman", userId = "roundtrip-user", searchedAt = 1_000L)
        )
        dao.insert(
            SearchHistoryEntity(query = "superman", userId = "roundtrip-user", searchedAt = 3_000L)
        )

        // suspend COUNT query round-trips the worker protocol (prepare/step).
        assertEquals(2, dao.getCount("roundtrip-user"))
        // Flow query proves invalidation-tracking works through the driver.
        val recent = dao.getRecent("roundtrip-user", limit = 10).first()
        assertEquals(listOf("superman", "batman"), recent.map { it.query })
    }
}

/** Same js() construction as di/WebDatabaseModule.kt's private
 *  createWebDatabaseWorker (whole-body js() — the WasmClock rule). */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun createWebDatabaseWorkerForTest(): org.w3c.dom.Worker =
    js("""new Worker(new URL("jellyplay-sqlite-wasm-worker/worker.js", import.meta.url))""")
