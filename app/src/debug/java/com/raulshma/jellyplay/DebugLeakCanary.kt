package com.raulshma.jellyplay

/**
 * Debug-build-only LeakCanary hook. The leakcanary-android dependency
 * (debugImplementation) self-installs its watcher via a startup
 * ContentProvider, so there is nothing to start here — this function exists
 * as the debug-only seam for config tweaks, mirroring installDebugStrictMode.
 * Defaults: activities, fragments, view models and services are watched, a
 * heap dump fires after five retained sightings, and results surface via the
 * "Leaks" launcher entry and the LeakCanary logcat tag.
 */
internal fun installLeakCanary() {
    // Known noise: closing an mpv session logs a StrictMode
    // LeakedClosableViolation for android.view.Surface ("Surface.release not
    // called"). The Surface is created inside mpv's native MediaCodec hwdec
    // path (private copy-from-native ctor, no Java frames) and its finalizer
    // frees it — LeakCanary reports zero retained objects for it. Not a leak
    // to fix; do not add exclusionsRefs for app classes because of it.
    // Known-noise exclusions land here once a hunt flags library internals,
    // e.g.:
    // LeakCanary.config = LeakCanary.config.copy(
    //     exclusionsRefs = LeakCanary.config.exclusionsRefs + "androidx.example.SomeHolder"
    // )
}
