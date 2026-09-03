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

    /**
     * Downloaded content. The split matters to the screen: only [Implicit]
     * (the fetch-failure fallback) shows the implicit-offline banner, and
     * deriving that from the same single fold — instead of re-comparing the
     * (slower, mirror-hopping) [HomeUiState.offlineMode] — keeps the banner
     * from flashing on a deliberate offline toggle while the mirror lags.
     */
    sealed interface Offline : HomeRenderSource {
        /** An offline mode is active (manual or auto) — the user's choice. */
        data object Explicit : Offline

        /**
         * An online fetch failed leaving only downloads to show — the
         * fallback nobody asked for, hence the status banner.
         */
        data object Implicit : Offline
    }

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
 * healthy fetch. A failed fetch is the implicit-offline precondition — stale
 * cached sections do NOT keep the online feed alive (they can't refresh, so
 * Continue Watching / Next Up would freeze at the pre-failure server
 * snapshot): [FallbackPending] until the first library emission proves
 * downloads exist ([Offline.Implicit]) or don't (back to [Online], where the
 * hard-error screen still requires empty sections — see
 * [homeSurface]).
 *
 * Public because [HomeUiState.renderSource] exposes it.
 */
fun computeHomeRenderSource(
    offlineMode: OfflineMode,
    fetchFailed: Boolean,
    offlineLibrary: List<OfflineMediaItem>,
    fallbackPending: Boolean,
): HomeRenderSource = when {
    offlineMode != OfflineMode.ONLINE -> HomeRenderSource.Offline.Explicit
    !fetchFailed -> HomeRenderSource.Online
    fallbackPending -> HomeRenderSource.FallbackPending
    offlineLibrary.isNotEmpty() -> HomeRenderSource.Offline.Implicit
    else -> HomeRenderSource.Online
}
