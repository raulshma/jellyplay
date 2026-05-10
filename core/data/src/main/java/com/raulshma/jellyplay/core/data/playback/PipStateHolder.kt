package com.raulshma.jellyplay.core.data.playback

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PipStateHolder @Inject constructor() {
    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    private val _shouldAutoEnterPip = MutableStateFlow(false)
    val shouldAutoEnterPip: StateFlow<Boolean> = _shouldAutoEnterPip.asStateFlow()

    private val _pipDismissed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val pipDismissed: SharedFlow<Unit> = _pipDismissed.asSharedFlow()

    fun setPipMode(inPip: Boolean) {
        _isInPipMode.value = inPip
    }

    fun requestAutoEnterPip(shouldEnter: Boolean) {
        _shouldAutoEnterPip.value = shouldEnter
    }

    fun notifyPipDismissed() {
        _pipDismissed.tryEmit(Unit)
    }
}
