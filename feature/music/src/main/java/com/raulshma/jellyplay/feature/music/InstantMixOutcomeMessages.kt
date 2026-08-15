package com.raulshma.jellyplay.feature.music

import android.content.Context
import com.raulshma.jellyplay.core.data.playback.AudioQueueOutcome

/**
 * Shared instant-mix outcome → message mapping for the music detail screens
 * (album / artist). Both screens resolve the outcome identically — the
 * localized empty-mix string and the cause-message fallback — so the mapping
 * lives here once. Returns null for [AudioQueueOutcome.Started] (implicit:
 * playback started) and [AudioQueueOutcome.Suppressed] (guard veto, silent by
 * design) — the caller treats null as "no error".
 */
internal fun AudioQueueOutcome.toMixErrorMessage(context: Context): String? = when (this) {
    AudioQueueOutcome.Empty -> context.getString(R.string.music_mix_unavailable)
    is AudioQueueOutcome.Failed -> cause.message ?: "Failed to start Instant Mix"
    else -> null
}
