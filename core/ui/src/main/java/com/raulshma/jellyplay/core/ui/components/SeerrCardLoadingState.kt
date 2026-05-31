package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

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
 * )
 * ```
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
