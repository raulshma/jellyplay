package com.raulshma.jellyplay.core.data.concurrency

import java.util.concurrent.atomic.AtomicBoolean

/**
 * One announced-staleness READ GROUP: the lazy-staleness marker AND the
 * read choreography that spends it, behind one type — "announced user-data
 * change" → "next read fetches fresh", so a group can defer the cost of an
 * invalidation until somebody actually reads it.
 *
 * Deepening of the former [MediaRepositoryImpl]-internal split (a bare
 * `AnnouncedStaleness` marker + a private `staleAwareRead` helper that
 * choreographed it): the ladder's steps only make sense together — the
 * consume/re-arm pairing IS the bug fix — so this type owns both, and the
 * repository's three read wrappers, three arm sites and identity reset
 * become one-liners over it (see [StaleReadGroups] for the fan-out).
 *
 * The rule, in prose:
 *  1. **[arm] on announce** — a confirmed own-write (played/favorite flip, a
 *     delivered STOP report, the outbox drain) marks the group stale instead
 *     of eagerly clearing its cache. Idempotent, so a burst of announces
 *     collapses into one refetch, and zero refetches happen while nobody
 *     reads the group. (A consumer MAY also arm eagerly, riding an
 *     unconditional eviction the write path already runs — the gap-group
 *     riders in `MediaRepositoryImpl` do, accepting one redundant forced
 *     read per failed/unconfirmed write.)
 *  2. **[consume] as a one-shot force** — the next read consumes the marker
 *     and spends it as an *effective* force that bypasses the cached
 *     payload. getAndSet (not a read-then-clear), so an announce that races
 *     the consuming read re-arms the marker for the NEXT read instead of
 *     being swallowed by this one.
 *  3. **a failed consuming read re-arms** — inside [staleAwareRead]: the
 *     marker must not die with the read that spent it. On a failed fetch
 *     (returned OR thrown — the thrown shape covers the caller's own
 *     cancellation) that read produced nothing, and the pre-announce cached
 *     payload would serve until the next announce or the TTL, exactly the
 *     window the marker exists to close.
 *  4. **[reset] on identity switch** — the marker is armed by the PREVIOUS
 *     user's confirmed writes; without a reset it survives the switch and
 *     burns the next user's first read as one redundant force.
 *
 * This is the #157 home-sections rule (`MediaRepositoryImpl`'s
 * `homeSectionsStale`, originally a bare `AtomicBoolean`) lifted behind a
 * type so every read group that an announce can stale takes the same shape.
 * The two bug classes that shaped the ladder, both fixed once for home
 * sections and since recurring wherever the rule was missing or
 * half-implemented (both now pinned by direct tests on [staleAwareRead]):
 *  - a consumed marker dying with a failed/cancelled read, stranding the
 *    pre-announce payload (1ba22d962 "preserve invalidated reads across
 *    failed fetches"); and
 *  - a consumed marker that refetched the in-memory cache but did not
 *    propagate its force past the repository boundary into the network
 *    layer's nested sub-call caches (53b90d228 "propagate consumed
 *    staleness to network reads") — [staleAwareRead] hands the read the
 *    EFFECTIVE force, not the caller's manual lever, for exactly this.
 *
 * Server WebSocket pushes do NOT arm these markers — their consumers serve
 * them live; only confirmed own-writes (which may never echo on the socket)
 * do.
 *
 * jvmShared (not commonMain): the marker is a `java.util.concurrent`
 * `AtomicBoolean`, and the consumers of this package
 * (`MediaRepositoryImpl`, `SingleFlightFetcher`) are jvmShared too — there
 * is no wasm consumer for the staleness ladder.
 */
internal class StaleReadGroup {

    private val stale = AtomicBoolean(false)

    /** Marks the group stale (idempotent — a burst of announces collapses to one refetch). */
    fun arm() {
        stale.set(true)
    }

    /**
     * One-shot: returns true exactly once per arm, false when the group is
     * not stale. getAndSet (not a read-then-clear), so an announce racing
     * this consume re-arms the marker for the NEXT read instead of being
     * swallowed by this one. Exposed for the concurrency suite's direct pin
     * of the ladder's atomic core — production code reaches it through
     * [staleAwareRead].
     */
    fun consume(): Boolean = stale.getAndSet(false)

    /**
     * Restores staleness after a consuming read failed — only meaningful
     * post-[consume], and [staleAwareRead] is the only production caller.
     * Simply sets the marker back: the write is idempotent, and an announce
     * that raced the failed read may already have re-armed it. Exposed for
     * the same direct-pin reason as [consume].
     */
    fun rearm() {
        stale.set(true)
    }

    /** Clears the marker — the identity-switch reaction (the previous user's writes armed it). */
    fun reset() {
        stale.set(false)
    }

    /**
     * The one choreography for a read of this group: [read] receives the
     * EFFECTIVE force — the caller's manual lever OR the consumed one-shot
     * marker — so a consumed marker propagates into the read's own eviction
     * and any nested fetch flag (the api client's sub-call caches), not just
     * the in-memory cache.
     *
     * The marker is consumed even by a manually forced read (that read is at
     * least as fresh as the announce, so leaving the marker armed would only
     * buy one redundant forced read later) and re-armed on BOTH failure
     * shapes: a returned [Result.failure] and a thrown failure, which covers
     * this caller's own cancellation — a consumed marker must not die with
     * the read that spent it, or the pre-announce cached payload would serve
     * until the next announce or the TTL.
     */
    suspend fun <T> staleAwareRead(
        force: Boolean,
        read: suspend (effectiveForce: Boolean) -> Result<T>,
    ): Result<T> {
        val stalenessConsumed = consume()
        val effectiveForce = force || stalenessConsumed
        return try {
            read(effectiveForce).also { result ->
                if (stalenessConsumed && result.isFailure) rearm()
            }
        } catch (t: Throwable) {
            if (stalenessConsumed) rearm()
            throw t
        }
    }
}

/**
 * The registry of one owner's [StaleReadGroup]s: the single place the
 * announce fan-out is spelled, so a new read group is ONE registration —
 * not an edit at every arm site (the drift this registry replaces: the
 * marker fan-out was copy-pasted at three sites in `MediaRepositoryImpl`,
 * each with its own hand-maintained group set).
 *
 * Two announce channels, matching the two group sets the arm sites express:
 *
 *  - **[announceUserDataWrite]** — "a user-data write/invalidation is being
 *    processed" (the mutation wrapper's pre-eviction, the cache-invalidation
 *    seam). Unconditional: the write may later fail or go unconfirmed. Arms
 *    only the groups registered with `ridesUserDataWrite = true` — the
 *    "gap" groups that ride the unconditional eviction the write path
 *    already runs, accepting one redundant forced read per failed or
 *    unconfirmed write.
 *  - **[announceConfirmedWrite]** — "an own-write was confirmed on the
 *    server" (the synthetic user-data-changed announce). Arms EVERY group:
 *    the scroll-sensitive groups (home sections) stale only for confirmed
 *    writes, and a confirmed write also satisfies the riders' channel.
 *
 * [resetAll] is the identity-switch reaction — every group's marker was
 * armed by the PREVIOUS user's writes.
 *
 * Register during construction only (the registry is published through the
 * owner's final fields, never mutated afterwards); the announce/reset
 * fan-outs are then plain lock-free reads over the frozen lists, matching
 * the `AtomicBoolean` markers they drive.
 */
internal class StaleReadGroups {

    private val groups = mutableListOf<StaleReadGroup>()

    // The gap-group riders: armed by EVERY user-data write/invalidation, not
    // just confirmed announces (see [announceUserDataWrite]).
    private val userDataWriteRiders = mutableListOf<StaleReadGroup>()

    /**
     * Creates a group and registers it with the fan-out below. Returns the
     * group so the owner keeps a named handle for its read wrappers
     * (`staleAwareRead`); [ridesUserDataWrite] selects the announce channels
     * that arm it (see the class KDoc).
     */
    fun register(ridesUserDataWrite: Boolean = false): StaleReadGroup =
        StaleReadGroup().also { group ->
            groups.add(group)
            if (ridesUserDataWrite) userDataWriteRiders.add(group)
        }

    /** Arms the [ridesUserDataWrite] riders — every user-data write/invalidation, confirmed or not. */
    fun announceUserDataWrite() {
        userDataWriteRiders.forEach { it.arm() }
    }

    /** Arms EVERY registered group — the confirmed own-write announce. */
    fun announceConfirmedWrite() {
        groups.forEach { it.arm() }
    }

    /** Clears every marker — the identity-switch reaction. */
    fun resetAll() {
        groups.forEach { it.reset() }
    }
}
