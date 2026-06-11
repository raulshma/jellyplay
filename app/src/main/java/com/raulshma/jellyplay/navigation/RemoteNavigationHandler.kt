package com.raulshma.jellyplay.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.raulshma.jellyplay.core.data.remote.NavigationTarget
import com.raulshma.jellyplay.core.data.remote.RemoteNavigationBridge
import com.raulshma.jellyplay.core.ui.navigation.NavigationState
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import androidx.navigation3.runtime.NavKey

@Composable
internal fun RemoteNavigationHandler(
    navigator: Navigator,
    navigationState: NavigationState,
    allTopLevelKeys: Set<NavKey>,
    pendingRoute: NavKey?,
    onPendingRouteConsumed: () -> Unit,
    remoteNavigationBridge: RemoteNavigationBridge,
) {
    LaunchedEffect(pendingRoute) {
        pendingRoute?.let { route ->
            if (allTopLevelKeys.contains(route)) {
                navigationState.topLevelRoute.value = route
            } else {
                navigator.navigate(route)
            }
            onPendingRouteConsumed()
        }
    }

    LaunchedEffect(remoteNavigationBridge) {
        remoteNavigationBridge.targets.collect { target ->
            when (target) {
                is NavigationTarget.ClosePlayer -> {
                    navigationState.backStacks.values.forEach { stack ->
                        while (stack.isNotEmpty()) {
                            val last = stack.last()
                            if (last is Route.VideoPlayer ||
                                last is Route.AudioPlayer ||
                                last is Route.LiveTvChannelPlayer ||
                                last is Route.OfflinePlayer
                            ) {
                                stack.removeLastOrNull()
                            } else {
                                break
                            }
                        }
                    }
                }
                else -> navigator.navigate(
                    when (target) {
                        is NavigationTarget.OpenVideoPlayer -> Route.VideoPlayer(
                            itemId = target.itemId,
                            mediaSourceId = target.mediaSourceId,
                            startPositionTicks = target.startPositionTicks,
                            audioStreamIndex = target.audioStreamIndex,
                            subtitleStreamIndex = target.subtitleStreamIndex,
                        )
                        is NavigationTarget.OpenAudioPlayer -> Route.AudioPlayer(target.itemId)
                        is NavigationTarget.OpenMediaDetail -> Route.MediaDetail(target.itemId)
                        else -> Route.Home
                    }
                )
            }
        }
    }
}
