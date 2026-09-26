package com.raulshma.jellyplay

/**
 * Release no-op for the LeakCanary install defined in the debug source set
 * (app/src/debug/java/com/raulshma/jellyplay/DebugLeakCanary.kt). Keeping the
 * signature here lets JellyPlayApplication call it unconditionally while the
 * release variant compiles none of it — the dependency itself is
 * debugImplementation-only and never reaches release APKs.
 */
internal fun installLeakCanary() {}
