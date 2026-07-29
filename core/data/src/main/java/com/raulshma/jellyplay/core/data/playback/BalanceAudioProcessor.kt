package com.raulshma.jellyplay.core.data.playback

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi

/**
 * Left/right balance via per-channel gain. Stereo (and surround) only —
 * mono input deactivates. All channels start at unity: this processor only
 * applies L/R balance, not cross-channel mixing. Any downmix, upmix, or
 * surround attenuation belongs to [ChannelMixAudioProcessor].
 *
 * Buffer scaffolding lives in [BasePcmAudioProcessor]; this class is just the
 * balance gain math. Output format is identical to input (passthrough processor).
 *
 * Note: pre-extraction, the 16-bit path multiplied raw shorts directly (no
 * normalize/denormalize round-trip). The base normalizes to float and back,
 * which changes quantization by one ULP on gain change — audibly identical
 * for a balance adjustment and buys ~80 LOC of shared scaffolding.
 */
@UnstableApi
class BalanceAudioProcessor : BasePcmAudioProcessor() {

    private var pendingBalance: Float = 0f
    private var leftGain: Float = 1f
    private var rightGain: Float = 1f
    @Volatile private var active: Boolean = false
    private var channelGains: FloatArray = FloatArray(2) { 1f }
    private var combinedGains: FloatArray = channelGains

    @Synchronized
    fun setBalance(balance: Float) {
        pendingBalance = balance.coerceIn(-1f, 1f)
        updateGains()
        active = pendingBalance != 0f
    }

    @Synchronized
    fun getBalance(): Float = pendingBalance

    override fun computeIsActive(): Boolean = active

    override fun onConfigure(format: AudioProcessor.AudioFormat) {
        if (format.channelCount < 2) {
            // Mono: balance is meaningless — deactivate.
            active = false
            return
        }
        channelGains = FloatArray(format.channelCount) { 1f }
        updateGains()
    }

    override fun processFloatSample(sample: Float, channelIndex: Int): Float {
        // No clamp on float output — balance gain ≤ 1.0 never overdrives.
        return sample * combinedGains[channelIndex]
    }

    private fun updateGains() {
        if (pendingBalance >= 0f) {
            leftGain = 1f - pendingBalance
            rightGain = 1f
        } else {
            leftGain = 1f
            rightGain = 1f + pendingBalance
        }
        rebuildCombinedGains()
    }

    private fun rebuildCombinedGains() {
        val channelCount = channelGains.size
        if (combinedGains.size != channelCount) {
            combinedGains = FloatArray(channelCount)
        }
        for (ch in 0 until channelCount) {
            val balanceGain = balanceGainFor(ch)
            combinedGains[ch] = balanceGain * channelGains[ch]
        }
    }

    private fun balanceGainFor(ch: Int): Float =
        if (ch == 0 || ch == 2 || ch == 4 || ch == 6) leftGain else rightGain
}
