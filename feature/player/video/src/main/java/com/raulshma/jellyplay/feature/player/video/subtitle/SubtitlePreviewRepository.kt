package com.raulshma.jellyplay.feature.player.video.subtitle

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves and parses the active external subtitle track into a [TimedCue] list
 * for the subtitle-sync preview. Covers side-loaded text subs (user pick,
 * provider download, Jellyfin/OpenSubtitles/Wyzie delivery) and the text subs
 * the app side-loads alongside transcoded playback.
 *
 * NOT covered (returns null, surfaced by the UI as "preview unavailable"):
 *  - Embedded text subs during DIRECT_PLAY (demuxed by the engine; no bytes).
 *  - Image subs (PGS/VOBSUB/DVB) — not text-parseable.
 *
 * Results are memoized per [SubtitleSource.url] for the process lifetime; the
 * caller clears the cache when the active track changes via [clearCache]. The
 * memo is bounded to [MAX_CACHED_SUBTITLE_TRACKS] entries (access-order LRU) so
 * previewing many tracks over a long session can't grow it unbounded — an
 * evicted entry is simply re-parsed on next access (it's network/file-backed).
 */
@Singleton
@UnstableApi
class SubtitlePreviewRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    // Access-order LinkedHashMap: reads (get) re-order to MRU, and
    // removeEldestEntry evicts the LRU once the cap is exceeded. All access is
    // confined to withContext(Dispatchers.IO) / clearCache, matching the prior
    // mutableMapOf concurrency model.
    private val cache: MutableMap<String, List<TimedCue>> =
        object : LinkedHashMap<String, List<TimedCue>>(MAX_CACHED_SUBTITLE_TRACKS, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<TimedCue>>?): Boolean =
                size > MAX_CACHED_SUBTITLE_TRACKS
        }

    /**
     * Resolves [source] to its bytes, maps its codec/extension to a MIME type,
     * and parses it via [SubtitleParserHelper]. Returns null when the source
     * cannot be read or its format is not text-parseable.
     *
     * @param headers optional auth headers (e.g. Jellyfin `X-Emby-Token`) needed
     *  for HTTP(s) sources served by the media server.
     */
    suspend fun loadCues(source: SubtitleSource, headers: Map<String, String> = emptyMap()): List<TimedCue>? =
        withContext(Dispatchers.IO) {
            cache[source.url]?.let { return@withContext it }
            val mime = mimeFor(source) ?: run {
                Log.w(TAG, "No MIME mapping for source codec=${source.codec}; skipping preview")
                return@withContext null
            }
            val bytes = readBytes(source.url, headers) ?: run {
                Log.w(TAG, "Failed to read subtitle bytes from ${source.url}")
                return@withContext null
            }
            val cues = SubtitleParserHelper.parseSubtitles(bytes, mime)
            if (cues.isEmpty()) {
                Log.w(TAG, "Parsed 0 cues for ${source.url} (mime=$mime)")
                return@withContext null
            }
            cache[source.url] = cues
            cues
        }

    /** Clears the memoized cue list for a source (call when the active track changes). */
    fun clearCache(url: String? = null) {
        if (url == null) cache.clear() else cache.remove(url)
    }

    private fun readBytes(url: String, headers: Map<String, String>): ByteArray? = try {
        val uri = Uri.parse(url)
        when (uri.scheme?.lowercase()) {
            "file" -> {
                // Uri.fromFile() on Windows emits "file://C%3A%5C..." — the drive
                // letter and separators are percent-encoded, so a re-parse sees
                // no literal '/' and reports an EMPTY path (the whole tail
                // becomes the authority). Fall back to decoding the URL tail in
                // that case; on Android the parsed path is always non-empty.
                val rawPath = uri.path?.takeIf { it.isNotEmpty() }
                    ?: Uri.decode(url.removePrefix("file://"))
                File(rawPath).readBytes()
            }
            "content" -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            "http", "https" -> {
                val builder = Request.Builder().url(url)
                headers.forEach { (k, v) -> builder.header(k, v) }
                okHttpClient.newCall(builder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.bytes()
                }
            }
            else -> null
        }
    } catch (e: Exception) {
        Log.w(TAG, "Error reading subtitle $url", e)
        null
    }

    /**
     * Maps a [SubtitleSource] to the MIME type expected by Media3's
     * [androidx.media3.extractor.text.DefaultSubtitleParserFactory]. Prefers an
     * explicit mime on the source, then the codec, then the URL extension.
     * Note: ASS/SSA is not handled here (DefaultSubtitleParserFactory does not
     * cover it); the caller treats a null return as "preview unavailable".
     */
    private fun mimeFor(source: SubtitleSource): String? {
        source.mimeType?.let { return it }
        val codec = source.codec?.lowercase()
        TEXT_MIME_BY_FORMAT[codec]?.let { return it }
        // ASS/SSA is explicitly unsupported: do not fall through to the URL
        // extension (an .ass source with a codec-less URL must stay null).
        if (codec in UNSUPPORTED_CODECS) return null
        return TEXT_MIME_BY_FORMAT[source.url.substringAfterLast('.', "").lowercase()]
    }

    private companion object {
        const val TAG = "SubtitlePreviewRepo"

        /** Max parsed-track lists held in the memo; LRU-evicted beyond this. */
        const val MAX_CACHED_SUBTITLE_TRACKS = 8

        /** Codec / file-extension names → Media3 text MIME types. */
        val TEXT_MIME_BY_FORMAT = mapOf(
            "srt" to "application/x-subrip",
            "subrip" to "application/x-subrip",
            "vtt" to "text/vtt",
            "webvtt" to "text/vtt",
            "ttml" to "application/ttml+xml",
            "dfxp" to "application/ttml+xml",
        )

        /** Codecs that DefaultSubtitleParserFactory cannot parse (no MIME mapping). */
        val UNSUPPORTED_CODECS = setOf("ass", "ssa")
    }
}
