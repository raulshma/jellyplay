package com.raulshma.jellyplay.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.database.dao.SearchHistoryDao
import com.raulshma.jellyplay.core.database.entity.SearchHistoryEntity
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * The [ProfileSyncAdapter] that roams the search history (ADR 0011's state
 * surfaces): namespace [NAMESPACE], one key per query — `sha1(query)` (the
 * raw query text would leak every search term into a route-logged URL
 * pattern and blow the 64KB namespace on long queries; the fixed-length hash
 * is the key the server sees, the query itself rides the value) — and a
 * value carrying `{query, searchedAt}` as an opaque JSON object.
 *
 * The history lives in the Room `search_history` store (per-user, capped at
 * [SearchHistoryDao] `insertAndEvict`'s 50 — the client cap is kept, sync
 * never grows it); the mirror — the last-synced snapshot dirty detection
 * diffs against — rides the user-prefs DataStore under the reserved
 * `jpsync.mirror.search.` prefix (the shared [SyncMirror] idiom; the
 * `jpsync.` reservation keeps every mirror key out of every adapter's synced
 * set, so state can't feed back).
 *
 * DELETES ROAM both ways: a local removal (single delete or clear-all)
 * leaves the key in the mirror but out of [snapshot] — [deletedKeys] reports
 * it and the engine pushes a null-value (tombstone) write; a server tombstone
 * arrives as [deleteRemote], which removes the local rows hashing to the key
 * AND the mirror entries so the adopted delete never resurrects or re-reads
 * as a local edit.
 *
 * Rows belong to the ACTIVE user ([userIdProvider] — the history is
 * user-scoped like the Room table): with no session the adapter reads empty
 * and writes nothing (the engine is identity-reset anyway).
 *
 * Remote adoption is UPSERT-AT-QUERY: the `(query, userId)` unique index
 * dedupes, `insertAndEvict` re-stamps the adopted row to the head of the
 * recency order, and the 50-cap evicts the tail — exactly the local write
 * path, so synced adoption can never grow the store past its cap.
 */
class JellyPlaySearchHistorySyncAdapter(
    private val historyDao: SearchHistoryDao,
    /** The mirror's persistence home — the SAME user-prefs store the other adapters mirror, under a reserved prefix. */
    private val mirrorStore: DataStore<Preferences>,
    /** The active session's user id (null = signed out — the store reads empty and stays untouched). */
    private val userIdProvider: suspend () -> String?,
    override val namespace: String = NAMESPACE,
) : ProfileSyncAdapter {

    private val mirror = SyncMirror(mirrorStore, JpsyncReservation.mirrorPrefix(NAMESPACE))

    // ------------------------------------------------------------------
    // key <-> query: the fixed-length sha1 hex of the query text. The hash
    // is deterministic, so every adapter re-derives the same key for the
    // same query; a key that decodes to no current row is simply absent.
    // ------------------------------------------------------------------

    private fun keyOf(entity: SearchHistoryEntity) = sha1Hex(entity.query)

    private fun valueOf(entity: SearchHistoryEntity): JsonObject = buildJsonObject {
        put("query", entity.query)
        put("searchedAt", entity.searchedAt)
    }

    override suspend fun snapshot(): Map<String, JsonElement> {
        val userId = userIdProvider() ?: return emptyMap()
        return historyDao.getRecent(userId, CAP).first().associate { keyOf(it) to valueOf(it) }
    }

    override suspend fun dirtyValues(current: Map<String, JsonElement>): Map<String, JsonElement> =
        mirror.dirtyValues(current)

    override suspend fun deletedKeys(): Set<String> = mirror.deletedKeys(snapshot())

    override suspend fun applyRemote(entries: Map<String, JsonElement>) {
        val userId = userIdProvider() ?: return
        for ((key, value) in entries) {
            if (value is JsonNull) continue // defensive: tombstones route through [deleteRemote]
            val query = value.toQueryOrNull() ?: continue
            // The payload must agree with its own key — the key IS the
            // query's hash, so a mismatched pair is a garbage row, skipped
            // like any uncoercible value.
            if (key != sha1Hex(query)) continue
            historyDao.insertAndEvict(
                SearchHistoryEntity(
                    query = query,
                    userId = userId,
                    searchedAt = value.toSearchedAtOrNull(),
                ),
            )
        }
    }

    override suspend fun deleteRemote(keys: Set<String>) {
        if (keys.isEmpty()) return
        val userId = userIdProvider()
        if (userId != null) {
            val doomed = historyDao.getRecent(userId, CAP).first()
                .filter { keyOf(it) in keys }
            for (row in doomed) historyDao.deleteById(row.id)
        }
        // The mirror entries go REGARDLESS of key shape or session: an
        // unparseable key must not wedge in the mirror forever re-reading as
        // deleted.
        mirror.clear(keys)
    }

    override suspend fun markSynced(values: Map<String, JsonElement>) = mirror.markSynced(values)

    private fun JsonElement.toQueryOrNull(): String? = try {
        (jsonObject["query"] as? JsonPrimitive)?.content
    } catch (_: IllegalArgumentException) {
        null // not a JSON object — skip
    }

    private fun JsonElement.toSearchedAtOrNull(): Long = try {
        (jsonObject["searchedAt"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
    } catch (_: IllegalArgumentException) {
        0L
    }

    private fun sha1Hex(text: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private companion object {
        const val NAMESPACE = "search"

        /** The client's own history cap (the DAO's `insertAndEvict` default) — sync never grows the store. */
        const val CAP = 50
    }
}
