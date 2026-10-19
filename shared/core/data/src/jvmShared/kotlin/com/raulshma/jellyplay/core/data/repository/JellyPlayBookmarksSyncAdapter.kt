package com.raulshma.jellyplay.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.database.dao.BookBookmarkDao
import com.raulshma.jellyplay.core.database.dao.BookBookmarkJoinKey
import com.raulshma.jellyplay.core.database.entity.BookBookmarkEntity
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * The [ProfileSyncAdapter] that puts the reader's bookmarks onto the general
 * settings-sync protocol (ADR 0011's bookmarks migration): namespace
 * [NAMESPACE], one key per bookmark — `"{itemId}/{positionTicks}"`, the
 * reader's own toggle-match join key — and a value carrying the FULL local
 * payload including the EPUB CFI the dedicated bookmark route loses on the
 * wire (`{itemId, positionTicks, cfi, chapterLabel, createdAt}` as an opaque
 * JSON object; the server never interprets it).
 *
 * The bookmarks live in the Room `book_bookmarks` store (ADR 0003,
 * local-first, untouched); the mirror — the last-synced snapshot dirty
 * detection diffs against — rides the user-prefs DataStore under the reserved
 * `jpsync.mirror.books.` prefix (the shared [SyncMirror] idiom; the `jpsync.`
 * reservation keeps every mirror key out of both adapters' synced sets, so
 * state can't feed back).
 *
 * DELETES ROAM both ways: a local removal leaves the key in the mirror but
 * out of [snapshot] — [deletedKeys] reports it and the engine pushes a
 * null-value (tombstone) write; a server tombstone arrives as [deleteRemote],
 * which removes the Room rows at the join key AND the mirror entry so the
 * adopted delete never resurrects or re-reads as a local edit. The same
 * [deleteRemote] clears the mirror after an applied outbound tombstone.
 *
 * Remote adoption is REPLACE-NEWEST-AT-JOIN-KEY: rows are immutable once
 * created (createdAt is their LWW stamp), and two rows at one position are
 * "the same bookmark" — the pulled row replaces the newest local twin (the
 * reader's ambiguity rule), never accumulating duplicates.
 */
class JellyPlayBookmarksSyncAdapter(
    private val bookmarkDao: BookBookmarkDao,
    /** The mirror's persistence home — the SAME user-prefs store the prefs adapter mirrors, under a reserved prefix. */
    mirrorStore: DataStore<Preferences>,
    namespace: String = NAMESPACE,
) : MirrorProfileSyncAdapter(namespace, mirrorStore, tombstonesRoam = true) {

    // ------------------------------------------------------------------
    // key <-> (itemId, positionTicks) — the join key. Item ids are Jellyfin
    // guids (no '/'), position ticks are numeric, so the LAST '/' splits;
    // anything else is malformed and skipped (garbage/hostile-row rule).
    // ------------------------------------------------------------------

    private fun keyOf(entity: BookBookmarkEntity) = "${entity.itemId}/${entity.positionTicks}"

    private fun parseKey(key: String): Pair<String, Long>? {
        val itemId = key.substringBeforeLast('/')
        val ticks = key.substringAfterLast('/').toLongOrNull() ?: return null
        if (itemId.isEmpty() || itemId == key) return null
        return itemId to ticks
    }

    private fun valueOf(entity: BookBookmarkEntity): JsonObject = buildJsonObject {
        put("itemId", entity.itemId)
        put("positionTicks", entity.positionTicks)
        put("cfi", entity.cfi?.let { JsonPrimitive(it) } ?: JsonNull)
        put("chapterLabel", entity.chapterLabel)
        put("createdAt", entity.createdAt)
    }

    override suspend fun snapshot(): Map<String, JsonObject> =
        bookmarkDao.getAll().associate { keyOf(it) to valueOf(it) }

    override suspend fun applyRemote(entries: Map<String, JsonElement>) {
        if (entries.isEmpty()) return
        val deletes = mutableListOf<BookBookmarkJoinKey>()
        val upserts = mutableListOf<BookBookmarkEntity>()
        for ((key, value) in entries) {
            val location = parseKey(key) ?: continue
            if (value is JsonNull) {
                // Defensive: the engine routes tombstones through
                // [deleteRemote]; a null here is treated identically.
                deletes += BookBookmarkJoinKey(location.first, location.second)
                continue
            }
            val entity = value.toEntityOrNull(location) ?: continue
            // The join key IS the row identity in the sync model (the reader's
            // own toggle-match rule): every local twin at this position goes
            // (the batch's delete phase), the pulled row replaces them —
            // adoption never accumulates duplicates.
            deletes += BookBookmarkJoinKey(location.first, location.second)
            upserts += entity
        }
        if (deletes.isEmpty() && upserts.isEmpty()) return
        // ONE transaction for the whole pulled batch (the per-key
        // delete/upsert pairs were one transaction each). Deletes run before
        // upserts and keys are disjoint per join key, so the batch lands
        // exactly as the per-row loop did.
        bookmarkDao.adoptBookmarks(deletes, upserts)
    }

    override suspend fun deleteLocally(keys: Set<String>) {
        for (key in keys) {
            parseKey(key)?.let { (itemId, ticks) -> bookmarkDao.deleteAtPosition(itemId, ticks) }
        }
    }

    /**
     * Decodes a remote value into the row it describes, or null when the
     * payload is malformed — an uncoercible remote value skips the key
     * entirely (the local state survives a garbage row; the same stance as
     * the prefs adapter's coercion rule).
     */
    private fun JsonElement.toEntityOrNull(location: Pair<String, Long>): BookBookmarkEntity? = try {
        val obj = jsonObject
        val itemId = obj["itemId"]?.let { (it as? JsonPrimitive)?.content } ?: return null
        val ticks = obj["positionTicks"]?.let { (it as? JsonPrimitive)?.content?.toLongOrNull() } ?: return null
        val entity = BookBookmarkEntity(
            itemId = itemId,
            positionTicks = ticks,
            cfi = (obj["cfi"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content,
            chapterLabel = (obj["chapterLabel"] as? JsonPrimitive)?.content ?: "",
            createdAt = (obj["createdAt"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L,
        )
        // The payload must agree with its own key — a mismatched pair is a
        // garbage row, skipped like any uncoercible value.
        entity.takeIf { it.itemId == location.first && it.positionTicks == location.second }
    } catch (_: IllegalArgumentException) {
        null // not a JSON object — skip
    }

    private companion object {
        const val NAMESPACE = "books"
    }
}
