package com.raulshma.jellyplay.core.datastore.di

import androidx.datastore.core.DataStore
import androidx.datastore.core.InterProcessCoordinator
import androidx.datastore.core.ReadScope
import androidx.datastore.core.Storage
import androidx.datastore.core.StorageConnection
import androidx.datastore.core.WriteScope
import androidx.datastore.core.createSingleProcessCoordinator
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.emptyPreferences
import com.raulshma.jellyplay.core.datastore.ArrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.LocalStorageSecureKeyValueStorage
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.SubtitleProviderSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.WasmSecureKeyValueStorage
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.browser.localStorage
import okio.Buffer
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Web platform Koin module (docs/kmp-migration-plan.md §Phase W): the four
 * named preference DataStores, persisted to `window.localStorage`, plus the
 * credential stores. Wave 16B scope change: ONLY the Seerr credential store
 * is persistent now — [SeerrSecureCredentialsStore] binds to
 * [LocalStorageSecureKeyValueStorage] (localStorage-backed; see that class
 * for the why + the honest XSS caveat: cookie auth is browser-impossible, so
 * the user-entered API key is the only functional Seerr credential and it is
 * config-tier, not secret-tier). The Arr, subtitle-provider — and via
 * platform modules the Jellyfin — credential stores KEEP the web v1
 * session-memory cut ([WasmSecureKeyValueStorage], empty every boot).
 *
 * §Phase W spike outcome (recorded per plan): androidx.datastore 1.2.1 DOES
 * ship a fully usable wasmJs surface for custom persistence — `Storage<T>` /
 * `StorageConnection<T>` (readScope/writeScope/coordinator),
 * `createSingleProcessCoordinator`, `PreferencesSerializer` (proto
 * [okio.Buffer] roundtrip) and `PreferenceDataStoreFactory.create(storage)`
 * are all exported by the local `datastore-*-wasm-js` 1.2.1 klibs. So the
 * "browser storage adapter" arm of the plan is implemented natively on
 * DataStore rather than a session-memory fallback:
 *
 * - Each store's `Preferences` are serialized with the stock proto
 *   [PreferencesSerializer] — the localStorage value is Base64 of the exact
 *   `.preferences_pb` bytes the Android/desktop files hold.
 * - localStorage key mirrors the desktop path layout:
 *   `jellyplay/datastore/<name>.preferences_pb`.
 * - Corrupt/foreign entries reset to empty preferences instead of crashing;
 *   localStorage write failures (quota exceeded / storage disabled) degrade
 *   to session-only persistence — the DataStore's in-memory flow stays
 *   authoritative for the tab either way.
 * - Single-tab assumption: the coordinator is the in-process
 *   [createSingleProcessCoordinator]; cross-tab sync via storage events is a
 *   deliberate v1 non-goal. Note: the String overload of
 *   createSingleProcessCoordinator is @RestrictTo(LIBRARY_GROUP) upstream —
 *   invisible to lint from wasmJsMain, so a datastore upgrade that tightens
 *   it will surface here at compile time only.
 */
fun webDatastoreModule(): Module = module {

    single(qualifier = DatastoreQualifiers.userPreferencesDataStore) {
        webPreferencesDataStore("user_prefs")
    }

    single(qualifier = DatastoreQualifiers.seerrPreferencesDataStore) {
        webPreferencesDataStore("seerr_prefs")
    }

    single(qualifier = DatastoreQualifiers.arrPreferencesDataStore) {
        webPreferencesDataStore("arr_prefs")
    }

    single(qualifier = DatastoreQualifiers.subtitleProviderPreferencesDataStore) {
        webPreferencesDataStore("subtitle_provider_prefs")
    }

    // Wave 16B: the ONLY persistent credential store on web — the API key is
    // the sole Seerr auth that can function in a browser (Cookie is a
    // fetch-forbidden header), and it is user-entered config, so it persists
    // via localStorage. See [LocalStorageSecureKeyValueStorage] for the full
    // rationale + security caveat.
    single {
        SeerrSecureCredentialsStore(
            LocalStorageSecureKeyValueStorage(),
        )
    }

    // Session-memory cuts UNCHANGED (see the module KDoc): no Arr/subtitle
    // — and no Jellyfin — secret ever leaves process memory on web.
    single {
        ArrSecureCredentialsStore(
            WasmSecureKeyValueStorage(),
        )
    }

    single {
        SubtitleProviderSecureCredentialsStore(
            WasmSecureKeyValueStorage(),
        )
    }
}

private fun webPreferencesDataStore(name: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        storage = WebLocalStoragePreferencesStorage("jellyplay/datastore/$name.preferences_pb"),
    )

/**
 * [Storage] over one localStorage key. [createConnection] is called once per
 * DataStore; the returned connection reads lazily so the value always
 * reflects the current localStorage state within the tab.
 */
private class WebLocalStoragePreferencesStorage(
    private val storageKey: String,
) : Storage<Preferences> {

    override fun createConnection(): StorageConnection<Preferences> =
        WebLocalStorageConnection(storageKey)
}

private class WebLocalStorageConnection(
    private val storageKey: String,
) : StorageConnection<Preferences> {

    // No cross-tab locking (documented single-tab v1): this coordinator only
    // serializes access within the page, matching DataStore's expectations.
    override val coordinator: InterProcessCoordinator =
        createSingleProcessCoordinator("jellyplay/web/$storageKey")

    override suspend fun <R> readScope(
        block: suspend (readScope: ReadScope<Preferences>, isActive: Boolean) -> R,
    ): R {
        val scope = object : ReadScope<Preferences> {
            override suspend fun readData(): Preferences = loadPreferences(storageKey)
            override fun close() {}
        }
        try {
            // isActive=true: the scope stays valid after the block (localStorage
            // reads are repeatable), matching Okio's file-backed connection.
            return block(scope, true)
        } finally {
            scope.close()
        }
    }

    override suspend fun writeScope(block: suspend (WriteScope<Preferences>) -> Unit) {
        val scope = object : WriteScope<Preferences> {
            override suspend fun readData(): Preferences = loadPreferences(storageKey)
            override suspend fun writeData(value: Preferences) {
                savePreferences(storageKey, value)
            }
            override fun close() {}
        }
        try {
            block(scope)
        } finally {
            scope.close()
        }
    }

    override fun close() {}
}

@OptIn(ExperimentalEncodingApi::class)
private suspend fun loadPreferences(storageKey: String): Preferences {
    val encoded = try {
        localStorage.getItem(storageKey)
    } catch (_: Throwable) {
        // localStorage unavailable (storage disabled / privacy mode):
        // degrade to a session-only store.
        null
    } ?: return emptyPreferences()

    val bytes = try {
        Base64.decode(encoded)
    } catch (_: IllegalArgumentException) {
        // Foreign/corrupt entry under our key: treat as no data.
        return emptyPreferences()
    }

    return try {
        PreferencesSerializer.readFrom(Buffer().apply { write(bytes) })
    } catch (_: Throwable) {
        // Undecodable proto payload: reset rather than crash the store. A
        // corruption handler can't help here (no prior file to fall back on).
        emptyPreferences()
    }
}

@OptIn(ExperimentalEncodingApi::class)
private suspend fun savePreferences(storageKey: String, value: Preferences) {
    val bytes = Buffer().let { sink ->
        PreferencesSerializer.writeTo(value, sink)
        sink.readByteArray()
    }
    try {
        localStorage.setItem(storageKey, Base64.encode(bytes))
    } catch (t: Throwable) {
        // Quota exceeded / storage disabled: the DataStore's in-memory state
        // stays authoritative for the session; only persistence is lost.
        println("JellyPlay datastore: localStorage write failed for $storageKey ($t)")
    }
}
