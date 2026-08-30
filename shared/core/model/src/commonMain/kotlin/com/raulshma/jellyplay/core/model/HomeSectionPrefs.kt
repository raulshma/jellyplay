package com.raulshma.jellyplay.core.model

/**
 * One snapshot of the user's home-section preferences: the fetch query (all
 * seven [HomeSectionQuery] inputs, NESTED so each is declared exactly once —
 * not mirrored field-for-field here) plus the display-only ordering rules.
 * Bundled so a prefs collector can diff and adopt an emission with a single
 * `!=` / assignment and a fetcher can consume one consistent snapshot per
 * run; adding a section preference means adding it to [HomeSectionQuery]
 * (fetch inputs) or here (display-only) — not to scattered field listings.
 *
 * This is also the home screen's section-prefs WRITE ALGEBRA: the three
 * transforms below are the single policy behind every section toggle/move —
 * the home's inline section-config sheet, Settings → Configure Libraries and
 * Settings → Appearance all write through them (via the command methods on
 * `HomeDiscoveryStore`), so no consumer holds a policy copy that can drift.
 */
data class HomeSectionPrefs(
    val query: HomeSectionQuery = HomeSectionQuery(),
    val homeSectionOrder: List<HomeSectionType> = HomeSectionType.CONFIGURABLE,
    val mergeContinueWatchingAndNextUp: Boolean = false,
) {

    /**
     * Copy with [type]'s membership in the enabled-sections set toggled —
     * the policy behind the section-visibility toggles.
     */
    fun withSectionVisible(type: HomeSectionType, visible: Boolean): HomeSectionPrefs =
        copy(query = query.copy(enabledSections = query.enabledSections.toMutableSet().apply {
            if (visible) add(type) else remove(type)
        }))

    /**
     * Copy with [type] swapped with its neighbour in the section order
     * ([up] or down), or null when no swap is possible (type absent, or
     * already at the requested edge) so callers can skip the write.
     */
    fun withSectionMoved(type: HomeSectionType, up: Boolean): HomeSectionPrefs? {
        val index = homeSectionOrder.indexOf(type)
        if (index == -1) return null
        val target = if (up) index - 1 else index + 1
        if (target !in homeSectionOrder.indices) return null
        return copy(homeSectionOrder = homeSectionOrder.toMutableList().apply {
            val removed = removeAt(index)
            add(target, removed)
        })
    }

    /**
     * Copy with [type]'s disabled-state toggled for [libraryId]. The override
     * map is keyed by library id with the DISABLED types as its value set; an
     * empty set removes the key (restoring default-enabled state).
     */
    fun withLibrarySectionVisible(
        libraryId: String,
        type: HomeSectionType,
        visible: Boolean,
    ): HomeSectionPrefs {
        val overrides = query.libraryHomeSectionOverrides.toMutableMap()
        val disabled = overrides[libraryId].orEmpty().toMutableSet()
        if (visible) disabled.remove(type) else disabled.add(type)
        if (disabled.isEmpty()) overrides.remove(libraryId) else overrides[libraryId] = disabled
        return copy(query = query.copy(libraryHomeSectionOverrides = overrides))
    }
}
