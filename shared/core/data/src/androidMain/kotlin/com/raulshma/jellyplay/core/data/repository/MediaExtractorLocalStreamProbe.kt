package com.raulshma.jellyplay.core.data.repository

import android.media.MediaExtractor
import android.media.MediaFormat
import com.raulshma.jellyplay.core.model.MediaStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [LocalStreamProbe] backed by the framework [MediaExtractor] (C4 part 2:
 * moved verbatim from the legacy `:core:data` `LocalStreamProbe.kt`; the
 * `@Inject` constructor annotation was stripped — Koin's
 * [com.raulshma.jellyplay.core.data.di.androidDataModule] constructs it;
 * consumers resolve the same single straight from Koin). Stateless and
 * cheap to construct; each [probe] opens and releases the extractor on
 * [Dispatchers.IO].
 */
class MediaExtractorLocalStreamProbe : LocalStreamProbe {

    override suspend fun probe(videoFilePath: String): List<MediaStream> = withContext(Dispatchers.IO) {
        runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(videoFilePath)
                buildList {
                    for (i in 0 until extractor.trackCount) {
                        extractor.getTrackFormat(i).toMediaStream(i)?.let(::add)
                    }
                }
            } finally {
                runCatching { extractor.release() }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Reads a single [MediaFormat] track and delegates to [mediaStreamFromProbe].
     * Every getter is guarded: keys are optional and a malformed track must not
     * abort the whole probe (the remaining tracks still produce badges).
     */
    private fun MediaFormat.toMediaStream(index: Int): MediaStream? {
        val mime = safeString(MediaFormat.KEY_MIME) ?: return null
        val codec = mime.substringAfter('/')
        val hasHdrStaticInfo = safeByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO) != null
        val (doViTitle, rangeType) = resolveVideoRange(codec, hasHdrStaticInfo)
        return mediaStreamFromProbe(
            index = index,
            mime = mime,
            height = safeInt(MediaFormat.KEY_HEIGHT),
            width = safeInt(MediaFormat.KEY_WIDTH),
            channels = safeInt(MediaFormat.KEY_CHANNEL_COUNT),
            sampleRate = safeInt(MediaFormat.KEY_SAMPLE_RATE),
            language = safeString(MediaFormat.KEY_LANGUAGE),
            videoDoViTitle = doViTitle,
            videoRangeType = rangeType,
        )
    }

    private fun MediaFormat.safeInt(key: String): Int? = runCatching { getInteger(key) }.getOrNull()

    private fun MediaFormat.safeString(key: String): String? = runCatching { getString(key) }.getOrNull()

    private fun MediaFormat.safeByteBuffer(key: String): java.nio.ByteBuffer? =
        runCatching { getByteBuffer(key) }.getOrNull()
}
