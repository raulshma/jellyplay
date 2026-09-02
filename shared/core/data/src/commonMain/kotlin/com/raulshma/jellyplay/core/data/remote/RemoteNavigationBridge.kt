package com.raulshma.jellyplay.core.data.remote

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

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

    data object ClosePlayer : NavigationTarget()
}

class RemoteNavigationBridge() {
    private val _targets = MutableSharedFlow<NavigationTarget>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val targets: SharedFlow<NavigationTarget> = _targets.asSharedFlow()

    fun request(target: NavigationTarget) {
        _targets.tryEmit(target)
    }
}
