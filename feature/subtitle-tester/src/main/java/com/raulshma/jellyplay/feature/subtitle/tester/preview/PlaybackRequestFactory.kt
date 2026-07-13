package com.raulshma.jellyplay.feature.subtitle.tester.preview

import android.content.Context
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import com.raulshma.jellyplay.feature.subtitle.tester.SampleSubtitlePreset

/** Builds a minimal [PlaybackRequest] for the bundled host clip + a sample subtitle. */
object PlaybackRequestFactory {

    fun forPreview(
        context: Context,
        preset: SampleSubtitlePreset,
        useAssTrack: Boolean,
    ): PlaybackRequest {
        val packageName = context.packageName
        val hostUri = "android.resource://$packageName/${com.raulshma.jellyplay.feature.subtitle.tester.R.raw.subtester_host_clip}"
        val trackResId = if (useAssTrack) preset.assResId else preset.srtResId
        val trackUri = "android.resource://$packageName/$trackResId"
        val mimeType = if (useAssTrack) "text/x-ssa" else "application/x-subrip"

        return PlaybackRequest(
            uri = hostUri,
            title = "Subtitle Tester",
            externalSubtitles = listOf(
                SubtitleSource(
                    url = trackUri,
                    label = "sample",
                    language = null,
                    mimeType = mimeType,
                    id = "sample-subtitle",
                    isDefault = true,
                )
            ),
            // Use PlaybackRequest defaults (15s/50s). ExoPlayer's
            // DefaultLoadControl requires minBufferMs >= bufferForPlaybackAfterRebufferMs
            // (default 5000ms), so setting tight buffers here crashes on load.
        )
    }
}
