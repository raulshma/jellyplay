package com.raulshma.jellyplay.core.network.api

/**
 * Minimal deferred-provider seam for [JellyfinApiEngine]'s constructor —
 * the exact surface `dagger.Lazy` used to supply there (audit BIN-8). The
 * full dagger artifact existed in the dependency graph ONLY to source that
 * interface: no Dagger compiler runs anywhere (KSP is Room-only, Koin
 * constructs every type), Android stripped the classes via R8, but the
 * desktop runtime jar shipped them dead. This local `fun interface` keeps
 * every call-site shape working unchanged — the Koin adapter
 * (`NetworkKoinModules.memoizingLazy`) still wraps `lazy(...)` so first
 * `.get()` memoizes with the same single-evaluation semantics, and the
 * jvmTest suites still construct it with SAM syntax (`LazyProvider { value }`).
 */
fun interface LazyProvider<out T> {
    fun get(): T
}
