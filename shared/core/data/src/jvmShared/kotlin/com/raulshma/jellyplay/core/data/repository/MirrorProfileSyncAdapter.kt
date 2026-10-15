package com.raulshma.jellyplay.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.json.JsonElement

/**
 * The jvmShared base half of [ProfileSyncAdapter] — the delegation seam
 * between an adapter and its [SyncMirror], extracted once (GLOSSARY.md's
 * "Sync adapter base"): the dirty/delete/markSynced verbs ARE the mirror's,
 * and [deleteRemote] is the shared clear-tail — the adapter's local removal
 * runs first, then the mirror entries go REGARDLESS of key shape (an
 * unparseable key must not wedge in the mirror forever re-reading as
 * deleted). The tombstone rule this base states once: a server `JsonNull`
 * reaches [ProfileSyncAdapter.applyRemote] (treated there like a delete),
 * an adopted delete routes through [deleteRemote].
 *
 * A concrete adapter implements [ProfileSyncAdapter.snapshot] and
 * [ProfileSyncAdapter.applyRemote], overrides [deleteLocally] where an
 * adopted tombstone has real local work, and nothing else — "one class over
 * a store" made literal. The key-agreement checks stay in each adapter's
 * applyRemote (they differ per key grammar); a JsonNull arriving there is
 * the defensive arm, handled per adapter exactly like a tombstone.
 *
 * [JellyPlayPreferencesSyncAdapter] is the deliberate shape-drift: its
 * mirror predates per-namespace prefixes (the frozen `jpsync.mirror.<key>`
 * root) and its tombstones adopt as resets (ADR 0011 §2), so it passes its
 * own [mirrorPrefix] and overrides [deleteRemote] whole. The interface
 * itself stays commonMain and unchanged.
 */
abstract class MirrorProfileSyncAdapter(
    final override val namespace: String,
    /** The mirror's persistence home — the SAME user-prefs store every adapter mirrors. */
    mirrorStore: DataStore<Preferences>,
    /**
     * The reserved prefix this namespace's mirror entries live under.
     * Default: the per-namespace `jpsync.mirror.<ns>.` prefix minted by
     * [JpsyncReservation.mirrorPrefix]; only the prefs adapter's frozen
     * stored shape departs from it.
     */
    mirrorPrefix: String = JpsyncReservation.mirrorPrefix(namespace),
    /** The adapter's exclusion rule, handed to the mirror (default: mirror everything). */
    shouldMirror: (String) -> Boolean = { true },
    /**
     * Whether this namespace's local deletions roam as outbound tombstones.
     * Default false — the SPI default — because for the value-only
     * namespaces (prefs, homelayout, integrations) a local key removal
     * means "reset to default", never "deleted everywhere" (ADR 0011 §2);
     * the tombstone-roaming adapters (books, search, cw, reader, itemprefs,
     * playlists) pass true and the base supplies the mirror delegation.
     */
    private val tombstonesRoam: Boolean = false,
) : ProfileSyncAdapter {

    protected val mirror = SyncMirror(mirrorStore, mirrorPrefix, shouldMirror)

    override suspend fun dirtyValues(current: Map<String, JsonElement>): Map<String, JsonElement> =
        mirror.dirtyValues(current)

    override suspend fun markSynced(values: Map<String, JsonElement>) = mirror.markSynced(values)

    // The tombstone face is the ONE deliberate fork: roaming namespaces
    // delegate to the mirror, value-only ones keep the SPI default — a
    // mirror-backed override would push their resets as tombstones.
    override suspend fun deletedKeys(current: Map<String, JsonElement>): Set<String> =
        if (tombstonesRoam) mirror.deletedKeys(current) else emptySet()

    /**
     * The adopted-tombstone tail: [deleteLocally] does the adapter's real
     * work (parsed keys only), the mirror entries go regardless of key shape.
     */
    override suspend fun deleteRemote(keys: Set<String>) {
        deleteLocally(keys)
        mirror.clear(keys)
    }

    /**
     * The local half of an adopted tombstone: remove whatever [keys] name
     * locally. Default no-op — a namespace whose tombstones carry no local
     * removal keeps exactly this (and one whose tombstones never roam at all
     * overrides [deleteRemote] whole, as prefs and the home layout do).
     */
    protected open suspend fun deleteLocally(keys: Set<String>) {}
}
