package com.raulshma.jellyplay.core.data.session

import com.raulshma.jellyplay.core.model.CacheIdentity
import kotlinx.coroutines.flow.SharedFlow

/**
 * Wave 15B seam (wasmJs target of `core:data`): the identity surface
 * `SeerrRepositoryImpl` and [SessionCacheRegistry] need from the session —
 * cache identity reads plus the transition stream the registry reacts to —
 * WITHOUT the JVM-bound machinery [HomeSession] is built on (the OkHttp
 * `JellyfinApiClient` + `AtomicReference` mirror). On android/desktop the
 * jvmShared DI graph binds [HomeSession] here (existing behavior unchanged);
 * wasmJs binds an `AtomicSessionState`-backed provider (`dataWasmModule`)
 * over the same classifier shape.
 */
interface SessionIdentityProvider {

    /**
     * Identity changes, in order. Replay 1 — a late subscriber is re-delivered
     * the latest transition (handlers are idempotent invalidations), so the
     * privacy clear can't be missed; the never-transitioned start state
     * (fresh install, signed out) has nothing to replay and needs the
     * one-shot identity reads instead.
     */
    val transitions: SharedFlow<HomeSessionTransition>

    /**
     * The current identity read from the SOURCE flow (`.first()`), not the
     * mirror. Suspend + non-blocking, and immune to the observe-ordering race
     * where a caller's collector fires before the provider's own collector has
     * written the mirror. Returns `null` when no identity is established.
     */
    suspend fun currentIdentity(): SessionIdentity?

    /**
     * Synchronous read of the identity mirror. Use for cache keying from
     * non-suspend contexts; prefer [currentIdentity] whenever the caller can
     * suspend.
     */
    fun currentIdentitySnapshot(): SessionIdentity?

    /**
     * [currentIdentity] mapped to the [CacheIdentity] key the identity-keyed
     * caches (`TtlCache`, Room snapshots) use. The source-flow read matters
     * right after a switch: the mirror is updated asynchronously, and keying a
     * fetch against the lagging mirror would read/write the PREVIOUS
     * identity's cache entries in that window.
     * Returns [CacheIdentity.UNKNOWN] before login / after logout — nothing
     * cached under that key can leak across users, since no real identity
     * ever collides with it.
     */
    suspend fun cacheIdentity(): CacheIdentity

    /**
     * Synchronous [CacheIdentity] variant of [currentIdentitySnapshot] for
     * non-suspend callers (best-effort evictions). Mirror staleness is benign
     * there: identity switches clear the caches wholesale via [transitions]
     * regardless of which identity an entry was keyed under.
     */
    fun cacheIdentitySnapshot(): CacheIdentity
}

/**
 * The fully established `(serverId, userId)` an identity consumer should key
 * against. One half being absent is represented by a null identity (see the
 * [SessionIdentityProvider] classifiers), not by a partial value.
 *
 * Moved verbatim out of jvmShared `HomeSession.kt` (wave 15B) — the
 * classifier types must be visible to commonMain [SessionCacheRegistry]
 * subscribers; the classifying HomeSession itself stays JVM-bound.
 */
data class SessionIdentity(val serverId: String, val userId: String)

/**
 * A single observed identity change, classified between two consecutive
 * stable identities.
 *
 * [previousIdentity] is `null` only on [SignedIn] (there was no previous
 * identity). [SignedOut] carries the identity that was cleared — consumers
 * like `MediaRepositoryImpl`'s SWR privacy clear need it after the session
 * flow has already moved to `null`.
 *
 * Moved verbatim out of jvmShared `HomeSession.kt` (wave 15B).
 */
sealed interface HomeSessionTransition {
    /** The identity in effect before this transition; `null` on [SignedIn]. */
    val previousIdentity: SessionIdentity?

    /** An identity was established where none was before (login / restore). */
    data object SignedIn : HomeSessionTransition {
        override val previousIdentity: SessionIdentity? get() = null
    }

    /** A different user on the SAME server became active. */
    data class UserSwitched(override val previousIdentity: SessionIdentity) : HomeSessionTransition

    /** A different server (and therefore user) became active. */
    data class ServerSwitched(override val previousIdentity: SessionIdentity) : HomeSessionTransition

    /** The identity was cleared (logout / disconnect). */
    data class SignedOut(override val previousIdentity: SessionIdentity?) : HomeSessionTransition
}
