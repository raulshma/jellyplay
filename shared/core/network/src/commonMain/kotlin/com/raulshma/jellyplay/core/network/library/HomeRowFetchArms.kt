package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.concurrency.mapConcurrent
import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.DiscoverRowSource
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.PinnedHomeSection
import com.raulshma.jellyplay.core.model.descriptor
import com.raulshma.jellyplay.core.model.home.ContinueWatchingRowRule
import com.raulshma.jellyplay.core.model.home.HomeRowModules
import kotlinx.coroutines.sync.Semaphore

/**
 * The transport registration of the home row registry — the network-layer
 * counterpart of the model-layer [HomeRowModules] (see its KDoc for the
 * two-tier placement): ONE registration per [HomeSectionType] carrying BOTH
 * of the row's network arms, where two parallel tables used to hold the batch
 * sub-call and the single-row refresh arm in agreement by comment.
 *
 * Per registration:
 *  - [HomeRowTransportRegistration.read] — the row's direct port read with
 *    its wire parameters (the resume trio's limit and the H6
 *    exclude-active-sessions flag, Next Up's rewatching/maxDays params, the
 *    H4 Upcoming read shape), stated ONCE; the batch sub-call and the refresh
 *    arm both call it, so they cannot disagree on the wire.
 *  - [HomeRowTransportRegistration.batchSubcall] — the batch fetch's per-type
 *    sub-call: the enabled gate (a disabled section makes ZERO port calls —
 *    resolved locally as an empty success) wrapped around the read, stated
 *    once for every registration rather than per entry. Types without a
 *    direct read (rows that emerge from a fan-out, the seed chain, or a group
 *    path) carry null — the fetch schedule owns those.
 *  - [HomeRowTransportRegistration.refreshArm] — the single-row refetch
 *    behind the home screen's edge-pull refresh; null when the row has no
 *    refresh arm today (never constructed by the network, or batch-shaped) —
 *    the absence is stated once, on the registration, instead of an absent
 *    table entry, and [HomeSectionsFetcher.refreshSection] fails such a call
 *    with the caller-bug error.
 *
 * The SCHEDULE itself — what runs concurrently, the shared folders fetch, the
 * recommendations seed chain — stays in [HomeSectionsFetcher.fetch] because it
 * is cross-type policy; the RECENTLY_ADDED refresh arm re-runs the schedule's
 * latest fan-out through the one shared shape
 * ([HomeRowRefreshContext.latestFanOut]). The arms consult the model
 * registry's gates where they need one ([HomeRowModule.edgeRefreshable]
 * decides which types carry a real arm — the equivalence is pinned by
 * HomeRowArmsTest) but never the other way around: the model layer stays
 * transport-blind. The fold references are shared the same way: the CW arm's
 * merge branch consumes the NEXT_UP registration's read and runs
 * [ContinueWatchingRowRule.mergedContinueWatchingRow] — the item-list
 * projection of OrderHomeSectionsUseCase's section fold, the fold's canonical
 * owner (this arm and the offline mirror run its projection; neither
 * re-derives the fold).
 */

/** The fixed row limit the resume trio and the H4 Upcoming read send — stated once, shared by the batch sub-call and the refresh arm. */
private const val RESUME_ROW_LIMIT = 20

/**
 * The row's direct port read: sources + query → items, with the wire
 * parameters (limits, flags) living here and nowhere else.
 */
internal typealias HomeRowRead = suspend (HomeSectionSources, HomeSectionQuery) -> Result<List<MediaItem>>

/**
 * The batch fetch's direct sub-call for one row — the enabled-gated
 * [HomeRowRead] the fetch schedule launches.
 */
internal typealias HomeRowBatchSubcall = suspend (HomeSectionSources, HomeSectionQuery) -> Result<List<MediaItem>>

/**
 * One home row's transport registration — see the file KDoc. Everything the
 * type knows about the wire sits on this class; the fetch schedule and
 * [HomeSectionsFetcher.refreshSection] dispatch through it.
 */
internal class HomeRowTransportRegistration(
    val type: HomeSectionType,
    /** The row's direct port read, wire parameters stated once — null when the row emerges from a fan-out, the seed chain, or a group path. */
    val read: HomeRowRead? = null,
    /** The single-row refresh arm — null when the row has no refresh arm today (stated here, once). */
    val refreshArm: HomeRowRefreshArm? = null,
) {

    /**
     * The batch sub-call: the enabled gate around [read] (a disabled section
     * makes ZERO port calls — resolved locally as an empty success). Null iff
     * [read] is null.
     */
    val batchSubcall: HomeRowBatchSubcall? = read?.let { rowRead ->
        // A suspend-function-type typealias has no constructor syntax — the
        // lambda needs the typed local to check against the suspend shape.
        val gatedRead: HomeRowBatchSubcall = { sources, query ->
            if (type in query.enabledSections) {
                rowRead(sources, query)
            } else {
                Result.success(emptyList())
            }
        }
        gatedRead
    }
}

// ── The four direct reads (wire parameters stated once) ─────────────────────

private val continueWatchingRead: HomeRowRead = { sources, query ->
    sources.getContinueWatching(
        limit = RESUME_ROW_LIMIT,
        classicRows = query.classicRows,
        excludeActiveSessions = query.excludeActiveSessions,
    )
}

private val continueReadingRead: HomeRowRead = { sources, _ ->
    sources.getContinueReading(limit = RESUME_ROW_LIMIT)
}

private val nextUpRead: HomeRowRead = { sources, query ->
    sources.getNextUp(
        limit = RESUME_ROW_LIMIT,
        enableRewatching = query.nextUpRewatching,
        maxDays = query.nextUpMaxDays,
    )
}

// H4: the catalog-wide unaired list — no per-user query parameters, uncached
// like its resume-trio siblings (the sub-call TTL memos are the latest/similar
// rows' policy; watch-state rows stay live every refresh).
private val upcomingRead: HomeRowRead = { sources, _ ->
    sources.getUpcoming(userId = null, parentId = null, limit = RESUME_ROW_LIMIT)
}

/** One item-swap shape for instance-typed rows: fresh items, identity preserved; empty drops the row. */
private fun refreshedOrNull(section: HomeSection, items: List<MediaItem>): HomeSection? =
    if (items.isEmpty()) null else section.copy(items = items)

// ── The nine refresh arms ────────────────────────────────────────────────────

/**
 * CONTINUE_WATCHING's refresh arm: the row's registered read, the hidden-item
 * drop, and — when the user's layout folds Next Up into this row — the merge
 * fold's rebuild from BOTH fresh sources: the NEXT_UP registration's read
 * (the same wire params — the fold cannot disagree with the row it folds)
 * through [ContinueWatchingRowRule.mergedContinueWatchingRow], the item-list
 * projection of OrderHomeSectionsUseCase's section fold (the fold's canonical
 * owner — the batch ordering runs the section-level fold at merge time; this
 * rebuild and the offline mirror run its item-list projection). A Next Up
 * failure degrades to the CW half (the batch's per-source failure policy);
 * CW empty + Next Up present is the merge's relabel arm — the row survives
 * carrying Next Up; both empty drops it.
 */
private val continueWatchingRefreshArm = HomeRowRefreshArm { ctx, _ ->
    val cw = continueWatchingRead(ctx.sources, ctx.query)
        .getOrThrow()
        .excludingHiddenItemIds(ctx.query.hiddenCwItemIds)
    if (!ctx.mergeNextUpIntoContinueWatching) {
        cw.takeIf { it.isNotEmpty() }
            ?.let { HomeSectionType.CONTINUE_WATCHING.descriptor.section(it) }
    } else {
        val nextUp = nextUpRead(ctx.sources, ctx.query).getOrDefault(emptyList())
        val merged = ContinueWatchingRowRule.mergedContinueWatchingRow(
            cw = cw,
            nextUp = nextUp,
            excludedSeriesIds = ctx.query.nextUpExcludedSeriesIds,
        )
        merged.takeIf { it.isNotEmpty() }
            ?.let { HomeSectionType.CONTINUE_WATCHING.descriptor.section(it) }
    }
}

/** CONTINUE_READING's refresh arm: the registered read + the hidden-item drop, identity preserved. */
private val continueReadingRefreshArm = HomeRowRefreshArm { ctx, _ ->
    continueReadingRead(ctx.sources, ctx.query)
        .getOrThrow()
        .excludingHiddenItemIds(ctx.query.hiddenCwItemIds)
        .takeIf { it.isNotEmpty() }
        ?.let { HomeSectionType.CONTINUE_READING.descriptor.section(it) }
}

/**
 * NEXT_UP's refresh arm: the registered read + the assembler's eligibility
 * filters — the CW-overlap set from a fresh Continue Watching read
 * ([HomeRowRefreshContext.continueWatchingFilterIds], mirroring the
 * assembler's seed reuse) and the excluded-series blocklist.
 */
private val nextUpRefreshArm = HomeRowRefreshArm { ctx, _ ->
    val cwIds = ctx.continueWatchingFilterIds()
    nextUpRead(ctx.sources, ctx.query)
        .getOrThrow()
        .filterNextUpEligible(cwIds, ctx.query.nextUpExcludedSeriesIds)
        .takeIf { it.isNotEmpty() }
        ?.let { HomeSectionType.NEXT_UP.descriptor.section(it) }
}

// H4: the batch sub-call's identical port read — no eligibility filters
// (upcoming is watch-state-blind by definition; there is nothing to
// re-filter against), rebuilt under the descriptor's static identity.
private val upcomingRefreshArm = HomeRowRefreshArm { ctx, _ ->
    upcomingRead(ctx.sources, ctx.query)
        .getOrThrow()
        .takeIf { it.isNotEmpty() }
        ?.let { HomeSectionType.UPCOMING.descriptor.section(it) }
}

/** LATEST_MEDIA's refresh arm: one `/Items/Latest` for the row's own library, through the fetcher's TTL memo. */
private val latestMediaRefreshArm = HomeRowRefreshArm { ctx, section ->
    val libraryId = HomeSectionType.LATEST_MEDIA.descriptor.instanceIdFor(section.id)
        ?: error("Latest Media row ${section.id} carries no library id")
    val latest = ctx.latestForLibrary(libraryId, section.collectionType, ctx.query.classicRows)
        .getOrThrow()
    refreshedOrNull(section, latest)
}

/**
 * RECENTLY_ADDED's refresh arm: the batch fan-out's aggregate twin — the same
 * shared folders read → music filter → semaphore-bounded per-library latest
 * ([HomeRowRefreshContext.latestFanOut], the shape the fetch schedule runs),
 * re-aggregated with the assembler's per-folder override (a disabled library
 * contributes nothing — and costs no port call: the gate runs before the
 * fan-out, where the batch applies the same overrides at assembly) and the
 * CW-overlap fold. The row is an aggregate, so its refetch costs the
 * aggregate.
 */
private val recentlyAddedRefreshArm = HomeRowRefreshArm { ctx, section ->
    val folders = ctx.sources.getLibraryFolders().getOrThrow()
        .filter { folder ->
            HomeSectionType.RECENTLY_ADDED !in ctx.query.libraryHomeSectionOverrides[folder.id].orEmpty()
        }
    val allLatest = ctx.latestFanOut(folders)
        .flatMap { (_, latest) -> latest.getOrDefault(emptyList()) }
    val cwIds = ctx.continueWatchingFilterIds()
    refreshedOrNull(section, allLatest.distinctByIdExcluding(cwIds))
}

private val discoverRefreshArm = HomeRowRefreshArm { ctx, section ->
    val row = ctx.query.discoverRows.firstOrNull {
        it.enabled && HomeSectionType.DISCOVER.descriptor.idFor(it.id) == section.id
    } ?: error("No enabled discover row for section ${section.id}")
    check(row.source == DiscoverRowSource.JELLYFIN) {
        "Seerr discover rows are not edge-refreshable (${section.id})"
    }
    ctx.discoverRowItems(row, ctx.force)
        .getOrThrow()
        .let { refreshedOrNull(section, it) }
}

private val pinnedRefreshArm = HomeRowRefreshArm { ctx, section ->
    val pin = ctx.query.pinnedSections.firstOrNull {
        HomeSectionType.PINNED.descriptor.idFor(it.id) == section.id
    } ?: error("No pinned section configured for ${section.id}")
    refreshedOrNull(section, ctx.pinnedSectionItems(pin))
}

/**
 * PLUGIN_ROW's refresh arm: the same gated leaf read the batch path runs —
 * the seasonal and custom reads carry the gate, the TTL memo and the builder,
 * so the row id stays stable and only the payload moves (the gesture's
 * `force` flag carries the "fetch fresh"). An emptied source (plugin row
 * removed mid-session) yields null — the row drops, matching the batch's
 * zero-items policy.
 */
private val pluginRowRefreshArm = HomeRowRefreshArm { ctx, section ->
    val instanceId = HomeSectionType.PLUGIN_ROW.descriptor.instanceIdFor(section.id)
        ?: error("Plugin row ${section.id} carries no instance id")
    if (instanceId.startsWith("custom_")) {
        ctx.fetchPluginCustomRow(instanceId.removePrefix("custom_"), ctx.force)
    } else {
        ctx.fetchSeasonalPluginRow(ctx.force).firstOrNull()
    }
}

/**
 * The caller-bug arm: a refresh arriving for a type whose registration states
 * no refresh arm (never constructed by the network, or batch-shaped — the
 * gesture is gated off those upstream) fails with this error rather than
 * guessing.
 */
private val notRefreshable = HomeRowRefreshArm { _, section ->
    error("Home section type ${section.type} is not refreshable")
}

/**
 * THE transport table: every [HomeSectionType] maps to exactly one
 * registration (complete over the enum, in the model registry's order), so a
 * missing arm or read is a registration decision, never an absent table
 * entry. Adding a row type touches one registration line here.
 */
internal val HomeRowTransports: Map<HomeSectionType, HomeRowTransportRegistration> = listOf(
    HomeRowTransportRegistration(
        HomeSectionType.CONTINUE_WATCHING,
        read = continueWatchingRead,
        refreshArm = continueWatchingRefreshArm,
    ),
    HomeRowTransportRegistration(
        HomeSectionType.CONTINUE_READING,
        read = continueReadingRead,
        refreshArm = continueReadingRefreshArm,
    ),
    HomeRowTransportRegistration(
        HomeSectionType.NEXT_UP,
        read = nextUpRead,
        refreshArm = nextUpRefreshArm,
    ),
    HomeRowTransportRegistration(
        HomeSectionType.UPCOMING,
        read = upcomingRead,
        refreshArm = upcomingRefreshArm,
    ),
    // My Media: the tiles assemble the batch's shared folders read — no port
    // call of their own, so no read and no refresh arm (edge refresh buys
    // nothing; the row is exactly as fresh as the batch that fetched it).
    HomeRowTransportRegistration(HomeSectionType.MY_MEDIA),
    HomeRowTransportRegistration(
        HomeSectionType.LATEST_MEDIA,
        refreshArm = latestMediaRefreshArm,
    ),
    HomeRowTransportRegistration(
        HomeSectionType.RECENTLY_ADDED,
        refreshArm = recentlyAddedRefreshArm,
    ),
    // Recommendations: the seed chain is batch-shaped — no single-row refetch.
    HomeRowTransportRegistration(HomeSectionType.RECOMMENDATIONS),
    HomeRowTransportRegistration(
        HomeSectionType.DISCOVER,
        refreshArm = discoverRefreshArm,
    ),
    // Favorites: never constructed by the network.
    HomeRowTransportRegistration(HomeSectionType.FAVORITES),
    HomeRowTransportRegistration(HomeSectionType.LIVE_TV),
    HomeRowTransportRegistration(HomeSectionType.DOWNLOADED),
    HomeRowTransportRegistration(
        HomeSectionType.PINNED,
        refreshArm = pinnedRefreshArm,
    ),
    HomeRowTransportRegistration(
        HomeSectionType.PLUGIN_ROW,
        refreshArm = pluginRowRefreshArm,
    ),
).associateBy { it.type }

/**
 * The batch fetch's direct sub-call for [type] — the registration's
 * enabled-gated read. Absent for the fan-out/seed/group rows; the fetch
 * schedule carries those, and asking for their sub-call is a schedule bug.
 */
internal fun homeRowBatchSubcall(type: HomeSectionType): HomeRowBatchSubcall =
    HomeRowTransports.getValue(type).batchSubcall
        ?: error("Home section type $type has no batch sub-call (its row emerges from the fetch schedule)")

/**
 * The single-row refresh arm for [type] — the registration's real arm for the
 * refreshable rows, [notRefreshable] for the rest (the same caller-bug
 * failure the former `else` arm threw). The refreshable set is derived from
 * [HomeRowModules]/[com.raulshma.jellyplay.core.model.home.HomeRowModule.edgeRefreshable]
 * and pinned by test.
 */
internal fun homeRowRefreshArm(type: HomeSectionType): HomeRowRefreshArm =
    HomeRowTransports.getValue(type).refreshArm ?: notRefreshable

/**
 * Everything a refresh arm needs from the fetcher — the port, the query, and
 * the fetcher-private collaborators (the TTL-wrapped latest-media read, the
 * shared latest fan-out, the discover-row memo, the plugin reads, the pin
 * resolution), passed as suspend function values so the arms stay pure
 * mappings and the fetcher keeps owning its caches.
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
     * The latest fan-out's ONE shape — folders → music filter → per-library
     * latest, semaphore-bounded, in folder order: the same fan-out the batch
     * schedule runs ([HomeSectionsFetcher.fetch]). The RECENTLY_ADDED refresh
     * arm re-aggregates its degraded results; the per-library override gate
     * stays with the arm (it must gate the port call, not the payload).
     */
    val latestFanOut: suspend (folders: List<LibraryFolder>) -> List<Pair<LibraryFolder, Result<List<MediaItem>>>>,
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
