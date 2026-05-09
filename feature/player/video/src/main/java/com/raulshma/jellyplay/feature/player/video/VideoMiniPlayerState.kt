package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoMiniPlayerState @Inject constructor() {
    private val _isMiniMode = MutableStateFlow(false)
    val isMiniMode: StateFlow<Boolean> = _isMiniMode.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _subtitle = MutableStateFlow("")
    val subtitle: StateFlow<String> = _subtitle.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var _engine: PlayerEngine? = null
    val engine: PlayerEngine? get() = _engine

    private val _itemId = MutableStateFlow<String?>(null)
    val itemId: StateFlow<String?> = _itemId.asStateFlow()

    private var _mediaSourceId: String? = null
    val mediaSourceId: String? get() = _mediaSourceId

    fun enterMiniMode(
        engine: PlayerEngine,
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
        _isPlaying.value = engine.isPlaying
        engine.setOnStateChanged { playing ->
            _isPlaying.value = playing
        }
        _isMiniMode.value = true
    }

    fun tryReclaimEngine(itemId: String): PlayerEngine? {
        if (!_isMiniMode.value) return null
        if (_itemId.value != itemId) return null
        val engine = _engine
        _isMiniMode.value = false
        _engine = null
        _itemId.value = null
        _mediaSourceId = null
        return engine
    }

    fun togglePlayPause() {
        val engine = _engine ?: return
        if (engine.isPlaying) engine.pause() else engine.play()
    }

    fun release() {
        val engine = _engine
        engine?.setOnStateChanged(null)
        engine?.setOnTracksChanged(null)
        engine?.setOnPlaybackStateChanged(null)
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
