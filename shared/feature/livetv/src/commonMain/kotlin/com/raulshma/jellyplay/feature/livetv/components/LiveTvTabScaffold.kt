package com.raulshma.jellyplay.feature.livetv.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.PagedCollectionRung
import com.raulshma.jellyplay.core.ui.components.PullToRefreshBox
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.message.UiMessage
import com.raulshma.jellyplay.core.ui.message.asText
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The tab-ladder rung vocabulary rides core:ui's `PagedCollectionRung` (the
 * shared InitialLoading/RefreshError/Empty/Content seal), decided with the
 * paged ladder's precedence: an error over an empty tab wins over the
 * spinner, both are gated on emptiness — a pull-to-refresh or re-sort over
 * live content never blanks it. (This is exactly core:ui's
 * `pagedCollectionRung` decision shape; the livetv tabs are list-sourced with
 * a [UiMessage] error, so mapping the state onto the paging inputs would be
 * artificial — the precedence is pinned by `LiveTvTabLadderTest` to stay
 * byte-equal.)
 */
internal fun liveTvTabRung(
    isLoading: Boolean,
    hasError: Boolean,
    isEmpty: Boolean,
): PagedCollectionRung = when {
    hasError && isEmpty -> PagedCollectionRung.RefreshError
    isLoading && isEmpty -> PagedCollectionRung.InitialLoading
    isEmpty -> PagedCollectionRung.Empty
    else -> PagedCollectionRung.Content
}

/**
 * The ONE load ladder behind the Live TV host tabs (Programs, Channels,
 * Recordings, Schedule, Series — the former five hand-copied `when` ladders,
 * whose drift included a Channels tab with NO loading rung at all). Renders
 * the four rungs off the tab's state:
 *
 * - initial load → the shared [ScreenLoadingState];
 * - load failure over an empty tab → the shared [ErrorScreen] ([error]
 *   resolved with [UiMessage.asText], retry wired to [onRefresh]);
 * - settled-empty → the shared [ScreenEmptyState];
 * - content → [PullToRefreshBox]([isRefreshing], [onRefresh]) around
 *   [content] — the refresh affordance exists only on the content rung, as
 *   the hand-copied ladders had it.
 *
 * Per-tab guard variants stay call-site facts: [isEmpty] is a boolean, so
 * Schedule's two-list shape passes the compound
 * `activeRecordings.isEmpty() && upcomingGroups.isEmpty()`. The full-screen
 * rungs render with [Modifier.fillMaxSize]; the screens' own scaffolds, focus
 * grabs and dialogs stay around the call.
 */
@Composable
fun LiveTvTabScaffold(
    isLoading: Boolean,
    error: UiMessage?,
    isEmpty: Boolean,
    emptyIcon: ImageVector,
    emptyTitleRes: StringResource,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val e = error
    when (liveTvTabRung(isLoading = isLoading, hasError = e != null, isEmpty = isEmpty)) {
        PagedCollectionRung.InitialLoading -> ScreenLoadingState(modifier = modifier.fillMaxSize())
        PagedCollectionRung.RefreshError -> if (e != null) ErrorScreen(
            // RefreshError is reachable only when [e] is non-null (it feeds
            // hasError above); the check gives the compiler the smart cast.
            message = e.asText(),
            onRetry = onRefresh,
            modifier = modifier,
        )
        PagedCollectionRung.Empty -> ScreenEmptyState(
            icon = emptyIcon,
            title = stringResource(emptyTitleRes),
            modifier = modifier,
        )
        PagedCollectionRung.Content -> PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier,
        ) {
            content()
        }
    }
}
