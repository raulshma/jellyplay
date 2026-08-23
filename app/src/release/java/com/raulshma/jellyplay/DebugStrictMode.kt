package com.raulshma.jellyplay

/**
 * Release no-op for the StrictMode install defined in the debug source set
 * (app/src/debug/java/com/raulshma/jellyplay/DebugStrictMode.kt). Keeping the
 * signature here lets JellyPlayApplication call it unconditionally while the
 * release variant compiles no policy code at all.
 */
internal fun installDebugStrictMode() {}
