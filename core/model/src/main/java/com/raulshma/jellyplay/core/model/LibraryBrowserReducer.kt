package com.raulshma.jellyplay.core.model

/**
 * Pure transitions for [LibraryBrowserState]. No Android, no DataStore, no
 * coroutines — the deep module: one interface, one place to test the invariants.
 *
 * Each `reduceX` returns a new immutable state; it does **not** write to disk.
 * Persistence is a side effect the VM performs *after* a transition, gated by
 * [shouldPersistPerFolder] — a single boolean derived from state, not a
 * hand-written guard at each site.
 *
 * The precedence rule ([resolveViewMode]) is computed in **one** place. This
 * replaces the two divergent derivation paths the old VM had:
 *  - the async collector (`loadViewMode()`) — full 3-tier rule;
 *  - the sync `selectFolder()` — only per-folder or global, **omitting**
 *    `defaultViewMode()`.
 * `selectFolder` here applies the full 3-tier rule, making the old divergence
 * an asserted invariant (see `LibraryBrowserReducerTest`).
 */
object LibraryBrowserReducer {

    /**
     * Resolve view-mode precedence: per-folder override > collectionType default
     * (via [LibraryFolder.defaultViewMode]) > global.
     *
     * Per-folder overrides are ignored while a section (See-All deep-link) is
     * active: the synthetic section folder's id must never be read as a real
     * library's saved view mode.
     */
    fun resolveViewMode(
        current: LibraryBrowserState,
        perFolderOverride: LibraryViewMode?,
        globalDefault: LibraryViewMode,
    ): LibraryViewMode = when {
        perFolderOverride != null && !current.isSection -> perFolderOverride
        current.folder?.defaultViewMode() != null -> current.folder.defaultViewMode()!!
        else -> globalDefault
    }

    /**
     * Select a (real or synthetic) folder. Decoding saved filters/sort from the
     * persisted blob is the caller's job (it owns the store + Json) — pass the
     * decoded [filters] in. View mode is recomputed via [resolveViewMode] so a
     * folder with a collectionType default (e.g. music → LIST) is honoured even
     * with no explicit saved override — the divergence the old `selectFolder()`
     * had is fixed here.
     */
    fun selectFolder(
        current: LibraryBrowserState,
        folder: LibraryFolder?,
        filters: LibraryFilters,
        perFolderOverride: LibraryViewMode?,
        globalDefault: LibraryViewMode,
    ): LibraryBrowserState {
        val next = current.copy(folder = folder, filters = filters, sectionContext = null, title = null)
        return next.copy(viewMode = resolveViewMode(next, perFolderOverride, globalDefault))
    }

    /**
     * Enter a home-section "See-All" deep-link. Scopes to the section's synthetic
     * folder + pre-applied sort/media-type filter, and marks the state as a
     * section so per-folder persistence is gated off.
     */
    fun configureSection(current: LibraryBrowserState, ctx: LibrarySectionContext): LibraryBrowserState {
        val folder = ctx.parentId?.let {
            LibraryFolder(id = it, name = ctx.title, collectionType = ctx.collectionType)
        }
        val sectionSort = ctx.sortBy?.let { api ->
            SortOption.entries.firstOrNull { it.apiValue == api || it.name == api }
        } ?: SortOption.DATE_ADDED
        // "See All" from a home Latest row mirrors the default library tab view
        // (top-level items) and differs only in sort order (latest first).
        // Explicit ctx.mediaTypes (if passed) still win.
        val filters = LibraryFilters(sortBy = sectionSort, mediaTypes = ctx.mediaTypes)
        return current.copy(
            folder = folder,
            title = ctx.title,
            filters = filters,
            sectionContext = ctx,
        )
    }

    /**
     * Tear down section mode and return to the default browsing view. Idempotent
     * (a no-op when [LibraryBrowserState.isSection] is already false).
     */
    fun clearSectionMode(current: LibraryBrowserState): LibraryBrowserState {
        if (!current.isSection) return current
        return current.copy(
            sectionContext = null,
            title = null,
            folder = null,
            filters = LibraryFilters(),
        )
    }

    /** Explicit user view-mode tap. Does not touch persistence. */
    fun setViewMode(current: LibraryBrowserState, mode: LibraryViewMode): LibraryBrowserState =
        current.copy(viewMode = mode)

    /** Replace the active filters (e.g. from the filter sheet). Does not persist. */
    fun updateFilters(current: LibraryBrowserState, filters: LibraryFilters): LibraryBrowserState =
        current.copy(filters = filters)

    /** Change the client-side grouping dimension. Does not persist. */
    fun setGroupBy(current: LibraryBrowserState, groupBy: GroupBy): LibraryBrowserState =
        current.copy(groupBy = groupBy)

    /** Change the poster-size multiplier. Does not persist. */
    fun setPosterSize(current: LibraryBrowserState, size: Float): LibraryBrowserState =
        current.copy(posterSize = size)

    /**
     * Reset the whole browser to defaults: clear filters, drop the folder + title,
     * restore poster size / grouping, and recompute the view mode from the global
     * default. Any active section is torn down. Returns whether the reset touched
     * a real (non-section) folder so the caller can persist a clean slate for it.
     */
    fun resetToDefault(
        current: LibraryBrowserState,
        globalDefault: LibraryViewMode,
    ): ResetResult {
        val wasInSection = current.isSection
        val realFolderBeforeReset = current.folder
        val next = LibraryBrowserState(
            viewMode = globalDefault,
            posterSize = DEFAULT_POSTER_SIZE,
            groupBy = GroupBy.NONE,
        )
        // Persist a clean slate for the real folder the user was viewing (never a
        // synthetic section parentId — that must never be written as a library key).
        val folderToClean = if (!wasInSection) realFolderBeforeReset?.id else null
        return ResetResult(state = next, realFolderIdToClean = folderToClean)
    }

    data class ResetResult(
        val state: LibraryBrowserState,
        /** Non-null only when the reset cleared a real (non-section) folder. */
        val realFolderIdToClean: String?,
    )

    /**
     * Does this transition deserve a per-folder write? False for synthetic section
     * folders and when no folder is selected. Replaces the hand-written
     * `_sectionContext.value == null` guard at each persist site.
     */
    fun shouldPersistPerFolder(state: LibraryBrowserState): Boolean =
        !state.isSection && state.folder != null
}
