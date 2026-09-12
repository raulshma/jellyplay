package com.raulshma.jellyplay.core.data.cast

import android.os.StrictMode

/**
 * Runs [block] with StrictMode disk-read detection suspended.
 *
 * The Cast SDK's one-time CastContext initialization loads its Dynamite dex
 * modules from disk on the calling (main) thread — the API has no off-thread
 * variant — so these third-party reads are permitted rather than fixed.
 * Release builds install no StrictMode policy, making this a no-op there.
 */
inline fun <T> withCastDiskReadsPermitted(block: () -> T): T {
    val previous = StrictMode.allowThreadDiskReads()
    try {
        return block()
    } finally {
        StrictMode.setThreadPolicy(previous)
    }
}
