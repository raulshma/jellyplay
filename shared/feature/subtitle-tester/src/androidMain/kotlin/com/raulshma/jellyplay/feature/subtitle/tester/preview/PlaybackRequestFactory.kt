package com.raulshma.jellyplay.feature.subtitle.tester.preview

import android.content.Context
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import com.raulshma.jellyplay.feature.subtitle.tester.SampleSubtitlePreset
import com.raulshma.jellyplay.feature.subtitle.tester.generated.resources.Res
import com.raulshma.jellyplay.feature.subtitle.tester.generated.resources.subtitle_tester_preview_title
import com.raulshma.jellyplay.feature.subtitle.tester.generated.resources.subtitle_tester_track_sample
import com.raulshma.jellyplay.shared.feature.subtitle.tester.R
import java.io.File
import org.jetbrains.compose.resources.getString

/**
 * Extracts the bundled host clip and sample subtitle raw resources to the app's
 * private files directory and returns `file://` URIs.
 *
 * Raw `android.resource://` URIs break across the three preview engines:
 *  - ExoPlayer's `RawResourceDataSource` needs an `AssetFileDescriptor`, but AAPT
 *    compresses text resources (`.srt`/`.ass`) by default — `openRawResourceFd`
 *    then throws "This file can not be opened as a file descriptor; it is
 *    probably compressed". The mp4 host clip is already stored uncompressed, so
 *    it loads fine, but the subtitle track is silently disabled.
 *  - mpv / libVLC are native players with no Java `ContentResolver` access; they
 *    log "No protocol handler found to open URL android.resource://..." and fail
 *    to open the host clip **and** the subtitle.
 *
 * Copying both resources to real files under [filesDir] gives every engine a
 * plain `file://` path it can open directly. Extraction is cached: once a
 * resource is materialized the file is reused for subsequent previews.
 *
 * (V3 conveyor note: the two label strings moved from `context.getString` to
 * compose-resources' suspend `getString` — the shared module's strings live in
 * `composeResources`, not an Android `res/values` set — which is why
 * [forPreview] is `suspend`; every caller (the ViewModel's
 * `ensureEngineLoaded`) already runs inside `viewModelScope`. The raw-resource
 * materialization mechanics above are unchanged from the legacy module.)
 */
class PlaybackRequestFactory(private val context: Context) {

    /** Subdirectory holding the extracted preview assets. */
    private val assetDir: File = File(context.filesDir, ASSET_DIR).apply { mkdirs() }

    suspend fun forPreview(
        preset: SampleSubtitlePreset,
        useAssTrack: Boolean,
    ): PlaybackRequest {
        val hostUri = materialize(R.raw.subtester_host_clip, HOST_CLIP_NAME).toUri()
        val trackResId = if (useAssTrack) preset.assResId else preset.srtResId
        val trackExt = if (useAssTrack) ASS_EXT else SRT_EXT
        val trackFile = materialize(trackResId, "${preset.id}_$trackExt")
        val mimeType = if (useAssTrack) "text/x-ssa" else "application/x-subrip"

        return PlaybackRequest(
            uri = hostUri,
            title = getString(Res.string.subtitle_tester_preview_title),
            externalSubtitles = listOf(
                SubtitleSource(
                    url = trackFile.toUri(),
                    label = getString(Res.string.subtitle_tester_track_sample),
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

    /**
     * Copies the raw resource [resId] to [assetDir]/[name] if absent, returning
     * the on-disk [File]. Existing files are reused so repeated preview loads
     * (engine/preset switches) don't re-copy.
     */
    private fun materialize(resId: Int, name: String): File {
        val target = File(assetDir, name)
        if (!target.exists()) {
            context.resources.openRawResource(resId).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return target
    }

    /**
     * Builds a `file://` URI string for [this] file. Uses [java.net.URI] rather
     * than `android.net.Uri.fromFile` so the factory stays unit-testable without
     * the Android framework (which returns null for static Uri methods under
     * Robolectric-less unit tests).
     */
    private fun File.toUri(): String {
        // File.toURI() yields "file:/..."; normalize to the "file:///" authority
        // form engines and Media3 expect.
        return toURI().toString().replaceFirst("file:/", "file:///")
    }

    private companion object {
        const val ASSET_DIR = "subtitle_tester_assets"
        const val HOST_CLIP_NAME = "subtester_host_clip.mp4"
        const val SRT_EXT = "srt"
        const val ASS_EXT = "ass"
    }
}
