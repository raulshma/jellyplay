package com.raulshma.jellyplay.core.data.concurrency

import java.util.concurrent.atomic.AtomicBoolean

/**
 * One lazy-staleness marker: the "announced user-data change" → "next read
 * fetches fresh" ladder behind a single boolean, so a read group can defer
 * the cost of an invalidation until somebody actually reads it.
 *
 * The rule, in prose:
 *  1. **[arm] on announce** — a confirmed own-write (played/favorite flip, a
 *     delivered STOP report, the outbox drain) marks the group stale instead
 *     of eagerly clearing its cache. Idempotent, so a burst of announces
 *     collapses into one refetch, and zero refetches happen while nobody
 *     reads the group. (A consumer MAY arm eagerly, riding an unconditional
 *     eviction the write path already runs — the gap-group riders in
 *     `MediaRepositoryImpl` do, accepting one redundant forced read per
 *     failed/unconfirmed write.)
 *  2. **[consume] as a one-shot force** — the next read consumes the marker
 *     and spends it as an *effective* force that bypasses the cached
 *     payload. getAndSet (not a read-then-clear), so an announce that races
 *     the consuming read re-arms the marker for the NEXT read instead of
 *     being swallowed by this one.
 *  3. **a failed consuming read [rearm]s** — the marker must not die with
 *     the read that spent it: on a failed fetch (returned OR thrown — the
 *     thrown shape covers the caller's own cancellation) that read produced
 *     nothing, and the pre-announce cached payload would serve until the
 *     next announce or the TTL, exactly the window the marker exists to
 *     close.
 *  4. **[reset] on identity switch** — the marker is armed by the PREVIOUS
 *     user's confirmed writes; without a reset it survives the switch and
 *     burns the next user's first read as one redundant force.
 *
 * This is the #157 home-sections rule (`MediaRepositoryImpl`'s
 * `homeSectionsStale`, previously a bare `AtomicBoolean`) lifted behind a
 * type so every read group that an announce can stale takes the same shape.
 * The two bug classes that shaped the ladder, both fixed once for home
 * sections and since recurring wherever the rule was missing or
 * half-implemented:
 *  - a consumed marker dying with a failed/cancelled read, stranding the
 *    pre-announce payload (1ba22d962 "preserve invalidated reads across
 *    failed fetches"); and
 *  - a consumed marker that refetched the in-memory cache but did not
 *    propagate its force past the repository boundary into the network
 *    layer's nested sub-call caches (53b90d228 "propagate consumed
 *    staleness to network reads").
 *
 * The ladder's steps 2-3 are choreography around this type, not part of it:
 * the consumer (see `MediaRepositoryImpl.staleAwareRead`) turns [consume]
 * into an effective force handed to the read (so nested caches are bypassed
 * too) and hands [rearm] both failure shapes.
 *
 * Server WebSocket pushes do NOT arm these markers — their consumers serve
 * them live; only confirmed own-writes (which may never echo on the socket)
 * do.
 */
class AnnouncedStaleness {

    private val stale = AtomicBoolean(false)

    /** Marks the group stale (idempotent — a burst of announces collapses to one refetch). */
    fun arm() {
        stale.set(true)
    }

    /**
     * One-shot: returns true exactly once per arm, false when the group is
     * not stale. getAndSet (not a read-then-clear), so an announce racing
     * this consume re-arms the marker for the NEXT read instead of being
     * swallowed by this one.
     */
    fun consume(): Boolean = stale.getAndSet(false)

    /**
     * Restores staleness after a consuming read failed — only meaningful
     * post-[consume]. Simply sets the marker back: the write is idempotent,
     * and an announce that raced the failed read may already have re-armed
     * it.
     */
    fun rearm() {
        stale.set(true)
    }

    /** Clears the marker — the identity-switch reaction (the previous user's writes armed it). */
    fun reset() {
        stale.set(false)
    }
}
