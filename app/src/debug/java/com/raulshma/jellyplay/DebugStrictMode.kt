package com.raulshma.jellyplay

import android.os.StrictMode

/**
 * Debug-build-only StrictMode guard for the tuned startup path. This file
 * lives in the debug source set, so release builds never compile it and pay
 * nothing. penaltyFlashScreen instead of penaltyDeath: this is a diagnostic
 * aid — violations flash and log rather than kill the debug process.
 */
internal fun installDebugStrictMode() {
    StrictMode.setThreadPolicy(
        StrictMode.ThreadPolicy.Builder()
            .detectNetwork()
            .detectDiskReads()
            .detectDiskWrites()
            .penaltyLog()
            .penaltyFlashScreen()
            .build()
    )
    StrictMode.setVmPolicy(
        StrictMode.VmPolicy.Builder()
            .detectActivityLeaks()
            .detectLeakedClosableObjects()
            .penaltyLog()
            .build()
    )
}
