package com.raulshma.jellyplay.feature.admin.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.feature.admin.AccessDeniedScreen

/**
 * Wraps every admin route so access control is enforced in a single place.
 * Reads the current user's admin status (kept fresh against the server by
 * [onRefreshAdmin]) and renders one of three states:
 *
 *  - loading → a brief spinner while the first admin-status refresh resolves,
 *    so a non-admin never flashes the real screen before being blocked;
 *  - admin → the wrapped [content];
 *  - non-admin → [AccessDeniedScreen].
 *
 * Because the status is read as Compose state, a mid-session server-side
 * demotion flips the visible content to the denied screen live.
 *
 * @param isAdmin reads the latest admin status from the activity-scoped
 *  MainViewModel. Passed as a function (not a value) so the navigation
 *  entry stays cheap to build and reads the live state on each composition.
 * @param isRefreshingAdmin reads the in-flight refresh flag.
 * @param onRefreshAdmin requests a server re-validation of admin status,
 *  de-duplicated inside MainViewModel.
 */
@Composable
fun AdminRouteContainer(
    onBack: () -> Unit,
    isAdmin: () -> Boolean,
    isRefreshingAdmin: () -> Boolean,
    onRefreshAdmin: () -> Unit,
    content: @Composable () -> Unit,
) {
    // Re-validate admin status against the server once when the admin area
    // is first entered (de-duplicated in MainViewModel on a 30s window).
    LaunchedEffect(Unit) { onRefreshAdmin() }

    val isAdminNow = isAdmin()
    when {
        // While a refresh is running we don't yet know the server's truth, so
        // avoid flashing denied (or content) until it settles. Initial cached
        // state is shown immediately — this only gates the transition.
        isRefreshingAdmin() && !isAdminNow -> {
            ScreenLoadingState(modifier = Modifier.fillMaxSize())
        }
        isAdminNow -> content()
        else -> AccessDeniedScreen(onBack = onBack)
    }
}
