package com.raulshma.jellyplay.core.data.remote

import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge for [com.raulshma.jellyplay.core.data.remote.RemoteControlDispatcher]
 * implementations to request navigation in the app without depending on Compose
 * / Navigation types beyond the [Route] key.
 *
 * The remote receivers emit [NavigationTarget] (an app-level model); the app
 * layer translates that into a [Route] in its `LaunchedEffect` collector.
 */
sealed class NavigationTarget {
    data class OpenVideoPlayer(
        val itemId: String,
        val mediaSourceId: String? = null,
        val startPositionTicks: Long = 0L,
        val audioStreamIndex: Int? = null,
        val subtitleStreamIndex: Int? = null,
    ) : NavigationTarget()

    data class OpenAudioPlayer(val itemId: String) : NavigationTarget()

    data class OpenMediaDetail(val itemId: String) : NavigationTarget()

    /**
     * Pop the active player (video or audio) and return to the previous
     * top-level destination. Emitted by remote "Stop" commands so the host
     * device closes the player UI instead of merely pausing.
     */
    data object ClosePlayer : NavigationTarget()
}

@Singleton
class RemoteNavigationBridge @Inject constructor() {
    private val _targets = MutableSharedFlow<NavigationTarget>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val targets: SharedFlow<NavigationTarget> = _targets.asSharedFlow()

    fun request(target: NavigationTarget) {
        _targets.tryEmit(target)
    }

    fun toRoute(target: NavigationTarget): Route = when (target) {
        is NavigationTarget.OpenVideoPlayer -> Route.VideoPlayer(
            itemId = target.itemId,
            mediaSourceId = target.mediaSourceId,
            startPositionTicks = target.startPositionTicks,
            audioStreamIndex = target.audioStreamIndex,
            subtitleStreamIndex = target.subtitleStreamIndex,
        )
        is NavigationTarget.OpenAudioPlayer -> Route.AudioPlayer(target.itemId)
        is NavigationTarget.OpenMediaDetail -> Route.MediaDetail(target.itemId)
        NavigationTarget.ClosePlayer -> Route.Home
    }

    /**
     * Translate the most recent [NavigationTarget] into a [Route]. Convenience
     * used by the app layer to apply navigation without depending on the
     * sealed-class API.
     */
    fun asRoute(target: NavigationTarget): Route = toRoute(target)
}
