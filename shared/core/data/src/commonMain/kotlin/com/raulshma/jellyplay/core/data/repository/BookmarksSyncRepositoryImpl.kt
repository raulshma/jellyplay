package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.data.session.isAvailableNowOrProbe
import com.raulshma.jellyplay.core.database.dao.BookBookmarkDao
import com.raulshma.jellyplay.core.database.entity.BookBookmarkEntity
import com.raulshma.jellyplay.core.model.BookProgressPolicy
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.network.api.JellyPlayBookmark
import com.raulshma.jellyplay.core.network.api.JellyPlayBookmarkRequest
import com.raulshma.jellyplay.core.network.api.JellyPlayPluginApiClient
import kotlinx.coroutines.flow.first
import kotlin.math.roundToLong

/**
 * Ticks per second of the Jellyfin tick space [BookProgressPolicy] encodes
 * in (100-ns units, the `PlaybackPositionTicks` convention). The companion
 * plugin's `position` is seconds (double, stored opaquely per the wire
 * contract), so this constant is the WHOLE local<->wire conversion:
 * `seconds = ticks / 10_000_000`, `ticks = round(seconds * 10_000_000)`.
 * It round-trips every [BookProgressPolicy] output exactly — doubles carry
 * tick-magnitude integers (max ~1e8 in practice) without loss — and keeps
 * the plugin payload in honest Jellyfin-tick-derived seconds.
 */
internal const val BOOKMARK_SYNC_TICKS_PER_SECOND: Long = 10_000_000L

/** The ONE ticks->seconds mapping (see [BOOKMARK_SYNC_TICKS_PER_SECOND]). */
internal fun ticksToPositionSeconds(ticks: Long): Double = ticks / BOOKMARK_SYNC_TICKS_PER_SECOND.toDouble()

/** The ONE seconds->ticks mapping; [round] recovers the exact tick value. */
internal fun positionSecondsToTicks(seconds: Double): Long =
    (seconds * BOOKMARK_SYNC_TICKS_PER_SECOND.toDouble()).roundToLong()

/**
 * Room-backed [BookmarksSyncRepository]: mirrors the `book_bookmarks` rows to
 * the companion plugin and back (commonMain like the marks repo it shares the
 * DAO with). The wire row is strictly poorer than the local one — the plugin
 * stores `{id, position, chapterIndex, label, notes}` with no CFI and no
 * local ids — so the mappings are deliberately lossy and the JOIN KEY is the
 * converted position (position round-trips exactly; two rows at one position
 * are "the same bookmark", mirroring the reader's own toggle-match rule):
 *
 *  - position: [BookProgressPolicy] ticks <-> seconds via
 *    [ticksToPositionSeconds] / [positionSecondsToTicks] (the ONE conversion,
 *    pinned by tests);
 *  - label <-> chapterLabel: straight across;
 *  - notes: local bookmarks have no notes column (the Room schema is the
 *    source of truth), so pushes write `""` and pulled notes are dropped;
 *  - chapterIndex: local-free — pushed as null, dropped on pull;
 *  - CFI: wire-free — pushed rows lose it, pulled rows adopt `null` (they
 *    render and export as ordinary marks but are not CFI-jumpable — the
 *    reader's documented null-CFI reflowable case);
 *  - timestamps: server `createdAt`/`updatedAt` ride across as epoch millis;
 *    the LWW comparisons use local `createdAt` (local bookmarks are immutable
 *    once created, so it doubles as their "updatedAt") against server
 *    `updatedAt`. Clock skew between server and device bounds the accuracy of
 *    those comparisons; equal timestamps keep the LOCAL row (no oscillation —
 *    the same stance as the plugin's per-key settings rule).
 *
 * Stateless per call — every operation re-reads both sides and Room holds the
 * only state — so unlike [ProfileSyncRepository] there is no mirror to
 * corrupt and no mutex to serialize on. The gate follows the
 * [ProfileSyncRepository] idiom: a stale/unknown probe is refreshed once,
 * then the operation proceeds only when the store reads AVAILABLE with the
 * `bookmarks` feature. Every network failure (and every merge path) is a
 * silent skip: this runs fire-and-forget beside the reader UI, where a throw
 * would have nowhere to go.
 */
class BookmarksSyncRepositoryImpl(
    private val bookmarkDao: BookBookmarkDao,
    private val apiClient: JellyPlayPluginApiClient,
    private val statusStore: JellyPlayPluginStatusStore,
    /**
     * The per-feature gate seam (probe AND the user's toggle). Nullable with
     * default (direct-construction tests) — without it the probe alone
     * governs, the pre-toggle behavior.
     */
    private val featureGate: com.raulshma.jellyplay.core.data.session.JellyPlayFeatureGate? = null,
) : BookmarksSyncRepository {

    override suspend fun pullBookmarks(itemId: String) {
        if (!gate()) return
        val remote = apiClient.getBookmarks(itemId).getOrElse { return }
        val local = bookmarkDao.observeByItemId(itemId).first().associateBy { it.positionTicks }
        for (mark in remote) {
            val ticks = positionSecondsToTicks(mark.position)
            val existing = local[ticks]
            when {
                // Unknown locally — adopt as a new mark (server-wins for pull).
                existing == null -> bookmarkDao.upsert(mark.toEntity(itemId, ticks))
                // Known but the server row is strictly newer — replace the
                // local twin (fresh label; local id is install-local anyway).
                mark.updatedAt > existing.createdAt -> {
                    bookmarkDao.deleteById(existing.id)
                    bookmarkDao.upsert(mark.toEntity(itemId, ticks))
                }
                // Local is newer or equal — the offline-created mark survives.
                else -> Unit
            }
        }
    }

    override suspend fun pushBookmark(itemId: String, positionTicks: Long, chapterLabel: String) {
        if (!gate()) return
        // The row the caller just wrote — its createdAt is the LWW stamp.
        val local = bookmarkDao.observeByItemId(itemId).first()
            .filter { it.positionTicks == positionTicks }
            .maxByOrNull { it.createdAt } ?: return
        val remote = apiClient.getBookmarks(itemId).getOrElse { return }
        val twin = remote.firstOrNull { positionSecondsToTicks(it.position) == positionTicks }
        // LOCAL TIMESTAMPS WIN FOR PUSH: an equal-or-newer server row is a
        // fresher mirror of the same mark — pushing over it would resurrect
        // stale data. A strictly older twin is overwritten IN PLACE (its id
        // rides the request) so both devices keep one server row per mark.
        if (twin != null && twin.updatedAt >= local.createdAt) return
        apiClient.upsertBookmark(
            itemId = itemId,
            request = JellyPlayBookmarkRequest(
                id = twin?.id,
                position = ticksToPositionSeconds(local.positionTicks),
                chapterIndex = null,
                label = local.chapterLabel,
                notes = "",
            ),
        ) // result ignored — the next pull reconciles any server-side reject.
    }

    override suspend fun pushBookmarkDeleted(itemId: String, positionTicks: Long) {
        if (!gate()) return
        val remote = apiClient.getBookmarks(itemId).getOrElse { return }
        val twin = remote.firstOrNull { positionSecondsToTicks(it.position) == positionTicks } ?: return
        apiClient.deleteBookmark(itemId, twin.id) // 404 = already gone — equally fine.
    }

    /**
     * The ADR-0010 gate every plugin call sits behind: ensure the capability
     * probe is fresh (an UNKNOWN/UNAVAILABLE store re-probes once), then the
     * ONE gate seam — AVAILABLE + the `bookmarks` feature + the user's
     * per-feature toggle ([JellyPlayFeatureGate.isAvailableNow]; without the
     * seam the probe-only read keeps the pre-toggle behavior). Off = local
     * bookmarks keep working, the plugin mirror just skips.
     */
    private suspend fun gate(): Boolean {
        if (statusStore.status.value != JellyPlayPluginStatus.AVAILABLE) {
            statusStore.refresh()
        }
        return featureGate.isAvailableNowOrProbe(statusStore, JellyPlayPluginFeatures.Bookmarks)
    }

    private fun JellyPlayBookmark.toEntity(itemId: String, ticks: Long) = BookBookmarkEntity(
        itemId = itemId,
        positionTicks = ticks,
        // The wire carries no CFI: a pulled mark degrades to the position
        // encoding (renderable, exportable, not CFI-jumpable).
        cfi = null,
        chapterLabel = label,
        createdAt = createdAt,
    )
}
