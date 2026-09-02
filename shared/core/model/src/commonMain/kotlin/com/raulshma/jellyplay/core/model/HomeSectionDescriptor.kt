package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable

/**
 * Deep module: the single owner of a home section's identity — its network row
 * id, user-facing name/description (the name doubles as the rendered row
 * header for single-row sections) and configurability.
 *
 * Previously this identity was fragmented across three layers: the display
 * strings lived on the [HomeSectionType] enum, the row id/title literals were
 * hardcoded at every construction site in the network impl (and had already
 * drifted — NEXT_UP rendered "NextUp" while every other surface showed "Next
 * Up"), and the icon mapping was duplicated in core/ui and settings. The
 * descriptor puts all identity facts next to the enum so a new surface reads
 * them from one place instead of copying literals that silently rot.
 *
 * The enum's CONSTANT NAMES are frozen: they are persisted by `t.name` in
 * HomeDiscoveryStore (DataStore), embedded by name in the Room JSON snapshot
 * (Converters.encodeHomeSectionsResult) and in `HomeSectionQuery.cacheKey()`.
 * Renaming a constant silently orphans persisted state; display strings may
 * evolve freely here.
 *
 * Dynamic sections carry no static id: LATEST_MEDIA fans out one row per
 * Jellyfin library (id `"latest_<libraryId>"`, title `"Latest <Library>"`),
 * and PINNED rows are fully instance-defined (the user's pin title under a
 * `"pinned_<pinId>"` id). Those resolve at fetch time via [idFor]/[titleFor].
 * FAVORITES, LIVE_TV and DOWNLOADED are never constructed by the network, so
 * they describe nothing but still need a descriptor (display strings) for the
 * UI surfaces that list every section type.
 */
@Immutable
class HomeSectionDescriptor(
    val type: HomeSectionType,
    /** Static row id for single-row sections; `null` when resolved per instance ([idFor]) or never fetched. */
    val id: String?,
    /** Human-readable name shown by config UIs and rendered as the row header by [section]. The enum's `displayName` delegates here. */
    val displayName: String,
    /** One-line explanation shown by config UIs. The enum's `description` delegates here. */
    val description: String,
    /** Whether the user can toggle/reorder this section in home layout configuration. The enum delegates here. */
    val isConfigurable: Boolean,
    /** Id prefix for per-instance rows (LATEST_MEDIA `"latest_"`, PINNED `"pinned_"`); `null` for static rows. */
    private val dynamicIdPrefix: String? = null,
    /** `"{name}"` title template for per-library rows (LATEST_MEDIA `"Latest {name}"`); `null` otherwise. */
    private val dynamicTitleTemplate: String? = null,
) {

    /**
     * Resolves the row id for a per-instance section: [dynamicIdPrefix] +
     * [instanceId]. LATEST_MEDIA passes the Jellyfin library id, PINNED the
     * composite pin id (`"${type.name}_$sourceId"`).
     */
    fun idFor(instanceId: String): String =
        checkNotNull(dynamicIdPrefix) { "$type has no dynamic id prefix" } + instanceId

    /**
     * Resolves the header title for a per-library LATEST_MEDIA row by
     * substituting the library's display name into [dynamicTitleTemplate].
     * PINNED rows render the user's pin title verbatim — no template.
     */
    fun titleFor(libraryName: String): String =
        checkNotNull(dynamicTitleTemplate) { "$type has no dynamic title template" }
            .replace("{name}", libraryName)

    /**
     * Builds the single [HomeSection] row for static types (non-null [id]),
     * headed by [displayName]. Calling it for a dynamic type is a programming
     * error and fails fast — dynamic rows must resolve their identity per
     * instance via [idFor]/[titleFor] at the fetch site.
     */
    fun section(items: List<MediaItem>, seedItem: MediaItem? = null): HomeSection = HomeSection(
        id = checkNotNull(id) { "$type has no static section id" },
        title = displayName,
        type = type,
        items = items,
        seedItem = seedItem,
    )
}

/**
 * The identity descriptor for a section type. Exhaustive on purpose: adding a
 * [HomeSectionType] constant without describing it here is a compile error,
 * not a runtime gap. Built once into an immutable table so reads of
 * [HomeSectionType.descriptor] (per home refresh, per recomposition) don't
 * allocate a fresh descriptor each time.
 */
private val homeSectionDescriptors: Map<HomeSectionType, HomeSectionDescriptor> =
    HomeSectionType.entries.associateWith { type ->
        when (type) {
            HomeSectionType.CONTINUE_WATCHING -> HomeSectionDescriptor(
                type = type,
                id = "continue_watching",
                displayName = "Continue Watching",
                description = "Resume watching in-progress media",
                isConfigurable = true,
            )
            HomeSectionType.NEXT_UP -> HomeSectionDescriptor(
                type = type,
                id = "next_up",
                displayName = "Next Up",
                description = "Next unwatched episodes of your shows",
                isConfigurable = true,
            )
            HomeSectionType.RECENTLY_ADDED -> HomeSectionDescriptor(
                type = type,
                id = "recently_added",
                displayName = "Recently Added",
                description = "Recently added items across all libraries",
                isConfigurable = true,
            )
            HomeSectionType.LATEST_MEDIA -> HomeSectionDescriptor(
                type = type,
                id = null,
                displayName = "Latest Media",
                description = "Latest items from each library",
                isConfigurable = true,
                dynamicIdPrefix = "latest_",
                dynamicTitleTemplate = "Latest {name}",
            )
            HomeSectionType.FAVORITES -> HomeSectionDescriptor(
                type = type,
                id = null,
                displayName = "Favorites",
                description = "Your favorited items",
                isConfigurable = false,
            )
            HomeSectionType.LIVE_TV -> HomeSectionDescriptor(
                type = type,
                id = null,
                displayName = "Live TV",
                description = "Live television channels",
                isConfigurable = false,
            )
            HomeSectionType.DOWNLOADED -> HomeSectionDescriptor(
                type = type,
                id = null,
                displayName = "Downloaded",
                description = "Offline downloaded items",
                isConfigurable = false,
            )
            HomeSectionType.RECOMMENDATIONS -> HomeSectionDescriptor(
                type = type,
                id = "recommendations",
                displayName = "Recommended For You",
                description = "Personalized picks based on your watch history",
                isConfigurable = true,
            )
            HomeSectionType.PINNED -> HomeSectionDescriptor(
                type = type,
                id = null,
                displayName = "Pinned",
                description = "Collections and shelves you have pinned to home",
                isConfigurable = false,
                dynamicIdPrefix = "pinned_",
            )
        }
    }

val HomeSectionType.descriptor: HomeSectionDescriptor
    get() = homeSectionDescriptors.getValue(this)
