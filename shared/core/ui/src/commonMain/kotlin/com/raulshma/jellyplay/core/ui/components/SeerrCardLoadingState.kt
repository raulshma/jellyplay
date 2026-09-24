package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Manages the per-card loading state for SeerrMediaCard click animations.
 *
 * The card shows a unique loading animation while the caller pre-fetches data.
 * Once loading completes, the caller navigates to the detail page.
 *
 * Usage:
 * ```
 * val loadingState = rememberSeerrCardLoadingState()
 *
 * SeerrMediaCard(
 *     item = item,
 *     imageUrl = posterUrl,
 *     isLoading = loadingState.isLoading(item.id),
 *     onClick = {
 *         loadingState.startLoading(item.id)
 *         scope.launch {
 *             // pre-fetch data...
 *             loadingState.stopLoading(item.id)
 *             onNavigate(Route.SeerrDetail(item.id, item.mediaType))
 *         }
 *     },
 *)
 * ```
 *
 * Callers usually shouldn't orchestrate that by hand: [seerrCardClickHandler]
 * builds the card onClick cascade, and [ProvideSeerrCardPrefetching] wires up
 * and provides both primitives below a screen root.
 */
@Stable
interface SeerrCardLoadingState {
    /** Whether the given card is currently in its loading/prefetch animation state. */
    fun isLoading(itemId: Int): Boolean

    /** Mark a card as loading. */
    fun startLoading(itemId: Int)

    /** Mark a card as done loading. */
    fun stopLoading(itemId: Int)
}

/**
 * Callback type for pre-fetching Seerr detail data.
 * Parameters: (tmdbId, mediaType, onDone)
 */
typealias SeerrPrefetchCallback = (Int, String, () -> Unit) -> Unit

/**
 * CompositionLocal that provides a prefetch callback for Seerr items.
 * Screens that have access to a SeerrRepository should provide this
 * so that SeerrMediaCard instances can trigger data prefetch.
 */
val LocalSeerrPrefetch = compositionLocalOf<SeerrPrefetchCallback?> { null }

/**
 * CompositionLocal that provides the [SeerrCardLoadingState] for
 * descendant SeerrMediaCard instances.
 */
val LocalSeerrCardLoadingState = compositionLocalOf<SeerrCardLoadingState?> { null }

@Composable
fun rememberSeerrCardLoadingState(): SeerrCardLoadingState {
    return remember { SeerrCardLoadingStateImpl() }
}

/**
 * Builds a SeerrMediaCard onClick that runs the prefetch cascade: mark the
 * card as loading, pre-fetch the detail via [prefetch], clear the loading
 * mark, then navigate. When either primitive is missing (the card renders
 * outside a prefetch-providing screen) the card navigates immediately.
 *
 * Navigation stays feature-owned — pass the route invocation as [navigate],
 * so this helper needs no knowledge of the app's navigation graph.
 *
 * Deduplicates the startLoading -> prefetch { stopLoading; navigate } cascade
 * that previously lived inline in both `SeerrHorizontalSection`
 * (SeerrDetailScreen) and `SeerrItemsRow` (MediaDetailBody).
 */
fun seerrCardClickHandler(
    loadingState: SeerrCardLoadingState?,
    prefetch: SeerrPrefetchCallback?,
    id: Int,
    mediaType: String,
    navigate: () -> Unit,
): () -> Unit = {
    if (loadingState != null && prefetch != null) {
        loadingState.startLoading(id)
        prefetch(id, mediaType) {
            loadingState.stopLoading(id)
            navigate()
        }
    } else {
        navigate()
    }
}

/**
 * Wraps a feature's raw Seerr detail prefetch (typically a ViewModel call) in
 * the card-loading choreography — start loading, run the prefetch, stop
 * loading, then signal completion — and provides the resulting
 * [SeerrPrefetchCallback] and its [SeerrCardLoadingState] to [content] via
 * [LocalSeerrPrefetch] and [LocalSeerrCardLoadingState].
 *
 * Screens that own a Seerr-capable ViewModel call this once at their root
 * instead of hand-rolling the callback + provider pair per feature. The
 * provided callback instance is stable across recompositions (the latest
 * [prefetchDetail] lambda is always invoked), so readers of
 * [LocalSeerrPrefetch] don't recompose unnecessarily.
 */
@Composable
fun ProvideSeerrCardPrefetching(
    prefetchDetail: (tmdbId: Int, mediaType: String, onDone: () -> Unit) -> Unit,
    content: @Composable () -> Unit,
) {
    val loadingState = rememberSeerrCardLoadingState()
    val currentPrefetch by rememberUpdatedState(prefetchDetail)
    val prefetch: SeerrPrefetchCallback = remember(loadingState) {
        { tmdbId, mediaType, onDone ->
            loadingState.startLoading(tmdbId)
            currentPrefetch(tmdbId, mediaType) {
                loadingState.stopLoading(tmdbId)
                onDone()
            }
        }
    }
    CompositionLocalProvider(
        LocalSeerrPrefetch provides prefetch,
        LocalSeerrCardLoadingState provides loadingState,
    ) {
        content()
    }
}

private class SeerrCardLoadingStateImpl : SeerrCardLoadingState {

    private val loadingIds = mutableStateListOf<Int>()

    override fun isLoading(itemId: Int): Boolean = itemId in loadingIds

    override fun startLoading(itemId: Int) {
        if (itemId !in loadingIds) loadingIds.add(itemId)
    }

    override fun stopLoading(itemId: Int) {
        loadingIds.remove(itemId)
    }
}
