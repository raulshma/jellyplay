package com.raulshma.jellyplay.core.data.repository

/**
 * The sync reservation — the `jpsync.*` key grammar (GLOSSARY.md's "Sync
 * reservation"), owned HERE and nowhere else: every mirror prefix, per-user
 * cursor pref, per-namespace toggle and device-identity key the ADR 0011
 * engine's adapters and wiring persist is minted by the factories below,
 * never by a raw string literal at the call site.
 *
 * The invariant the grammar encodes: every key produced here is DEVICE-LOCAL
 * by construction. The prefs adapter drops anything answering true to
 * [isReserved] from its synced set in BOTH directions (outbound snapshot,
 * inbound apply/delete/mark), so mirror state, cursors, toggles and identity
 * can never feed back through sync — a server row under `jpsync.*` (stale or
 * hostile) is inert on every device. That exclusion is prefix matching on
 * [RESERVED_PREFIX], which is exactly why the grammar needs one owner: a
 * hand-built `jpsync.…` string that drifts from these factories either leaks
 * device-local state INTO sync or orphans its stored copy (readers read one
 * key name, the writer wrote another — the write lands but nothing finds it).
 *
 * STORED KEYS NEVER CHANGE SHAPE — these strings are persisted in user
 * DataStores across releases; a "renamed" key is data loss (the old copy
 * reads as never-synced and the state re-pushes, resurrects, or re-prompts).
 * The byte-exact shapes are what JpsyncReservationTest pins.
 *
 * Dependency-free by design — a pure string grammar, no DataStore, no
 * serialization — so every source set in the module can acquire keys through
 * it: commonMain's JellyPushRepository as much as the jvmShared adapters and
 * their DI wiring.
 */
object JpsyncReservation {

    /**
     * The whole reserved space. Every adapter drops keys under this prefix in
     * both directions — mirrors (`jpsync.mirror.*`), device identity
     * (`jpsync.device.*`), sync cursors (`jpsync.cursor.*`), per-namespace
     * toggles (`jpsync.ns.*`) — so server state can never write, and device
     * state can never sync, under a reserved name.
     */
    const val RESERVED_PREFIX = "jpsync."

    /**
     * The mirror space's root: `jpsync.mirror.`. The prefs adapter's mirror
     * lives directly under it (`jpsync.mirror.<key>`, no namespace segment —
     * that stored shape predates per-namespace mirroring and is frozen); every
     * namespace adapter's mirror rides [mirrorPrefix] — `jpsync.mirror.<ns>.`
     * — so one DataStore hosts all the mirrors while none of them ever sees
     * another's entries.
     */
    const val MIRROR_ROOT = "jpsync.mirror."

    /** True for every key no adapter may sync or adopt in either direction. */
    fun isReserved(key: String): Boolean = key.startsWith(RESERVED_PREFIX)

    /**
     * The reserved `jpsync.mirror.<ns>.` prefix one namespace adapter's
     * SyncMirror entries live under in the shared user-prefs DataStore.
     */
    fun mirrorPrefix(namespace: String): String = "$MIRROR_ROOT$namespace."

    /** A single mirror entry: `jpsync.mirror.<ns>.<key>` — the last-synced wire value's slot. */
    fun mirrorKey(namespace: String, key: String): String = mirrorPrefix(namespace) + key

    /**
     * The per-namespace selective-sync toggle: `jpsync.ns.enabled.<ns>`.
     * Missing key = enabled (the default); the `reader` namespace is the ONE
     * default-off toggle, decided by the wiring, not the grammar.
     */
    fun namespaceToggleKey(namespace: String): String = "jpsync.ns.enabled.$namespace"

    /**
     * The per-user delta/SSE resume cursor: `jpsync.cursor.<cursor>.<userId>`
     * — keyed by identity so two users sharing a device never read each
     * other's change-log position.
     */
    fun cursorKey(cursor: String, userId: String): String = "jpsync.cursor.$cursor.$userId"

    /** Whether settings sync is enabled on this device: `jpsync.device.sync_enabled`. */
    fun deviceSyncEnabledKey(): String = "jpsync.device.sync_enabled"

    /** The persistent per-device identity: `jpsync.device.id`. */
    fun deviceIdKey(): String = "jpsync.device.id"

    /** The push face's persisted distributor endpoint: `jpsync.device.push.endpoint`. */
    fun pushEndpointKey(): String = "jpsync.device.push.endpoint"
}
