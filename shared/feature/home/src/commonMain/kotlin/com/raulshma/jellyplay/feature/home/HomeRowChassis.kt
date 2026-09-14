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
 * The three sinks every resume row routes through — the triple
 * [resumeRowClick] and [posterRowClick] share, bundled so the render sites
 * build it once (all rows in a render hand the same funnels) and the two
 * folds' signatures state the routing policy, not the plumbing.
 */
internal class ResumeRowSinks(
    val onDetails: (MediaItem) -> Unit,
    val onPlay: (MediaItem) -> Unit,
    val onAsk: (MediaItem) -> Unit,
)

/**
 * The section types that honor the resume click behavior ([ContinueWatchingClickBehavior]):
 * CONTINUE_WATCHING and CONTINUE_READING — the ONE predicate [resumeRowClick]
 * and [posterRowClick] share, so the next resume section joins both folds by
 * editing this single site.
 */
internal fun isResumeSection(sectionType: HomeSectionType): Boolean =
    sectionType == HomeSectionType.CONTINUE_WATCHING || sectionType == HomeSectionType.CONTINUE_READING

/**
 * One implementation of the CW / CONTINUE_READING / NEXT_UP click routing,
 * shared END-TO-END by the online and offline rows: the call sites differ only
 * in [toMediaItem] (offline lifts its items to [MediaItem]; online passes
 * identity), and every branch maps HERE before handing to its sink — the
 * ASK-dialog wiring in particular cannot drift between the two sites.
 * Resume sections ([isResumeSection]) honor [behavior] (books resume the
 * reader on PLAY — the play funnel's BOOK fork navigates to the reader);
 * NEXT_UP (and every other section type) always opens details. Every sink
 * lands in the same unified MediaDetail / player tree: it renders remote and
 * downloaded items alike, so no source-specific routing is needed.
 */
internal fun <T> resumeRowClick(
    sectionType: HomeSectionType,
    behavior: ContinueWatchingClickBehavior,
    toMediaItem: (T) -> MediaItem,
    sinks: ResumeRowSinks,
): (T) -> Unit = { item ->
    val mediaItem = toMediaItem(item)
    if (isResumeSection(sectionType)) {
        when (behavior) {
            ContinueWatchingClickBehavior.DETAILS -> sinks.onDetails(mediaItem)
            ContinueWatchingClickBehavior.PLAY -> sinks.onPlay(mediaItem)
            ContinueWatchingClickBehavior.ASK -> sinks.onAsk(mediaItem)
        }
    } else {
        sinks.onDetails(mediaItem)
    }
}

/**
 * The poster-row click routing — the ONE selection both poster render sites
 * (offline-mirrored and online) share: a resume-section poster row
 * ([isResumeSection] — Continue Reading today; the wide resume rows never
 * render as posters, but would route identically if they did) rides the
 * resume-row policy ([resumeRowClick]: Details / Play / Ask per the pref),
 * every other poster row opens through [onPlainClick]. Which poster rows
 * honor the resume behavior is decided here, not at the render sites, so the
 * two rows cannot drift; the item lift stays the caller's ([toMediaItem] —
 * offline lifts to [MediaItem], online passes identity) and the plain-click
 * sink differs by source (offline routes by id, online hands the item).
 */
internal fun <T> posterRowClick(
    sectionType: HomeSectionType,
    behavior: ContinueWatchingClickBehavior,
    toMediaItem: (T) -> MediaItem,
    sinks: ResumeRowSinks,
    onPlainClick: (T) -> Unit,
): (T) -> Unit =
    if (isResumeSection(sectionType)) {
        resumeRowClick(sectionType, behavior, toMediaItem, sinks)
    } else {
        onPlainClick
    }
