package com.raulshma.jellyplay.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.data.SliceJson
import com.raulshma.jellyplay.core.data.entityFrom
import com.raulshma.jellyplay.core.data.parseSlashKey
import com.raulshma.jellyplay.core.data.toPayload
import com.raulshma.jellyplay.core.data.toPayloadOrNull
import com.raulshma.jellyplay.core.database.dao.ItemPlaybackPreferenceDao
import com.raulshma.jellyplay.core.database.entity.ItemPlaybackPreferenceEntity
import com.raulshma.jellyplay.core.model.ItemPlaybackPreferencePayload
import com.raulshma.jellyplay.core.model.PlaybackPrefScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.encodeToJsonElement

/**
 * The [ProfileSyncAdapter] that backs the per-item/per-series playback
 * preferences up onto the general settings-sync protocol (the settings-backup
 * wave's `itemprefs` namespace): one key per row — `"{scope}/{key}"` (the
 * [com.raulshma.jellyplay.core.model.PlaybackPrefScope] name + the item or
 * series id) — and a value carrying the row's full [ItemPlaybackPreferencePayload]
 * (an opaque JSON object to the server; install-local Room ids omitted).
 *
 * The rows live in the Room `item_playback_preferences` store (local-first,
 * untouched — sync never blocks a local op and every sync failure is the
 * engine's ordinary retry, never a playback error); the mirror — the
 * last-synced snapshot dirty detection diffs against — rides the user-prefs
 * DataStore under the reserved `jpsync.mirror.itemprefs.` prefix (the shared
 * [SyncMirror] idiom; the `jpsync.` reservation keeps every mirror key out of
 * every adapter's synced set, so state can't feed back).
 *
 * THE ROAM CAP: [snapshot] publishes only the [ItemPlaybackPreferenceRoamCap.CAP]
 * most-recent rows by `updatedAt` (the Room store itself stays unbounded — the
 * cap bounds the SYNC SET, so at most [ItemPlaybackPreferenceRoamCap.CAP] keys
 * can ever push). A row that falls out of the
 * top-[ItemPlaybackPreferenceRoamCap.CAP] — deleted, or aged out by newer
 * writes — leaves its mirror entry orphaned and therefore reads as a LOCAL
 * DELETE: [deletedKeys] reports it and the engine pushes a tombstone, so the
 * aged-out preference stops roaming on every device too. That is intended and
 * documented behavior: the roaming set is exactly the cap-sized recent
 * window, nothing outside it.
 *
 * DELETES ROAM both ways: a local removal leaves the key in the mirror but
 * out of [snapshot] — [deletedKeys] reports it and the engine pushes a
 * null-value (tombstone) write; a server tombstone arrives as [deleteRemote],
 * which removes the Room row AND the mirror entry so the adopted delete never
 * resurrects or re-reads as a local edit.
 *
 * Remote adoption is REPLACE-AT-KEY: the `(scope, key)` unique index makes
 * the upsert a true replace, and a row is only adopted when a LOCAL write
 * could have produced it — the payload must agree with its own key (a
 * mismatched pair is a garbage row, skipped like any uncoercible value), the
 * scope must be a [PlaybackPrefScope] entry name (anything else is hostile or
 * unknown-version garbage), and the repository's local-write invariant (see
 * ItemPlaybackPreferenceRepositoryImpl.save: pinning a subtitle language
 * clears the disabled intent) is re-imposed on the upsert mapping. A refused
 * row is skipped AND its mirror entry cleared, so refused garbage can never
 * wedge in the mirror re-reading as a locally-deleted key.
 */
class JellyPlayItemPrefsSyncAdapter(
    private val dao: ItemPlaybackPreferenceDao,
    /** The mirror's persistence home — the SAME user-prefs store the other adapters mirror, under a reserved prefix. */
    private val mirrorStore: DataStore<Preferences>,
    override val namespace: String = NAMESPACE,
) : ProfileSyncAdapter {

    // encodeDefaults: the payload always carries every column, so a receiving
    // device sees explicit nulls (not merely absent fields) — the codec stance
    // lives in [SliceJson], shared with the backup slice source.

    private val mirror = SyncMirror(mirrorStore, JpsyncReservation.mirrorPrefix(NAMESPACE))

    // ------------------------------------------------------------------
    // key <-> (scope, key): `"{scope}/{key}"`. The scope is the enum name
    // (no '/'), the row key a Jellyfin item/series id (no '/'), so the FIRST
    // '/' splits; anything else is malformed and skipped (the
    // garbage/hostile-row rule) — [parseSlashKey], shared with the backup
    // slice source.
    // ------------------------------------------------------------------

    private fun keyOf(entity: ItemPlaybackPreferenceEntity) = "${entity.scope}/${entity.key}"

    private fun valueOf(entity: ItemPlaybackPreferenceEntity): JsonElement =
        SliceJson.encodeToJsonElement(entity.toPayload())

    override suspend fun snapshot(): Map<String, JsonElement> =
        // getAll is newest-write first: the head of the list IS the roam cap.
        dao.getAll().take(ItemPlaybackPreferenceRoamCap.CAP).associate { keyOf(it) to valueOf(it) }

    override suspend fun dirtyValues(current: Map<String, JsonElement>): Map<String, JsonElement> =
        mirror.dirtyValues(current)

    override suspend fun deletedKeys(): Set<String> = mirror.deletedKeys(snapshot())

    override suspend fun applyRemote(entries: Map<String, JsonElement>) {
        for ((key, value) in entries) {
            if (value is JsonNull) {
                // Defensive: the engine routes tombstones through
                // [deleteRemote]; a null here is treated identically.
                parseSlashKey(key)?.let { (scope, rowKey) -> dao.deleteByKey(scope, rowKey) }
                continue
            }
            if (!adopt(key, value)) {
                // A REFUSED row (see [adopt]) must not sit in the mirror
                // re-reading as a locally-deleted key — the same
                // "an unparseable key must not wedge in the mirror" rule the
                // [deleteRemote] clear-tail applies.
                mirror.clear(setOf(key))
            }
        }
    }

    /**
     * Adopts one remote row, or reports it REFUSED (false): an unparseable
     * key, a non-object value, a payload disagreeing with its own key, or a
     * scope that is not a [PlaybackPrefScope] entry name — hostile or
     * unknown-version garbage local writes can never produce (local rows are
     * only ever written from the enum). Refusal skips the write entirely —
     * the local state survives a garbage row (the prefs-adapter tolerance
     * rule).
     */
    private suspend fun adopt(key: String, value: JsonElement): Boolean {
        val (scope, rowKey) = parseSlashKey(key) ?: return false
        val payload = value.toPayloadOrNull(ItemPlaybackPreferencePayload.serializer()) ?: return false
        // The payload must agree with its own key — a mismatched pair is
        // a garbage row, skipped like any uncoercible value.
        if (payload.scope != scope || payload.key != rowKey) return false
        if (PlaybackPrefScope.entries.none { it.name == payload.scope }) return false
        // [entityFrom] re-imposes the repository's local-write invariant
        // (pinning a subtitle language clears the disabled intent — see
        // ItemPlaybackPreferenceRepositoryImpl.save), so a remote row lands
        // exactly as a local write could have produced it.
        dao.upsert(entityFrom(payload, payload.scope, payload.key))
        return true
    }

    override suspend fun deleteRemote(keys: Set<String>) {
        if (keys.isEmpty()) return
        for (key in keys) {
            parseSlashKey(key)?.let { (scope, rowKey) -> dao.deleteByKey(scope, rowKey) }
        }
        // The mirror entries go REGARDLESS of key shape: an unparseable key
        // must not wedge in the mirror forever re-reading as deleted.
        mirror.clear(keys)
    }

    override suspend fun markSynced(values: Map<String, JsonElement>) = mirror.markSynced(values)

    private companion object {
        const val NAMESPACE = "itemprefs"
    }
}

/**
 * The itemprefs roam cap — the sync set is the [CAP] most-recent rows by
 * `updatedAt`; the Room store stays unbounded (see
 * [JellyPlayItemPrefsSyncAdapter]'s class KDoc for the
 * aged-out-roams-as-delete consequence). Internal so the adapter and its
 * tests source the one value.
 */
internal object ItemPlaybackPreferenceRoamCap {
    const val CAP = 100
}
