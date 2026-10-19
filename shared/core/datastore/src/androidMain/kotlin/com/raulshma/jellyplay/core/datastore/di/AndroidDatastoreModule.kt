package com.raulshma.jellyplay.core.datastore.di

import android.content.Context
import com.raulshma.jellyplay.core.datastore.AndroidSecureKeyValueStorage
import java.io.File
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.module.Module

/**
 * Android platform Koin module: the shared
 * [datastorePlatformModule] over this shell's two actuals — the
 * `filesDir/datastore` preference root and the
 * EncryptedSharedPreferences-backed [AndroidSecureKeyValueStorage]. Paths and
 * file names are byte-for-byte what existing installs already use, so they
 * keep their data: `filesDir/datastore/<name>.preferences_pb` and the same
 * secure pref files.
 */
fun androidDatastoreModule(context: Context): Module = datastorePlatformModule(
    prefsDir = { File(context.filesDir, "datastore").absolutePath.toPath() },
    secureStorage = { name -> AndroidSecureKeyValueStorage(context, name) },
)
