package com.raulshma.jellyplay.core.model.home

import com.raulshma.jellyplay.core.model.HomeSectionDescriptor
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.descriptor

/**
 * Everything one [HomeSectionType] knows about itself — the "home row module"
 * deep module (ADR-0009 discipline): a row's identity, its per-type data
 * gates and its offline projection live in ONE registration instead of being
 * smeared across the pipeline's per-type `when` dispatches. Adding a row type
 * becomes one module object + one registration line (plus the enum constant),
 * and the registry-completeness test turns a missing registration into a
 * failing test instead of a runtime gap.
 *
 * TWO tiers, one home (placement is by dependency honesty):
 *  - THIS file (core/model, beside [ContinueWatchingRowRule]) owns the
 *    dependency-free facts: identity (delegating to the row's
 *    [HomeSectionDescriptor]), the chassis gates, the ordering affinity and
 *    the offline projection (see [HomeRowOfflineProjection]).
 *  - The transport arms (which port call produces the row, how a single row
 *    refetches) reference network types (`HomeSectionSources`, the fetcher's
 *    TTL caches) and therefore live in a sibling arm registry keyed by the
 *    same enum — core/network's `HomeRowFetchArms`/`HomeRowRefreshArms`.
 *    The model layer must not know the transport, so the module cannot hold
 *    those members; the split is deliberate, not a missing merge.
 *
 * NOT covered, on purpose (ADR-0009 protects hand-written emission): the
 * Compose rendering bodies, the localized row titles, the section
 * construction at the render sites, and the icon mapping (a UI-resource
 * concern in core/ui). The row module exposes the typed data those
 * hand-written bodies consume.
 */
public interface HomeRowModule {

    /** The section type this module serves. */
    public val type: HomeSectionType

    /** The row's identity (display strings, configurability, row-id grammar) — the descriptor is the registration's identity half. */
    public val descriptor: HomeSectionDescriptor

    /**
     * The row renders through the WIDE-card chassis (online and offline) —
     * Continue Watching and Next Up — every other row through the poster
     * chassis. The chassis dispatch's data half.
     */
    public val wideRow: Boolean get() = false

    /**
     * The row carries the "See All" pill — Recently Added and the per-library
     * Latest Media rows, the only rows whose full item set lives behind a
     * library browse screen.
     */
    public val hasSeeAll: Boolean get() = false

    /**
     * The row is a RESUME row: it honors the resume click behavior
     * (Details / Play / Ask per the user's pref) instead of always opening
     * details. Continue Watching and Continue Reading — the rows of
     * in-progress positions. Next Up is deliberately NOT resume-like by this
     * definition (its items are unplayed; a tap always opens details) — the
     * merge donor fact is [mergesIntoContinueWatching], a different set.
     */
    public val resumeLike: Boolean get() = false

    /**
     * The row can be refetched on its own (the home screen's edge-pull
     * refresh): the type half of the gesture gate — a registered single-row
     * fetch arm exists for it in core/network's `HomeRowRefreshArms`. The
     * per-INSTANCE half (Seerr-sourced discover rows ride the batch group
     * gate) stays with the section at the call site.
     */
    public val edgeRefreshable: Boolean get() = false

    /**
     * When the user's layout folds Next Up into Continue Watching (the merge
     * pref), this row DONATES its items into the Continue Watching row
     * ([ContinueWatchingRowRule.mergeCwNextUp] at batch time and offline;
     * the single-row refresh rebuilds the same fold) and drops. Next Up is
     * the only donor.
     */
    public val mergesIntoContinueWatching: Boolean get() = false

    /** The row's offline half — how it computes from the downloaded store. */
    public val offline: HomeRowOfflineProjection get() = InertOfflineProjection
}

/** The shared no-op offline projection: no fallback compute, generic mirror arm. */
private object InertOfflineProjection : HomeRowOfflineProjection()

/**
 * THE home row registry: every [HomeSectionType] maps to exactly one
 * [HomeRowModule]. Registration order IS the default home layout order for
 * the configurable types — [defaultOrder] — with the non-configurable types
 * trailing (they render by their own gates, never the layout order).
 */
public object HomeRowModules {

    private val registry: Map<HomeSectionType, HomeRowModule> = listOf<HomeRowModule>(
        // Configurable types first, IN the default layout order (pinned equal
        // to HomeSectionType.CONFIGURABLE by HomeRowModulesTest — the
        // enum's hand list stays the wire-safe authority, this is its
        // registry mirror).
        ContinueWatching,
        ContinueReading,
        NextUp,
        LatestMedia,
        RecentlyAdded,
        Recommendations,
        Discover,
        // Non-configurable types: driven by their own gates, layout-blind.
        Favorites,
        LiveTv,
        Downloaded,
        Pinned,
        PluginRows,
    ).associateBy { it.type }

    /** The module serving [type]. Missing registration = programming error (fails fast). */
    public operator fun get(type: HomeSectionType): HomeRowModule = registry.getValue(type)

    /** All registered modules, in registration order. */
    public val all: List<HomeRowModule> get() = registry.values.toList()

    /**
     * The configurable types in DEFAULT ORDER — the registry's registration
     * order projected to the configurable rows. Pinned equal to
     * [HomeSectionType.CONFIGURABLE] by HomeRowModulesTest; the enum list
     * stays the wire-safe authority (persisted layouts reference it), this
     * is its single derivation source so the two cannot drift silently.
     */
    public val defaultOrder: List<HomeSectionType> =
        registry.values.filter { it.descriptor.isConfigurable }.map { it.type }
}

// ── The 12 registrations ────────────────────────────────────────────────────
// One object per row type; identity delegates to the descriptor, and each
// registration overrides exactly the facts its row carries (everything else
// keeps the interface's inert default).

/** Continue Watching — the wide video resume row. */
public object ContinueWatching : HomeRowModule {
    override val type = HomeSectionType.CONTINUE_WATCHING
    override val descriptor = type.descriptor
    override val wideRow = true
    override val resumeLike = true
    override val edgeRefreshable = true
    override val offline = ContinueWatchingOfflineProjection
}

/** Continue Reading — the book resume row (poster chassis, resume click behavior). */
public object ContinueReading : HomeRowModule {
    override val type = HomeSectionType.CONTINUE_READING
    override val descriptor = type.descriptor
    override val resumeLike = true
    override val edgeRefreshable = true
    override val offline = ContinueReadingOfflineProjection
}

/** Next Up — the wide "unplayed next episode" row; donates into Continue Watching on merge. */
public object NextUp : HomeRowModule {
    override val type = HomeSectionType.NEXT_UP
    override val descriptor = type.descriptor
    override val wideRow = true
    override val edgeRefreshable = true
    override val mergesIntoContinueWatching = true
    override val offline = NextUpOfflineProjection
}

/** Latest Media — one row per library; per-library overrides gate it. */
public object LatestMedia : HomeRowModule {
    override val type = HomeSectionType.LATEST_MEDIA
    override val descriptor = type.descriptor
    override val hasSeeAll = true
    override val edgeRefreshable = true
}

/** Recently Added — the cross-library aggregate; See All + edge refresh (refetch re-runs the fan-out). */
public object RecentlyAdded : HomeRowModule {
    override val type = HomeSectionType.RECENTLY_ADDED
    override val descriptor = type.descriptor
    override val hasSeeAll = true
    override val edgeRefreshable = true
}

/** Recommendations — the seed-chain row: batch-shaped, never edge-refreshable; keeps its seed offline. */
public object Recommendations : HomeRowModule {
    override val type = HomeSectionType.RECOMMENDATIONS
    override val descriptor = type.descriptor
    override val offline = RecommendationsOfflineProjection
}

/** Discover — user-configured Jellyfin/Seerr rows; Jellyfin rows refresh, Seerr rows ride the batch group gate. */
public object Discover : HomeRowModule {
    override val type = HomeSectionType.DISCOVER
    override val descriptor = type.descriptor
    override val edgeRefreshable = true
}

/** Favorites — never constructed by the network; surfaces through user layout config lists only. */
public object Favorites : HomeRowModule {
    override val type = HomeSectionType.FAVORITES
    override val descriptor = type.descriptor
}

/** Live TV — unplayable offline; the mirror always drops it. */
public object LiveTv : HomeRowModule {
    override val type = HomeSectionType.LIVE_TV
    override val descriptor = type.descriptor
    override val offline = LiveTvOfflineProjection
}

/** Downloaded — the offline-only rows (recent / movies / series / music partition); never in an online snapshot. */
public object Downloaded : HomeRowModule {
    override val type = HomeSectionType.DOWNLOADED
    override val descriptor = type.descriptor
    override val offline = DownloadedOfflineProjection
}

/** Pinned — user-pinned collections/playlists/genres/studios, always fetched, refreshable per pin. */
public object Pinned : HomeRowModule {
    override val type = HomeSectionType.PINNED
    override val descriptor = type.descriptor
    override val edgeRefreshable = true
}

/** Plugin Rows — the companion server plugin's capability-gated rows (ADR 0010); refreshable per row. */
public object PluginRows : HomeRowModule {
    override val type = HomeSectionType.PLUGIN_ROW
    override val descriptor = type.descriptor
    override val edgeRefreshable = true
}
