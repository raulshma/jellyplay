package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.OfflineMediaItem

/**
 * Which content surface the home currently renders. Computed once in
 * [HomeViewModel] (see [computeHomeRenderSource]) and carried on
 * [HomeUiState.renderSource]; the screen branches exhaustively on it instead
 * of re-deriving the offline predicate per branch, and the VM's
 * downloads-rendering gate (`isRenderingDownloads`) reads the same value —
 * one fold, one place to change the fallback rule.
 */
sealed interface HomeRenderSource {
    /** Server content: normal online browsing (including the hard-error screen). */
    data object Online : HomeRenderSource

    /** Downloaded content: explicit offline mode, or the implicit fallback —
     * an online fetch failed leaving only downloads to show. */
    data object Offline : HomeRenderSource

    /**
     * The implicit-offline gate just opened and the first offline-library
     * emission hasn't landed — downloads may yet exist. The home shows a
     * loading state and suppresses the hard error screen for this window
     * instead of flashing it.
     */
    data object FallbackPending : HomeRenderSource
}

/**
 * The single offline-render predicate every home surface shares.
 *
 * Priority order matters: explicit offline wins outright; online requires a
 * healthy fetch. A failed, empty fetch is the implicit-offline precondition —
 * [FallbackPending] until the first library emission proves downloads exist
 * ([Offline]) or don't (back to [Online], the hard-error screen).
 *
 * Public because [HomeUiState.renderSource] exposes it.
 */
fun computeHomeRenderSource(
    offlineMode: OfflineMode,
    fetchFailedEmpty: Boolean,
    offlineLibrary: List<OfflineMediaItem>,
    fallbackPending: Boolean,
): HomeRenderSource = when {
    offlineMode != OfflineMode.ONLINE -> HomeRenderSource.Offline
    !fetchFailedEmpty -> HomeRenderSource.Online
    fallbackPending -> HomeRenderSource.FallbackPending
    offlineLibrary.isNotEmpty() -> HomeRenderSource.Offline
    else -> HomeRenderSource.Online
}
