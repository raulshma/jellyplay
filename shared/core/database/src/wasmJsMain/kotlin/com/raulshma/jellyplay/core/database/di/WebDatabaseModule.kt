package com.raulshma.jellyplay.core.database.di

import androidx.room3.Room
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.crypto.TokenCipher
import com.raulshma.jellyplay.core.database.migration.ContainerProbe
import com.raulshma.jellyplay.core.database.migration.allMigrations
import kotlin.js.ExperimentalWasmJsInterop
import org.koin.core.module.Module
import org.koin.dsl.module
import org.w3c.dom.Worker

/**
 * Web platform Koin module (): the wasmJs counterpart of
 * [androidDatabaseModule] / [desktopDatabaseModule]. [JellyPlayDatabase] is
 * built through the KMP Room builder over
 * [WebWorkerSQLiteDriver] — a Web Worker running @sqlite.org/sqlite-wasm
 * with the OPFS-backed database handle (see webworker/worker.js), so the
 * browser gets a real, persistent, origin-scoped SQLite file instead of the
 * pre- "no Room on web" state.
 *
 * CONNECTION MODEL: the driver reports `hasConnectionPool == false`, so Room
 * drives the whole database through ONE connection multiplexed onto ONE
 * worker — the arrangement Room 3 documents for the web driver to avoid
 * concurrent OPFS handles tripping "database is locked". DAO calls are
 * suspend/Flow end to end, so the asynchronous worker round-trip needs no
 * blocking bridge anywhere. SINGLE-TAB constraint inherited from the
 * opfs-sahpool VFS (see webworker/worker.js): the pool takes an exclusive
 * OPFS directory lock, so a second tab of the app fails at open with a
 * pool-lock error — "web ships single-tab" until a different VFS/driver
 * arrangement replaces it.
 *
 * Migrations: a fresh web DB is created at the current schema directly (the
 * chain never runs on fresh databases), but the builder still assembles
 * [allMigrations] — with the TokenCipher/ContainerProbe seams this module
 * binds — so an existing web DB upgrades through the same chain the
 * android/desktop builds run (e.g. Migration53To54's ciphered-token and
 * container-backfill bodies, which degrade honestly under the pass-through
 * cipher / null probe below).
 */
fun webDatabaseModule(): Module = module {

    single<TokenCipher> { WebTokenCipher }

    single<ContainerProbe> { ContainerProbe { null } }

    single {
        Room.databaseBuilder<JellyPlayDatabase>("jellyplay.db")
            .addMigrations(*allMigrations(get<TokenCipher>(), get<ContainerProbe>()).toTypedArray())
            // dropAllTables=true matches the desktop KMP builder arrangement
            // (the KMP builder makes the flag explicit).
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .setDriver(WebWorkerSQLiteDriver(createWebDatabaseWorker()))
            .build()
    }
}

/**
 * The Web Worker the [WebWorkerSQLiteDriver] talks to — the vendored
 * webworker/worker.js (protocol implementation over @sqlite.org/sqlite-wasm
 * + OPFS; see that file's provenance header). The `new URL(...,
 * import.meta.url)` form is the webpack 5 worker idiom: the bundler compiles
 * worker.js (plus the SQLite WASM binary and the OPFS async-proxy worker it
 * spawns) into self-contained sibling assets of the app bundle — verified
 * working under this exact Kotlin 2.3.21 toolchain by the upstream
 * room-web-demo this module's arrangement follows.
 *
 * Whole-body `js()` only (the WasmClock rule this repo's wasmJsMain code
 * follows: wasm `js()` must be a function's entire body).
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun createWebDatabaseWorker(): Worker =
    js("""new Worker(new URL("jellyplay-sqlite-wasm-worker/worker.js", import.meta.url))""")

/**
 * Web [TokenCipher]: a PASS-THROUGH cipher. Jellyfin access tokens are stored
 * as plaintext in the OPFS database file.
 *
 * Why this is the honest choice on this platform: the database file already
 * lives in origin-scoped OPFS storage — no other origin, extension-free
 * browser, or local user process can read it; the browser sandbox IS the
 * trust boundary. Encrypting at rest would only shift the question to key
 * storage, and every key store the browser offers an app (localStorage,
 * IndexedDB) is readable by the same origin's JavaScript — i.e. an XSS that
 * can read ciphertext can read the key too, so a web-crypto cipher here adds
 * no adversary resistance, only complexity. (Same reasoning the web Seerr
 * credential store documents for its localStorage carve-out; the desktop
 * build keeps its OS-keychain-backed cipher because a desktop process CAN
 * read a raw file on disk.)
 *
 * Format note: [encrypt] returns plaintext unchanged and [decrypt] returns
 * its input unchanged, which the common TokenCipher contract already treats
 * as legal values (decrypt is forward-compatible with plaintext rows), so
 * rows written on web round-trip correctly and no DAO or migration code
 * branches on the platform.
 */
private object WebTokenCipher : TokenCipher {

    override fun encrypt(plaintext: String?): String? = plaintext

    override fun decrypt(ciphertext: String?): String? = ciphertext

    override fun isEncrypted(value: String?): Boolean = false
}
