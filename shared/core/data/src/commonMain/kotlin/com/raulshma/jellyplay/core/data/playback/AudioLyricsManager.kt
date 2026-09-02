package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.repository.LyricsRepository
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsSource
import com.raulshma.jellyplay.core.model.lruMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AudioLyricsManager(
    private val lyricsRepository: LyricsRepository,
) {
    private lateinit var scope: CoroutineScope

    private val _lyrics = MutableStateFlow<List<LyricsLine>>(emptyList())
    val lyrics: StateFlow<List<LyricsLine>> = _lyrics.asStateFlow()

    private val _currentLyricIndex = MutableStateFlow(-1)
    val currentLyricIndex: StateFlow<Int> = _currentLyricIndex.asStateFlow()

    private val _lyricsSource = MutableStateFlow(LyricsSource.UNKNOWN)
    val lyricsSource: StateFlow<LyricsSource> = _lyricsSource.asStateFlow()

    private val _isFetchingLyrics = MutableStateFlow(false)
    val isFetchingLyrics: StateFlow<Boolean> = _isFetchingLyrics.asStateFlow()

    /**
     * Lead/lag applied to the playback position when computing the active
     * lyric line. A positive value makes lyrics advance earlier (useful when
     * an LRCLIB match lags behind the audio). Defaults to [DEFAULT_OFFSET_MS]
     * to preserve the historic fixed lead; adjustable per-item via
     * [setLyricsOffset].
     */
    private val _lyricsOffsetMs = MutableStateFlow(DEFAULT_OFFSET_MS)
    val lyricsOffsetMs: StateFlow<Long> = _lyricsOffsetMs.asStateFlow()

    /**
     * Per-item offset memory (session-scoped, LRU-capped so an all-night
     * queue session doesn't grow it without bound). The current item id is
     * captured in [fetchLyrics]/[applyLyrics] so the offset is restored when
     * the user returns to a previously-adjusted track.
     */
    private val perItemOffsets = lruMapOf<String, Long>(MAX_REMEMBERED_OFFSETS)
    private var currentItemId: String? = null

    /**
     * Tracks the in-flight lyrics fetch so a rapid skip can cancel the
     * previous request. Without this, N concurrent fetches race and the
     * last one to resolve wins [_lyrics] — which may be for a track several
     * skips ago. See [fetchLyrics].
     */
    private var lyricsJob: Job? = null

    fun initialize(scope: CoroutineScope) {
        this.scope = scope
    }

    fun fetchLyrics(
        itemId: String,
        artistName: String?,
        trackName: String?,
        durationSec: Double?,
    ) {
        currentItemId = itemId
        restoreOffsetForItem(itemId)
        // Cancel any in-flight fetch so a rapid skip (next/next/next) cannot
        // let a slow response from an older track overwrite the current one's
        // lyrics. Cleared on the fresh launch below.
        lyricsJob?.cancel()
        lyricsJob = scope.launch {
            _isFetchingLyrics.value = true
            lyricsRepository.getLyricsWithFallback(itemId, artistName, trackName, durationSec)
                .onSuccess {
                    // Bail if the user has since moved on to another track:
                    // this response is stale and must not overwrite the
                    // current item's lyrics.
                    if (currentItemId != itemId) return@onSuccess
                    _lyrics.value = it.lines
                    _lyricsSource.value = it.source
                }
                .onFailure {
                    if (currentItemId != itemId) return@onFailure
                    _lyrics.value = emptyList()
                    _lyricsSource.value = LyricsSource.UNKNOWN
                }
            _isFetchingLyrics.value = false
        }
    }

    fun searchLyrics(query: String, callback: (Result<List<LrcLibTrack>>) -> Unit) {
        scope.launch {
            val result = lyricsRepository.searchLyrics(query)
            callback(result)
        }
    }

    fun applyLyrics(lrcLibId: Long, currentItemId: String?) {
        val itemId = currentItemId ?: return
        this.currentItemId = itemId
        restoreOffsetForItem(itemId)
        // Cancel the auto-fetch (if any) so the user's manual selection isn't
        // later clobbered by a slower in-flight fallback.
        lyricsJob?.cancel()
        lyricsJob = scope.launch {
            lyricsRepository.getLyricsById(lrcLibId, itemId)
                .onSuccess {
                    if (currentItemId != itemId) return@onSuccess
                    _lyrics.value = it.lines
                    _lyricsSource.value = it.source
                }
        }
    }

    /**
     * Sets the lyrics offset for the current item, clamped to
     * [MIN_OFFSET_MS]..[MAX_OFFSET_MS]. The value is remembered per-item for
     * the session so switching tracks and back restores the adjustment.
     */
    fun setLyricsOffset(offsetMs: Long) {
        val clamped = offsetMs.coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS)
        _lyricsOffsetMs.value = clamped
        currentItemId?.let { perItemOffsets[it] = clamped }
    }

    private fun restoreOffsetForItem(itemId: String) {
        _lyricsOffsetMs.value = perItemOffsets[itemId] ?: DEFAULT_OFFSET_MS
    }

    fun updateCurrentLyricIndex(positionMs: Long) {
        if (_lyrics.value.isNotEmpty()) {
            _currentLyricIndex.value = findCurrentLyricLine(
                _lyrics.value, positionMs + _lyricsOffsetMs.value
            )
        }
    }

    fun reset() {
        lyricsJob?.cancel()
        lyricsJob = null
        _lyrics.value = emptyList()
        _currentLyricIndex.value = -1
        _lyricsSource.value = LyricsSource.UNKNOWN
        _isFetchingLyrics.value = false
        _lyricsOffsetMs.value = DEFAULT_OFFSET_MS
        currentItemId = null
    }

    private fun findCurrentLyricLine(lines: List<LyricsLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            when {
                lines[mid].timeMs <= positionMs -> low = mid + 1
                else -> high = mid - 1
            }
        }
        return high.coerceAtLeast(-1)
    }

    companion object {
        /** Historic fixed lead baked into `updateCurrentLyricIndex`. */
        const val DEFAULT_OFFSET_MS = 300L
        const val MIN_OFFSET_MS = -500L
        const val MAX_OFFSET_MS = 500L

        /** Cap for the session-scoped per-item offset memory (see [perItemOffsets]). */
        private const val MAX_REMEMBERED_OFFSETS = 64
    }
}
