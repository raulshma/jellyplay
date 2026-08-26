package com.raulshma.jellyplay.core.data.playback

import androidx.compose.runtime.Stable
import com.raulshma.jellyplay.core.data.remote.RemotePlayableEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds the active mini-player engine + display metadata so the mini-player can be shown
 * outside the video feature (e.g. while browsing Live TV channels or libraries) without those
 * modules depending on `feature:player:video`.
 *
 * Originally declared inside `feature:player:video`, moved to `core:data`
 * to break the only feature→feature dependency in the codebase:
 * `feature:livetv → feature:player:video`. The engine reference is typed as
 * [RemotePlayableEngine] (a `core.data.remote` interface) so no `feature:player:video` import
 * leaks in here. The video `MediaEngine` implements [RemotePlayableEngine] directly.
 *
 * V3 livetv conveyor: moved into :shared:core:data commonMain (Koin-owned —
 * the @Singleton/@Inject annotations were stripped at the move, one framework
 * per type); all consumers resolve the Koin single directly (the Hilt-era
 * DataModule bridge left with the wave-8 extinction).
 */
@Stable
class VideoMiniPlayerState {
    companion object {
        // How long the video engine (MPV/LibVLC holding native memory) is
        // retained after the user dismisses the mini player, in case they
        // return to the same item. Kept short to avoid holding native
        // resources for half an hour; reclaim still works within the window.
        private const val AUTO_RELEASE_TIMEOUT_MS = 5 * 60 * 1000L
    }

    private val _isMiniMode = MutableStateFlow(false)
    val isMiniMode: StateFlow<Boolean> = _isMiniMode.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _subtitle = MutableStateFlow("")
    val subtitle: StateFlow<String> = _subtitle.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var _engine: RemotePlayableEngine? = null
    val engine: RemotePlayableEngine? get() = _engine

    private var job: Job? = null
    private var timeoutJob: Job? = null
    private val miniScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _itemId = MutableStateFlow<String?>(null)
    val itemId: StateFlow<String?> = _itemId.asStateFlow()

    private var _mediaSourceId: String? = null
    val mediaSourceId: String? get() = _mediaSourceId

    fun enterMiniMode(
        engine: RemotePlayableEngine,
        itemId: String,
        mediaSourceId: String?,
        title: String,
        subtitle: String,
    ) {
        _engine = engine
        _itemId.value = itemId
        _mediaSourceId = mediaSourceId
        _title.value = title
        _subtitle.value = subtitle
        _isPlaying.value = engine.isPlaying.value

        job?.cancel()
        timeoutJob?.cancel()
        job = miniScope.launch {
            engine.isPlaying.collect { _isPlaying.value = it }
        }
        timeoutJob = miniScope.launch {
            delay(AUTO_RELEASE_TIMEOUT_MS)
            release()
        }
        _isMiniMode.value = true
    }

    fun tryReclaimEngine(itemId: String): RemotePlayableEngine? {
        if (!_isMiniMode.value) return null
        if (_itemId.value != itemId) return null
        val engine = _engine
        job?.cancel()
        timeoutJob?.cancel()
        job = null
        timeoutJob = null
        _isMiniMode.value = false
        _engine = null
        _itemId.value = null
        _mediaSourceId = null
        return engine
    }

    fun togglePlayPause() {
        val engine = _engine ?: return
        if (engine.isPlaying.value) engine.pause() else engine.play()
    }

    fun release() {
        val engine = _engine
        job?.cancel()
        timeoutJob?.cancel()
        job = null
        timeoutJob = null
        engine?.release()
        _engine = null
        _itemId.value = null
        _mediaSourceId = null
        _isMiniMode.value = false
        _title.value = ""
        _subtitle.value = ""
        _isPlaying.value = false
    }
}
