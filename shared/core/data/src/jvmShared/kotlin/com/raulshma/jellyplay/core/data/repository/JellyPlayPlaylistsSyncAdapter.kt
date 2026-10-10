package com.raulshma.jellyplay.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.data.FAMILY_MOOD
import com.raulshma.jellyplay.core.data.FAMILY_MOODPREF
import com.raulshma.jellyplay.core.data.FAMILY_SMART
import com.raulshma.jellyplay.core.data.SliceJson
import com.raulshma.jellyplay.core.data.entityFrom
import com.raulshma.jellyplay.core.data.parseSlashKey
import com.raulshma.jellyplay.core.data.toPayload
import com.raulshma.jellyplay.core.data.toPayloadOrNull
import com.raulshma.jellyplay.core.database.dao.MoodPlaylistDao
import com.raulshma.jellyplay.core.database.dao.SmartPlaylistDao
import com.raulshma.jellyplay.core.database.entity.MoodPlaylistEntity
import com.raulshma.jellyplay.core.database.entity.MoodPlaylistPreferenceEntity
import com.raulshma.jellyplay.core.database.entity.SmartPlaylistEntity
import com.raulshma.jellyplay.core.model.MoodPlaylistPreferencePayload
import com.raulshma.jellyplay.core.model.MoodPlaylistPayload
import com.raulshma.jellyplay.core.model.SmartPlaylistPayload
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.encodeToJsonElement

/**
 * The [ProfileSyncAdapter] that backs the playlist DEFINITIONS up onto the
 * general settings-sync protocol (the settings-backup wave's `playlists`
 * namespace): three key families over the Room playlist stores —
 * `smart/{id}` for smart-playlist definitions ([SmartPlaylistPayload]),
 * `mood/{id}` for mood-playlist definitions ([MoodPlaylistPayload]), and
 * `moodpref/{playlistId}` for the per-playlist user preferences
 * ([MoodPlaylistPreferencePayload]). Definitions sync WHOLE ROWS: the
 * criteria/keyword blobs ride as opaque JSON strings and no cached item lists
 * exist anywhere (smart/mood playlists resolve their items per library at
 * query time — nothing else to sync).
 *
 * The rows live in the Room `smart_playlists` / `mood_playlists` /
 * `mood_playlist_preferences` stores (local-first, untouched); the mirror —
 * the last-synced snapshot dirty detection diffs against — rides the
 * user-prefs DataStore under the reserved `jpsync.mirror.playlists.` prefix
 * (the shared [SyncMirror] idiom; the `jpsync.` reservation keeps every mirror
 * key out of every adapter's synced set, so state can't feed back).
 *
 * DELETES ROAM both ways (a deleted playlist roams to every device): a local
 * removal leaves the key in the mirror but out of [snapshot] — [deletedKeys]
 * reports it and the engine pushes a null-value (tombstone) write; a server
 * tombstone arrives as [deleteRemote], which removes the Room rows (the
 * definition row, or the preference row for `moodpref/`) AND the mirror
 * entries so the adopted delete never resurrects or re-reads as a local edit.
 * A mood playlist's removal and its preference row's removal are independent
 * keys: each roams its own tombstone (a playlist without a preference row
 * simply has none).
 *
 * Remote adoption is REPLACE-AT-ID: the id IS the primary key, the REPLACE
 * insert overwrites any local twin, and the payload must agree with its own
 * key (a mismatched pair is a garbage row, skipped like any uncoercible
 * value). A refused row is skipped AND its mirror entry cleared, so refused
 * garbage can never wedge in the mirror re-reading as a locally-deleted key.
 * An adopted definition decodes with the RECEIVING device's criteria
 * parser (the blobs are opaque strings), so an unknown criterion degrades
 * locally, never on the wire.
 */
class JellyPlayPlaylistsSyncAdapter(
    private val smartPlaylistDao: SmartPlaylistDao,
    private val moodPlaylistDao: MoodPlaylistDao,
    /** The mirror's persistence home — the SAME user-prefs store the other adapters mirror, under a reserved prefix. */
    private val mirrorStore: DataStore<Preferences>,
    override val namespace: String = NAMESPACE,
) : ProfileSyncAdapter {

    // encodeDefaults: the payload always carries every column, so a receiving
    // device sees explicit values (not merely absent fields) — the codec
    // stance lives in [SliceJson], shared with the backup slice source.

    private val mirror = SyncMirror(mirrorStore, JpsyncReservation.mirrorPrefix(NAMESPACE))

    // ------------------------------------------------------------------
    // key <-> id: `"{family}/{id}"` with family one of smart/ / mood/ /
    // moodpref/. Playlist ids are app-generated (uuid / preset slug, no
    // '/'), so the FIRST '/' splits family from id; anything else is
    // malformed and skipped (the garbage/hostile-row rule) — [parseSlashKey],
    // shared with the backup slice source.
    // ------------------------------------------------------------------

    private fun SmartPlaylistEntity.valueOf(): JsonElement = SliceJson.encodeToJsonElement(toPayload())

    private fun MoodPlaylistEntity.valueOf(): JsonElement = SliceJson.encodeToJsonElement(toPayload())

    private fun MoodPlaylistPreferenceEntity.valueOf(): JsonElement = SliceJson.encodeToJsonElement(toPayload())

    override suspend fun snapshot(): Map<String, JsonElement> = buildMap {
        smartPlaylistDao.getAll().forEach { put("smart/${it.id}", it.valueOf()) }
        moodPlaylistDao.getAll().forEach { put("mood/${it.id}", it.valueOf()) }
        moodPlaylistDao.getAllPreferences().forEach { put("moodpref/${it.playlistId}", it.valueOf()) }
    }

    override suspend fun dirtyValues(current: Map<String, JsonElement>): Map<String, JsonElement> =
        mirror.dirtyValues(current)

    override suspend fun deletedKeys(): Set<String> = mirror.deletedKeys(snapshot())

    override suspend fun applyRemote(entries: Map<String, JsonElement>) {
        for ((key, value) in entries) {
            if (value is JsonNull) {
                // Defensive: the engine routes tombstones through
                // [deleteRemote]; a null here is treated identically.
                parseSlashKey(key)?.let { (family, id) -> deleteLocal(family, id) }
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
     * key, an uncoercible value, or a payload disagreeing with its own key —
     * a mismatched pair is a garbage row, skipped like any uncoercible value
     * (the local state survives a garbage row). Refusal skips the write
     * entirely; the caller clears the key's mirror entry.
     */
    private suspend fun adopt(key: String, value: JsonElement): Boolean {
        val (family, id) = parseSlashKey(key) ?: return false
        when (family) {
            FAMILY_SMART -> {
                val payload = value.toPayloadOrNull(SmartPlaylistPayload.serializer()) ?: return false
                if (payload.id != id) return false
                smartPlaylistDao.insert(entityFrom(payload))
            }

            FAMILY_MOOD -> {
                val payload = value.toPayloadOrNull(MoodPlaylistPayload.serializer()) ?: return false
                if (payload.id != id) return false
                moodPlaylistDao.insert(entityFrom(payload))
            }

            FAMILY_MOODPREF -> {
                val payload = value.toPayloadOrNull(MoodPlaylistPreferencePayload.serializer()) ?: return false
                if (payload.playlistId != id) return false
                moodPlaylistDao.upsertPreference(entityFrom(payload))
            }

            else -> return false
        }
        return true
    }

    override suspend fun deleteRemote(keys: Set<String>) {
        if (keys.isEmpty()) return
        for (key in keys) {
            parseSlashKey(key)?.let { (family, id) -> deleteLocal(family, id) }
        }
        // The mirror entries go REGARDLESS of key shape: an unparseable key
        // must not wedge in the mirror forever re-reading as deleted.
        mirror.clear(keys)
    }

    override suspend fun markSynced(values: Map<String, JsonElement>) = mirror.markSynced(values)

    private suspend fun deleteLocal(family: String, id: String) {
        when (family) {
            FAMILY_SMART -> smartPlaylistDao.deleteById(id)
            FAMILY_MOOD -> moodPlaylistDao.deleteById(id)
            FAMILY_MOODPREF -> moodPlaylistDao.deletePreferenceById(id)
        }
    }

    private companion object {
        const val NAMESPACE = "playlists"
    }
}
