package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.Visualizer
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioVisualizerHelper(
    private val visualizerFactory: (Int) -> Visualizer = ::defaultVisualizer,
) {

    private var visualizer: Visualizer? = null
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    private val _fftData = MutableStateFlow(ByteArray(0))
    val fftData: StateFlow<ByteArray> = _fftData.asStateFlow()

    private val _waveformData = MutableStateFlow(ByteArray(0))
    val waveformData: StateFlow<ByteArray> = _waveformData.asStateFlow()

    private var lastFft: ByteArray? = null
    private var lastWaveform: ByteArray? = null
    private var lastFftUpdateTime = 0L
    private var lastWaveformUpdateTime = 0L

    var isEnabled: Boolean = false
        private set

    fun attach(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        if (audioSessionId == currentAudioSessionId && visualizer != null) return

        detach()
        currentAudioSessionId = audioSessionId

        visualizer = try {
            visualizerFactory(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[0]
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int,
                        ) {
                            if (waveform != null) {
                                // Throttle FIRST: the Visualizer fires callbacks
                                // at maxCaptureRate/4 (tens of times per second),
                                // so gating on the cheap elapsed-time check
                                // before the byte-by-byte contentEquals avoids
                                // an O(n) array compare on every audio-thread
                                // callback. Only when enough time has elapsed do
                                // we spend the cycles to confirm the frame
                                // actually changed.
                                val now = SystemClock.elapsedRealtime()
                                if (now - lastWaveformUpdateTime < 33) return
                                if (waveform.contentEquals(lastWaveform)) return
                                lastWaveformUpdateTime = now
                                lastWaveform = waveform
                                _waveformData.value = waveform
                            }
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int,
                        ) {
                            if (fft != null) {
                                // Throttle FIRST (same rationale as waveform):
                                // previously contentEquals ran on every callback
                                // before the time check, doing a full byte
                                // comparison on the audio thread each fire.
                                val now = SystemClock.elapsedRealtime()
                                if (now - lastFftUpdateTime < 33) return
                                if (fft.contentEquals(lastFft)) return
                                lastFftUpdateTime = now
                                lastFft = fft
                                _fftData.value = fft
                            }
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 4,
                    true,
                    true,
                )
                enabled = isEnabled
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create Visualizer", e)
            null
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        visualizer?.enabled = enabled
        if (!enabled) {
            _fftData.value = ByteArray(0)
            _waveformData.value = ByteArray(0)
        }
    }

    fun detach() {
        visualizer?.enabled = false
        visualizer?.release()
        visualizer = null
        currentAudioSessionId = C.AUDIO_SESSION_ID_UNSET
        _fftData.value = ByteArray(0)
        _waveformData.value = ByteArray(0)
    }

    companion object {
        fun defaultVisualizer(audioSessionId: Int): Visualizer = Visualizer(audioSessionId)
        private const val TAG = "AudioVisualizerHelper"
    }
}
