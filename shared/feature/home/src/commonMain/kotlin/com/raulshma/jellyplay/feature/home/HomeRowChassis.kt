package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem

/**
 * Which row chassis renders a home section — the per-section branch chain of
 * [HomeContentList] as data, so the render site's `when` is exhaustive and
 * decides nothing. The offline half of the dispatch is the offline-mirror
 * rule (#147): while the offline feed renders, every non-wide section is
 * offline-derived (generic DOWNLOADED rows or cached-layout mirror rows typed
 * as their online counterparts) and renders through the offline rows with
 * local artwork. Online, only a DOWNLOADED section could ever hit the offline
 * poster branch (defensive — the online feed has none). Pinned by
 * HomeRowChassisTest.
 */
internal sealed interface HomeRowChassis {

    /** The section this chassis renders. */
    val section: HomeSection

    /** Offline-derived poster row: DOWNLOADED or any mirrored non-wide section. */
    data class OfflinePoster(override val section: HomeSection) : HomeRowChassis

    /** Offline-derived Continue Watching / Next Up — wide cards, local artwork. */
    data class OfflineWide(override val section: HomeSection) : HomeRowChassis

    /** Online Continue Watching / Next Up. */
    data class OnlineWide(override val section: HomeSection) : HomeRowChassis

    /** Online poster row — everything the offline mirror does not claim. */
    data class OnlinePoster(override val section: HomeSection) : HomeRowChassis
}

/**
 * The ONE dispatch deciding a section's [HomeRowChassis] — extracted verbatim
 * from the former if/else chain in [HomeContentList] so the precedence lives
 * in one pure, tested place. The order is today's: DOWNLOADED wins outright
 * (first predicate, online feed included); then, while the offline feed
 * renders, the mirror claims every non-wide section; Continue Watching /
 * Next Up route by feed source (wide rows), and anything else falls to the
 * online poster row.
 */
internal fun homeRowChassis(section: HomeSection, hasOfflineContent: Boolean): HomeRowChassis {
    val isWide = section.type == HomeSectionType.CONTINUE_WATCHING || section.type == HomeSectionType.NEXT_UP
    return when {
        section.type == HomeSectionType.DOWNLOADED || (hasOfflineContent && !isWide) ->
            HomeRowChassis.OfflinePoster(section)
        isWide && hasOfflineContent -> HomeRowChassis.OfflineWide(section)
        isWide -> HomeRowChassis.OnlineWide(section)
        else -> HomeRowChassis.OnlinePoster(section)
    }
}

/**
 * The section types that get the "See All" pill — the single source both
 * poster rows (the offline mirror and the online row) read through the one
 * hoisted gate in [HomeContentList], so they agree on which sections carry
 * the affordance.
 */
internal fun sectionHasSeeAll(sectionType: HomeSectionType): Boolean =
    sectionType == HomeSectionType.RECENTLY_ADDED || sectionType == HomeSectionType.LATEST_MEDIA

/**
 * One implementation of the CW / NEXT_UP click routing, shared END-TO-END by
 * the online and offline rows: the call sites differ only in [toMediaItem]
 * (offline lifts its items to [MediaItem]; online passes identity), and every
 * branch maps HERE before handing to its sink — the ASK-dialog wiring in
 * particular cannot drift between the two sites. CONTINUE_WATCHING honors
 * [behavior]; NEXT_UP (and every other section type) always opens details.
 * Every sink lands in the same unified MediaDetail / player tree: it renders
 * remote and downloaded items alike, so no source-specific routing is needed.
 */
internal fun <T> cwRowClick(
    sectionType: HomeSectionType,
    behavior: ContinueWatchingClickBehavior,
    toMediaItem: (T) -> MediaItem,
    onDetails: (MediaItem) -> Unit,
    onPlay: (MediaItem) -> Unit,
    onAsk: (MediaItem) -> Unit,
): (T) -> Unit = { item ->
    val mediaItem = toMediaItem(item)
    if (sectionType == HomeSectionType.CONTINUE_WATCHING) {
        when (behavior) {
            ContinueWatchingClickBehavior.DETAILS -> onDetails(mediaItem)
            ContinueWatchingClickBehavior.PLAY -> onPlay(mediaItem)
            ContinueWatchingClickBehavior.ASK -> onAsk(mediaItem)
        }
    } else {
        onDetails(mediaItem)
    }
}
