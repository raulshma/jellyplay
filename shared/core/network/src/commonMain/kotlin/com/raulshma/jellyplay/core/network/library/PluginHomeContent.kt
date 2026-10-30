@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.CuratedHeroEntry
import com.raulshma.jellyplay.core.model.HomeFreshness
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.JellyPlayRowEntry
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.TtlCache
import com.raulshma.jellyplay.core.model.cacheThrough
import com.raulshma.jellyplay.core.model.descriptor
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.network.api.JellyPlayHeroCandidate
import com.raulshma.jellyplay.core.network.api.JellyPlayRowItem
import com.raulshma.jellyplay.core.network.api.JellyPlayRowResult

/**
 * Everything one plugin content source knows about itself — the seasonal row,
 * the admin-defined titled custom rows and the plugin-curated hero (the
 * `jellyplay` companion server plugin, ADR 0010) — as one module the
 * [HomeSectionsFetcher] composes. This is the fetcher's THIRD memo policy
 * moved beside its siblings: the WHAT gate is the capability registry (the
 * [JellyPlayHomeSectionSources] suspend gates — UNKNOWN probes once per
 * session, UNAVAILABLE is trusted), the TTL memos are THIS module's
 * ([homePluginRowCache] / [homeCuratedHeroCache], identity-scoped like every
 * fetcher sub-call memo), and every failure mode degrades silently — a
 * curated plugin row or hero must never fail home, and a plugin 404
 * legitimately means "no row this season". A cached value is the MAPPED
 * content (title + resolved entries), so a hit costs zero port calls.
 *
 * The two ports are the already-declared [JellyPlayHomeSectionSources]
 * (transport + the [JellyPlayHomeSectionSources.getItemsByIds] batched
 * library read the resolution arms share) — constructed through them, no
 * adapter line. The fetcher keeps the schedule (what launches when), the
 * per-row transports and its other two memo policies (the Jellyfin discover
 * generation memo, the Seerr TTL gate); this module owns the plugin family's
 * leaves: gate → memo → fetch → resolve → map.
 */
public class PluginHomeContent(
    /** The plugin transport; null behaves exactly like a gate reading false — zero port calls, zero rows. */
    private val jellyPlaySources: JellyPlayHomeSectionSources?,
    /** The atomic (server, user) identity the memos scope by; null normalizes to UNKNOWN pre-login. */
    private val cacheIdentity: () -> CacheIdentity?,
) {

    /**
     * Plugin rows (PLUGIN_ROW — the seasonal row today). Identity-scoped like
     * every entry (a user/server switch misses by construction) with the
     * sibling sub-call TTL ([HomeFreshness.NETWORK_SUBCALL_TTL_MS]): the row
     * carries resolved MediaItems with per-item UserData, so it rides the
     * same 2-minute memo the latest/similar rows ride and the same
     * user-data-write invalidation. Session-scoped by construction — the
     * module lives as long as the client single. The cached VALUE is the
     * mapped row (title + resolved entries), so a cache hit costs zero port
     * calls and the plugin's row title survives the memo.
     */
    private val homePluginRowCache = TtlCache<PluginRowValue>(ttlMs = HomeFreshness.NETWORK_SUBCALL_TTL_MS)

    /**
     * The plugin-curated hero: identity-scoped like every entry with the
     * sibling sub-call TTL — the cached VALUE is the resolved entry list (full
     * MediaItems with per-item UserData), so a hit costs zero port calls and a
     * watched/favorite write must not let the memo serve the pre-write hero
     * (cleared with the plugin-row memo — see [subcallCaches]).
     */
    private val homeCuratedHeroCache = TtlCache<List<CuratedHeroEntry>>(ttlMs = HomeFreshness.NETWORK_SUBCALL_TTL_MS)

    /**
     * This module's two memos, for the fetcher's cacheSpec list: the wholesale
     * drop ([HomeSectionsFetcher.invalidateCaches]) iterates every home
     * sub-call memo in one place, so a new plugin memo cannot be forgotten at
     * clear time (a forgotten clear serves stale badges for a TTL).
     */
    public val subcallCaches: List<TtlCache<*>> = listOf(
        homePluginRowCache,
        homeCuratedHeroCache,
    )

    /**
     * The seasonal row for the batch home fetch: gate → TTL memo →
     * [pluginRowCacheThrough] under the `seasonal` instance id. Empty list =
     * gated off / unconfigured / empty payload / failed — silently absent,
     * never an error (the assembler emits nothing for it).
     */
    public suspend fun fetchSeasonalPluginRow(force: Boolean, identity: CacheIdentity): List<HomeSection> {
        if (jellyPlaySources == null || !jellyPlaySources.seasonalRowsEnabled()) return emptyList()
        val section = pluginRowCacheThrough(
            cacheKey = "jellyplay_seasonal",
            force = force,
            identity = identity,
        ) { jellyPlaySources.getSeasonalRow(keyword = null) }
        return listOfNotNull(section)
    }

    /**
     * The admin-defined titled custom rows, in the plugin's configured order:
     * the enumerate endpoint (`GET jellyplay/rows`) lists the titles, each is
     * mapped through [fetchPluginCustomRow] (same gate, same TTL memo, same
     * batched id resolution). A catalog failure or empty catalog = no rows,
     * silently absent. Public for the leaf-test suite.
     */
    public suspend fun fetchTitledPluginRows(
        force: Boolean,
        identity: CacheIdentity?,
    ): List<HomeSection> {
        if (jellyPlaySources == null || !jellyPlaySources.customRowsEnabled()) return emptyList()
        val resolvedIdentity = identity ?: cacheIdentity() ?: CacheIdentity.UNKNOWN
        val catalog = runCatchingRethrowingCancellation {
            jellyPlaySources.getCustomRowCatalog().getOrNull()
        }.getOrNull() ?: return emptyList()
        return catalog.rows.mapNotNull { definition ->
            fetchPluginCustomRow(title = definition.title, force = force, identity = resolvedIdentity)
        }
    }

    /**
     * ONE admin-defined titled custom row, keyed by its exact title — mapped
     * through the enumerate catalog by [fetchTitledPluginRows] for the batch
     * home path; also public for direct single-row consumers (deep links).
     */
    public suspend fun fetchPluginCustomRow(
        title: String,
        force: Boolean = false,
        identity: CacheIdentity? = null,
    ): HomeSection? {
        if (jellyPlaySources == null || !jellyPlaySources.customRowsEnabled()) return null
        return pluginRowCacheThrough(
            cacheKey = "jellyplay_custom_$title",
            force = force,
            identity = identity ?: cacheIdentity() ?: CacheIdentity.UNKNOWN,
        ) { jellyPlaySources.getCustomRow(title = title) }
    }

    /**
     * The one cache-through + section-builder shape both plugin row reads
     * share: a miss fetches the raw payload, resolves every `localItemId`
     * through one batched [JellyPlayHomeSectionSources.getItemsByIds] call,
     * and maps to the PLUGIN_ROW [HomeSection] under [cacheKey]'s instance
     * id. A null payload (plugin 404 — no such row), an empty item list, or a
     * failed read yields null (no section); a resolution failure degrades the
     * affected entries to fallback tiles instead of dropping the row.
     */
    private suspend fun pluginRowCacheThrough(
        cacheKey: String,
        force: Boolean,
        identity: CacheIdentity,
        fetch: suspend () -> Result<JellyPlayRowResult?>,
    ): HomeSection? {
        val value = homePluginRowCache.cacheThrough(identity, cacheKey, force = force) {
            // A failed plugin read is a dropped row, not a fetch failure —
            // normalize to an empty success so the memo policy (cache-through,
            // drop-empty) stays the pinned-section one.
            val payload = runCatchingRethrowingCancellation { fetch().getOrNull() }
            Result.success(payload.getOrNull()?.let { row ->
                PluginRowValue(
                    title = row.title,
                    entries = resolvePluginRowEntries(row.items),
                )
            } ?: PluginRowValue.EMPTY)
        }.getOrDefault(PluginRowValue.EMPTY)
        if (value.entries.isEmpty()) return null
        val instanceId = cacheKey.removePrefix("jellyplay_")
        return HomeSection(
            id = HomeSectionType.PLUGIN_ROW.descriptor.idFor(instanceId),
            // The plugin's row title is authoritative (it names the curated
            // list, e.g. a Letterboxd collection); the descriptor's
            // displayName is only the degenerate fallback.
            title = value.title.ifBlank { HomeSectionType.PLUGIN_ROW.descriptor.displayName },
            type = HomeSectionType.PLUGIN_ROW,
            items = emptyList(),
            jellyPlayRowEntries = value.entries,
        )
    }

    /**
     * Maps the raw wire items to [JellyPlayRowEntry]s in payload order,
     * resolving the entries that carry a `localItemId` through ONE batched
     * ids read. Per-entry resolution failure (or an id the server no longer
     * knows) degrades that entry to a fallback tile — the row's shape is the
     * plugin's curation order, preserved end-to-end.
     */
    private suspend fun resolvePluginRowEntries(items: List<JellyPlayRowItem>): List<JellyPlayRowEntry> {
        if (items.isEmpty()) return emptyList()
        val localIds = items.mapNotNull { it.localItemId }.distinct()
        val resolved: Map<String, MediaItem> =
            if (localIds.isEmpty()) {
                emptyMap()
            } else {
                // Degrade, not fail: a lost ids read must not drop the row —
                // every entry falls back to its title+year tile (the same
                // policy as the classic-latest ids read in the client impl).
                jellyPlaySources?.getItemsByIds(localIds)
                    ?.getOrDefault(emptyList())
                    .orEmpty()
                    .associateBy { it.id }
            }
        return items.map { item ->
            JellyPlayRowEntry(
                title = item.title,
                year = item.year,
                localItem = item.localItemId?.let(resolved::get),
            )
        }
    }

    /**
     * The plugin-curated hero for the batch home fetch: gate → TTL memo →
     * resolve under the `jellyplay_hero` cache key. Empty list = gated off /
     * unconfigured / failed / nothing resolvable — silently absent, never an
     * error. Public for the leaf-test suite (the rows' convention).
     */
    public suspend fun fetchCuratedHero(
        force: Boolean = false,
        identity: CacheIdentity? = null,
    ): List<CuratedHeroEntry> {
        if (jellyPlaySources == null || !jellyPlaySources.curatedHeroEnabled()) return emptyList()
        return homeCuratedHeroCache.cacheThrough(
            identity ?: cacheIdentity() ?: CacheIdentity.UNKNOWN,
            "jellyplay_hero",
            force = force,
        ) {
            // A failed plugin read is an empty hero, not a fetch failure —
            // normalize to an empty success (the plugin-row arm's policy).
            runCatchingRethrowingCancellation {
                jellyPlaySources.getCuratedHero().getOrNull()
                    ?.let { payload -> resolveCuratedHeroEntries(payload.candidates) }
                    .orEmpty()
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Maps the wire candidates to [CuratedHeroEntry]s in serve order: the ids
     * resolve through ONE batched [JellyPlayHomeSectionSources.getItemsByIds]
     * read (the plugin rows' resolution pattern), and an id the server no
     * longer knows DROPS — a curated hero never renders a fallback tile. The
     * serve order is authoritative end-to-end; a duplicated id keeps its
     * FIRST slot (the earlier candidate wins) and resolves once.
     */
    private suspend fun resolveCuratedHeroEntries(candidates: List<JellyPlayHeroCandidate>): List<CuratedHeroEntry> {
        val deduped = candidates
            .filter { it.itemId.isNotBlank() }
            .distinctBy { it.itemId }
        if (deduped.isEmpty()) return emptyList()
        val resolved = jellyPlaySources?.getItemsByIds(deduped.map { it.itemId })
            ?.getOrDefault(emptyList())
            .orEmpty()
            .associateBy { it.id }
        return deduped.mapNotNull { candidate ->
            resolved[candidate.itemId]?.let { item ->
                CuratedHeroEntry(
                    item = item,
                    headline = candidate.headline?.takeIf { it.isNotBlank() },
                    subhead = candidate.subhead?.takeIf { it.isNotBlank() },
                )
            }
        }
    }
}

/**
 * One mapped plugin row as the [PluginHomeContent]'s plugin-row memo value:
 * the plugin's authoritative title plus the resolved entries (full rows are
 * memoised, so a hit costs zero port calls).
 */
private data class PluginRowValue(
    val title: String,
    val entries: List<JellyPlayRowEntry>,
) {
    companion object {
        val EMPTY = PluginRowValue(title = "", entries = emptyList())
    }
}
