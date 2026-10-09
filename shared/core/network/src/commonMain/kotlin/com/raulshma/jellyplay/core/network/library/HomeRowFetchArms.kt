package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.concurrency.mapConcurrent
import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.DiscoverRowSource
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.PinnedHomeSection
import com.raulshma.jellyplay.core.model.descriptor
import com.raulshma.jellyplay.core.model.home.ContinueWatchingRowRule
import com.raulshma.jellyplay.core.model.home.HomeRowModules
import kotlinx.coroutines.sync.Semaphore

/**
 * The transport half of the home row registry — the per-type arms that need
 * network types (the [HomeSectionSources] port, the fetcher's TTL caches)
 * and therefore live beside the fetcher rather than on the model-layer
 * [HomeRowModules] (see its KDoc for the two-tier placement).
 *
 * Two tables, both keyed by [HomeSectionType]:
 *  - [HomeRowBatchSubcalls] — the batch fetch's per-type port call for the
 *    resume-row trio (the
 *    enabled gate and the query-parameter wiring move here from the fetch
 *    schedule; the SCHEDULE itself — what runs concurrently, the shared
 *    folders fetch, the recommendations seed chain — stays in
 *    [HomeSectionsFetcher.fetch] because it is cross-type policy).
 *  - [HomeRowRefreshArms] — the single-row refetch behind the home screen's
 *    edge-pull refresh (absorbed from `HomeSectionsFetcher.refreshSection`'s
 *    per-type `when`). Non-refreshable types share [notRefreshable] — the
 *    same caller-bug failure the former `else` arm threw.
 *
 * The arms consult the model registry's gates where they need one
 * ([HomeRowModule.edgeRefreshable] decides which types get a real arm) but
 * never the other way around: the model layer stays transport-blind.
 */

/**
 * The batch fetch's direct sub-call for the resume-row trio: the enabled
 * gate (a disabled section makes ZERO port calls — resolved locally as an
 * empty success) and the query-parameter wiring, in one registration per
 * type. Types without a direct sub-call (rows that emerge from a fan-out,
 * the seed chain, or a group path) are absent — the fetch schedule carries
 * them.
 */
internal val HomeRowBatchSubcalls: Map<HomeSectionType, suspend (HomeSectionSources, HomeSectionQuery) -> Result<List<MediaItem>>> =
    mapOf(
        HomeSectionType.CONTINUE_WATCHING to { sources, query ->
            if (HomeSectionType.CONTINUE_WATCHING in query.enabledSections) {
                sources.getContinueWatching(limit = 20, classicRows = query.classicRows)
            } else {
                Result.success(emptyList())
            }
        },
        HomeSectionType.CONTINUE_READING to { sources, query ->
            if (HomeSectionType.CONTINUE_READING in query.enabledSections) {
                sources.getContinueReading(limit = 20)
            } else {
                Result.success(emptyList())
            }
        },
        HomeSectionType.NEXT_UP to { sources, query ->
            if (HomeSectionType.NEXT_UP in query.enabledSections) {
                sources.getNextUp(
                    limit = 20,
                    enableRewatching = query.nextUpRewatching,
                    maxDays = query.nextUpMaxDays,
                )
            } else {
                Result.success(emptyList())
            }
        },
    )

/**
 * Everything a refresh arm needs from the fetcher — the port, the query, and
 * the fetcher-private collaborators (the TTL-wrapped latest-media read, the
 * discover-row memo, the plugin reads, the pin resolution), passed as
 * suspend function values so the arms stay pure mappings and the fetcher
 * keeps owning its caches.
 */
internal class HomeRowRefreshContext(
    val sources: HomeSectionSources,
    val query: HomeSectionQuery,
    /** The edge-pull gesture's "fetch fresh" flag — bypasses memo READS, keeps WRITES. */
    val force: Boolean,
    /**
     * Whether the user's layout folds Next Up into the Continue Watching row
     * (OrderHomeSectionsUseCase at batch time) — the CW refresh arm rebuilds
     * that fold from BOTH fresh sources when set.
     */
    val mergeNextUpIntoContinueWatching: Boolean,
    /** The fetcher's TTL-memoised per-library latest read (shared with the batch fan-out). */
    val latestForLibrary: suspend (libraryId: String, collectionType: String?, classicRows: Boolean) -> Result<List<MediaItem>>,
    /**
     * The Continue Watching id set the Next Up / Recently Added filters key
     * on — a fresh read, gated on Continue Watching being enabled.
     */
    val continueWatchingFilterIds: suspend () -> Set<String>,
    /** The dice-roll generation-guarded discover-row memo read. */
    val discoverRowItems: suspend (row: DiscoverRowConfig, force: Boolean) -> Result<List<MediaItem>>,
    /** The admin-defined titled plugin row read (gate + TTL memo + resolve). */
    val fetchPluginCustomRow: suspend (title: String, force: Boolean) -> HomeSection?,
    /** The seasonal plugin row read (gate + TTL memo + resolve). */
    val fetchSeasonalPluginRow: suspend (force: Boolean) -> List<HomeSection>,
    /** The pin's item resolution — the same routing table the batch runs. */
    val pinnedSectionItems: suspend (PinnedHomeSection) -> List<MediaItem>,
)

/** One single-row refresh arm: the section → fresh items (or null = row drops). */
internal fun interface HomeRowRefreshArm {
    suspend fun refresh(ctx: HomeRowRefreshContext, section: HomeSection): HomeSection?
}

/**
 * The single-row refresh arms, complete over the enum. Eight rows carry a
 * real arm; RECOMMENDATIONS, FAVORITES, LIVE_TV and DOWNLOADED share
 * [notRefreshable] (never constructed by the network, or — for
 * RECOMMENDATIONS — batch-shaped; the gesture is gated off them upstream and
 * an unguarded call fails with the same caller-bug error as before). The
 * refreshable set is derived from [HomeRowModules]/[com.raulshma.jellyplay.core.model.home.HomeRowModule.edgeRefreshable]
 * and pinned by test.
 */
internal object HomeRowRefreshArms {

    private val notRefreshable = HomeRowRefreshArm { _, section ->
        error("Home section type ${section.type} is not refreshable")
    }

    /** One item-swap shape for instance-typed rows: fresh items, identity preserved; empty drops the row. */
    private fun refreshedOrNull(section: HomeSection, items: List<MediaItem>): HomeSection? =
        if (items.isEmpty()) null else section.copy(items = items)

    private val arms: Map<HomeSectionType, HomeRowRefreshArm> = mapOf(

        HomeSectionType.CONTINUE_WATCHING to HomeRowRefreshArm { ctx, _ ->
            val cw = ctx.sources.getContinueWatching(limit = 20, classicRows = ctx.query.classicRows)
                .getOrThrow()
                .excludingHiddenItemIds(ctx.query.hiddenCwItemIds)
            if (!ctx.mergeNextUpIntoContinueWatching) {
                cw.takeIf { it.isNotEmpty() }
                    ?.let { HomeSectionType.CONTINUE_WATCHING.descriptor.section(it) }
            } else {
                // Merged row: the batch assembler folded Next Up into this
                // row (OrderHomeSectionsUseCase), so the refetch rebuilds
                // that fold from BOTH fresh sources — fresh CW first, fresh
                // Next Up (same eligibility filters as the NEXT_UP arm)
                // appended through ContinueWatchingRowRule.mergeCwNextUp,
                // the exact call the ordering use case runs at batch time.
                // A Next Up failure degrades to the CW half (the batch's
                // per-source failure policy); CW empty + Next Up present
                // is the merge's relabel arm — the row survives carrying
                // Next Up; both empty drops it.
                val nextUp = ContinueWatchingRowRule.nextUpEligible(
                    ctx.sources.getNextUp(
                        limit = 20,
                        enableRewatching = ctx.query.nextUpRewatching,
                        maxDays = ctx.query.nextUpMaxDays,
                    ).getOrDefault(emptyList()),
                    continueWatchingIds = cw.map { it.id }.toSet(),
                    excludedSeriesIds = ctx.query.nextUpExcludedSeriesIds,
                )
                val merged = ContinueWatchingRowRule.mergeCwNextUp(cw, nextUp)
                merged.takeIf { it.isNotEmpty() }
                    ?.let { HomeSectionType.CONTINUE_WATCHING.descriptor.section(it) }
            }
        },

        HomeSectionType.CONTINUE_READING to HomeRowRefreshArm { ctx, _ ->
            ctx.sources.getContinueReading(limit = 20)
                .getOrThrow()
                .excludingHiddenItemIds(ctx.query.hiddenCwItemIds)
                .takeIf { it.isNotEmpty() }
                ?.let { HomeSectionType.CONTINUE_READING.descriptor.section(it) }
        },

        HomeSectionType.NEXT_UP to HomeRowRefreshArm { ctx, _ ->
            val cwIds = ctx.continueWatchingFilterIds()
            ctx.sources.getNextUp(
                limit = 20,
                enableRewatching = ctx.query.nextUpRewatching,
                maxDays = ctx.query.nextUpMaxDays,
            )
                .getOrThrow()
                .filterNextUpEligible(cwIds, ctx.query.nextUpExcludedSeriesIds)
                .takeIf { it.isNotEmpty() }
                ?.let { HomeSectionType.NEXT_UP.descriptor.section(it) }
        },

        HomeSectionType.LATEST_MEDIA to HomeRowRefreshArm { ctx, section ->
            val libraryId = HomeSectionType.LATEST_MEDIA.descriptor.instanceIdFor(section.id)
                ?: error("Latest Media row ${section.id} carries no library id")
            val latest = ctx.latestForLibrary(libraryId, section.collectionType, ctx.query.classicRows)
                .getOrThrow()
            refreshedOrNull(section, latest)
        },

        HomeSectionType.RECENTLY_ADDED to HomeRowRefreshArm { ctx, section ->
            val folders = ctx.sources.getLibraryFolders().getOrThrow()
                .filter { it.collectionType != "music" }
            val allLatest = Semaphore(4).mapConcurrent(folders) { folder ->
                // The assembler feeds the aggregate only from libraries the
                // user hasn't disabled Recently Added for.
                if (HomeSectionType.RECENTLY_ADDED in ctx.query.libraryHomeSectionOverrides[folder.id].orEmpty()) {
                    emptyList()
                } else {
                    ctx.latestForLibrary(folder.id, folder.collectionType, ctx.query.classicRows)
                        .getOrDefault(emptyList())
                }
            }.flatten()
            val cwIds = ctx.continueWatchingFilterIds()
            refreshedOrNull(section, allLatest.distinctByIdExcluding(cwIds))
        },

        HomeSectionType.DISCOVER to HomeRowRefreshArm { ctx, section ->
            val row = ctx.query.discoverRows.firstOrNull {
                it.enabled && HomeSectionType.DISCOVER.descriptor.idFor(it.id) == section.id
            } ?: error("No enabled discover row for section ${section.id}")
            check(row.source == DiscoverRowSource.JELLYFIN) {
                "Seerr discover rows are not edge-refreshable (${section.id})"
            }
            ctx.discoverRowItems(row, ctx.force)
                .getOrThrow()
                .let { refreshedOrNull(section, it) }
        },

        HomeSectionType.PINNED to HomeRowRefreshArm { ctx, section ->
            val pin = ctx.query.pinnedSections.firstOrNull {
                HomeSectionType.PINNED.descriptor.idFor(it.id) == section.id
            } ?: error("No pinned section configured for ${section.id}")
            refreshedOrNull(section, ctx.pinnedSectionItems(pin))
        },

        HomeSectionType.PLUGIN_ROW to HomeRowRefreshArm { ctx, section ->
            // The plugin row's single-row refetch (edge pull): the SAME
            // gated leaf reads the batch path runs — the seasonal and custom
            // reads carry the gate, the TTL memo and the builder, so the row
            // id stays stable and only the payload moves (the gesture's
            // `force` flag carries the "fetch fresh"). An emptied source
            // (plugin row removed mid-session) yields null — the row drops,
            // matching the batch's zero-items policy.
            val instanceId = HomeSectionType.PLUGIN_ROW.descriptor.instanceIdFor(section.id)
                ?: error("Plugin row ${section.id} carries no instance id")
            if (instanceId.startsWith("custom_")) {
                ctx.fetchPluginCustomRow(instanceId.removePrefix("custom_"), ctx.force)
            } else {
                ctx.fetchSeasonalPluginRow(ctx.force).firstOrNull()
            }
        },
    )

    /** The arm for [type] — a real arm for the refreshable rows, [notRefreshable] for the rest. */
    internal fun forType(type: HomeSectionType): HomeRowRefreshArm =
        if (HomeRowModules[type].edgeRefreshable) {
            arms.getValue(type)
        } else {
            notRefreshable
        }
}
