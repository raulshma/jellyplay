package com.raulshma.jellyplay.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * The [ProfileSyncAdapter] that roams continue-watching removals (ADR 0011's
 * state surfaces): namespace [NAMESPACE], one key per hidden item —
 * `"hidden/{itemId}"`, the plan's `cw/hidden/{itemId}` surface — and a
 * `true` primitive value (the key carries the whole payload; the value only
 * marks presence, opaque to the server like every synced value).
 *
 * The hidden set is the client-side overlay the home pipelines already filter
 * on ([HomeDiscoveryStore.hiddenCwItemIds] → `HomeSectionQuery` → the
 * HomeSectionsFetcher/Assembler Continue Watching + Next Up rows) and the
 * details screen's "Hide from continue watching" action writes — this adapter
 * adds the SYNC half only, no new local state: native Jellyfin data stays
 * untouched (augment-native), and the "restore" list in Home settings reads
 * the same set.
 *
 * The store's read projection is the user-namespaced
 * [HomeDiscoveryStore.homeDiscovery] StateFlow, so the synced set follows the
 * active user. The mirror — the last-synced snapshot dirty detection diffs
 * against — rides the user-prefs DataStore under the reserved
 * `jpsync.mirror.cw.` prefix (the shared [SyncMirror] idiom; the `jpsync.`
 * reservation keeps every mirror key out of every adapter's synced set).
 *
 * DELETES ROAM both ways: a local un-hide (the settings restore list) leaves
 * the key in the mirror but out of [snapshot] — [deletedKeys] reports it and
 * the engine pushes a null-value (tombstone) write; a server tombstone
 * arrives as [deleteRemote], which removes the itemId from the local set AND
 * the mirror entries so the adopted delete never resurrects or re-reads as a
 * local edit.
 *
 * NOTE: the read projection is a StateFlow snapshot, so a set write lands in
 * [snapshot] one dispatch after the DataStore edit — the background flush
 * triggers (app-background / reconnect / periodic) all read long after the
 * write settles; nothing here needs the write-adjacent freshness the Room
 * adapters get from direct reads.
 */
class JellyPlayCwSyncAdapter(
    private val homeDiscoveryStore: HomeDiscoveryStore,
    /** The mirror's persistence home — the SAME user-prefs store the other adapters mirror, under a reserved prefix. */
    private val mirrorStore: DataStore<Preferences>,
    override val namespace: String = NAMESPACE,
) : ProfileSyncAdapter {

    private val mirror = SyncMirror(mirrorStore, MIRROR_PREFIX)

    // ------------------------------------------------------------------
    // key <-> itemId: `"hidden/{itemId}"`. Item ids are Jellyfin guids (no
    // '/'), so the FIRST '/' after the literal prefix splits; anything else
    // is malformed and skipped (garbage/hostile-row rule).
    // ------------------------------------------------------------------

    private fun keyOf(itemId: String) = "$KEY_PREFIX$itemId"

    private fun parseKey(key: String): String? =
        key.takeIf { it.startsWith(KEY_PREFIX) }
            ?.substring(KEY_PREFIX.length)
            ?.takeIf { it.isNotEmpty() }

    private fun hiddenItemIds(): Set<String> = homeDiscoveryStore.homeDiscovery.value.hiddenCwItemIds

    override suspend fun snapshot(): Map<String, JsonElement> =
        hiddenItemIds().associate { keyOf(it) to HIDDEN_VALUE }

    override suspend fun dirtyValues(current: Map<String, JsonElement>): Map<String, JsonElement> =
        mirror.dirtyValues(current)

    override suspend fun deletedKeys(): Set<String> = mirror.deletedKeys(snapshot())

    override suspend fun applyRemote(entries: Map<String, JsonElement>) {
        if (entries.isEmpty()) return
        val hides = mutableListOf<String>()
        val unhides = mutableListOf<String>()
        for ((key, value) in entries) {
            val itemId = parseKey(key) ?: continue
            if (value is JsonNull) {
                // Defensive: the engine routes tombstones through
                // [deleteRemote]; a null here is treated identically.
                unhides += itemId
            } else {
                hides += itemId
            }
        }
        if (hides.isEmpty() && unhides.isEmpty()) return
        // ONE read-modify-write per batch: the projection may lag the last
        // edit by a dispatch, so batching avoids reading a stale set between
        // two writes.
        homeDiscoveryStore.setHiddenCwItemIds(hiddenItemIds() - unhides.toSet() + hides.toSet())
    }

    override suspend fun deleteRemote(keys: Set<String>) {
        if (keys.isEmpty()) return
        val removals = keys.mapNotNull(::parseKey).toSet()
        if (removals.isNotEmpty()) {
            homeDiscoveryStore.setHiddenCwItemIds(hiddenItemIds() - removals)
        }
        // The mirror entries go REGARDLESS of key shape: an unparseable key
        // must not wedge in the mirror forever re-reading as deleted.
        mirror.clear(keys)
    }

    override suspend fun markSynced(values: Map<String, JsonElement>) = mirror.markSynced(values)

    private companion object {
        const val NAMESPACE = "cw"
        const val KEY_PREFIX = "hidden/"
        val HIDDEN_VALUE: JsonElement = JsonPrimitive(true)
        const val MIRROR_PREFIX = "jpsync.mirror.cw."
    }
}
