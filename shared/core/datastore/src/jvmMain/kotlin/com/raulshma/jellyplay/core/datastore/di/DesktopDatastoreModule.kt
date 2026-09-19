package com.raulshma.jellyplay.core.datastore.di

import com.raulshma.jellyplay.core.datastore.DesktopSecureKeyValueStorage
import okio.Path
import org.koin.core.module.Module

/**
 * Desktop platform Koin module (docs/kmp-migration-plan.md): the shared
 * [datastorePlatformModule] over this shell's two actuals. [dataDir] is the
 * app's writable data directory; preference files land under
 * `dataDir/datastore/` mirroring the Android `filesDir/datastore/` layout.
 * Credential stores use the OS-keyring-backed [DesktopSecureKeyValueStorage]
 * with the documented `"JellyPlay/&lt;file&gt;"` service namespacing so the
 * three credential sets stay isolated, matching the per-file isolation on
 * Android.
 */
fun desktopDatastoreModule(dataDir: Path): Module = datastorePlatformModule(
    prefsDir = { dataDir / "datastore" },
    secureStorage = { name -> DesktopSecureKeyValueStorage("JellyPlay/$name") },
)
