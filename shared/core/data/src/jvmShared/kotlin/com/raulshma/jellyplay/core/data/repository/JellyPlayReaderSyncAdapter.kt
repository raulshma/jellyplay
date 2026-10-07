package com.raulshma.jellyplay.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.database.dao.BookAnnotationDao
import com.raulshma.jellyplay.core.database.entity.BookAnnotationEntity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * The [ProfileSyncAdapter] that backs the reader's annotations up onto the
 * general settings-sync protocol (ADR 0011's state surfaces, ADR 0003's
 * local-first semantics): namespace [NAMESPACE], one key per book —
 * `"ann/{itemId}"` (per-item keys bound the blast radius of the free-form
 * TEXT sizes; the `reader` namespace's server quota caps the whole backup) —
 * and a value carrying the book's FULL annotation list as a JSON array of
 * `{cfi, style, color, anchorText, note, chapterLabel, createdAt, updatedAt}`
 * objects (the export DTO's shape, install-local Room ids omitted).
 *
 * The annotations live in the Room `book_annotations` store (ADR 0003,
 * local-first, untouched — backup never blocks a local op and every sync
 * failure is the engine's ordinary retry, never a reader error); the mirror —
 * the last-synced snapshot dirty detection diffs against — rides the
 * user-prefs DataStore under the reserved `jpsync.mirror.reader.` prefix (the
 * shared [SyncMirror] idiom; the `jpsync.` reservation keeps every mirror key
 * out of every adapter's synced set).
 *
 * OPT-IN (default off): backup rides the namespace's own selective-sync
 * toggle — the wiring persists `jpsync.ns.enabled.reader` DEFAULTING OFF
 * (every other namespace defaults on), so the `reader` row in the sync
 * screen's namespace list IS the backup toggle: off parks this namespace
 * whole (no dirty collection, no push, no adopt), flipping it on runs one
 * cycle immediately, and a fresh device with it on restores the books'
 * annotation sets on the first pull. The adapter itself stays toggle-blind —
 * the engine's per-namespace gate is the ONE switch, honored at both faces.
 *
 * DELETES ROAM both ways: clearing a book's annotations locally leaves the
 * key in the mirror but out of [snapshot] — [deletedKeys] reports it and the
 * engine pushes a null-value (tombstone) write; a server tombstone arrives as
 * [deleteRemote], which removes the book's rows AND the mirror entries so the
 * adopted delete never resurrects or re-reads as a local edit.
 *
 * Remote adoption is REPLACE-AT-BOOK: the pulled array replaces the book's
 * whole local set (the value IS the book's annotation list — partial merge
 * would need an identity the wire does not carry; rows are recreated with
 * fresh install-local ids, exactly like the JSON import path).
 */
class JellyPlayReaderSyncAdapter(
    private val annotationDao: BookAnnotationDao,
    /** The mirror's persistence home — the SAME user-prefs store the other adapters mirror, under a reserved prefix. */
    private val mirrorStore: DataStore<Preferences>,
    override val namespace: String = NAMESPACE,
) : ProfileSyncAdapter {

    private val mirror = SyncMirror(mirrorStore, MIRROR_PREFIX)

    // ------------------------------------------------------------------
    // key <-> itemId: `"ann/{itemId}"`. Item ids are Jellyfin guids (no
    // '/'), so the FIRST '/' after the literal prefix splits; anything else
    // is malformed and skipped (garbage/hostile-row rule).
    // ------------------------------------------------------------------

    private fun keyOf(itemId: String) = "$KEY_PREFIX$itemId"

    private fun parseKey(key: String): String? =
        key.takeIf { it.startsWith(KEY_PREFIX) }
            ?.substring(KEY_PREFIX.length)
            ?.takeIf { it.isNotEmpty() }

    override suspend fun snapshot(): Map<String, JsonElement> =
        annotationDao.getAll()
            .groupBy { it.itemId }
            .mapKeys { (itemId, _) -> keyOf(itemId) }
            .mapValues { (_, rows) -> rows.toJsonArray() }

    override suspend fun dirtyValues(current: Map<String, JsonElement>): Map<String, JsonElement> =
        mirror.dirtyValues(current)

    override suspend fun deletedKeys(): Set<String> = mirror.deletedKeys(snapshot())

    override suspend fun applyRemote(entries: Map<String, JsonElement>) {
        for ((key, value) in entries) {
            val itemId = parseKey(key) ?: continue
            if (value is JsonNull) {
                // Defensive: the engine routes tombstones through
                // [deleteRemote]; a null here is treated identically.
                annotationDao.deleteByItemId(itemId)
                continue
            }
            val rows = value.toRowsOrNull(itemId) ?: continue
            // The pulled list replaces the book's whole local set.
            annotationDao.deleteByItemId(itemId)
            rows.forEach { annotationDao.upsert(it) }
        }
    }

    override suspend fun deleteRemote(keys: Set<String>) {
        if (keys.isEmpty()) return
        for (key in keys) {
            parseKey(key)?.let { annotationDao.deleteByItemId(it) }
        }
        // The mirror entries go REGARDLESS of key shape: an unparseable key
        // must not wedge in the mirror forever re-reading as deleted.
        mirror.clear(keys)
    }

    override suspend fun markSynced(values: Map<String, JsonElement>) = mirror.markSynced(values)

    // ------------------------------------------------------------------
    // value <-> rows: the export DTO's shape (install-local ids omitted);
    // style/color ride their raw enum names and decode defensively one
    // layer up (the repository's own corrupt-row stance). A value that is
    // not an array of objects is malformed and skips the key (the
    // prefs-adapter tolerance rule).
    // ------------------------------------------------------------------

    private fun BookAnnotationEntity.toJson(): JsonObject = buildJsonObject {
        put("cfi", cfi)
        put("style", style)
        put("color", color)
        put("anchorText", anchorText)
        note?.let { put("note", it) }
        put("chapterLabel", chapterLabel)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    private fun List<BookAnnotationEntity>.toJsonArray(): JsonArray =
        buildJsonArray { forEach { add(it.toJson()) } }

    private fun JsonElement.toRowsOrNull(itemId: String): List<BookAnnotationEntity>? = try {
        jsonArray.mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            val cfi = (obj["cfi"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            BookAnnotationEntity(
                itemId = itemId,
                cfi = cfi,
                style = (obj["style"] as? JsonPrimitive)?.content ?: "HIGHLIGHT",
                color = (obj["color"] as? JsonPrimitive)?.content ?: "YELLOW",
                anchorText = (obj["anchorText"] as? JsonPrimitive)?.content ?: "",
                note = (obj["note"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content,
                chapterLabel = (obj["chapterLabel"] as? JsonPrimitive)?.content ?: "",
                createdAt = (obj["createdAt"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L,
                updatedAt = (obj["updatedAt"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L,
            )
        }
    } catch (_: IllegalArgumentException) {
        null // not a JSON array — skip the key
    }

    private companion object {
        const val NAMESPACE = "reader"
        const val KEY_PREFIX = "ann/"
        const val MIRROR_PREFIX = "jpsync.mirror.reader."
    }
}
