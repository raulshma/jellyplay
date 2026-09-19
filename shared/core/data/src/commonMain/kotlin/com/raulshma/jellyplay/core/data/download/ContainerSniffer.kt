package com.raulshma.jellyplay.core.data.download

/**
 * Sniffs the real container format of a downloaded file from its magic
 * bytes, regardless of the on-disk file extension.
 *
 * Why: downloads created before the container-persistence migration carry a
 * hardcoded `.mp4` extension even when the underlying bytes are MKV/TS/FLV/AVI.
 * ExoPlayer picks its extractor from the URI extension and hangs silently on a
 * mismatch; the sniffer lets the offline playback path recover the real
 * container so the correct MIME type can be attached to the MediaItem.
 *
 * Byte-level and pure common on purpose: this module's commonMain also builds
 * for wasmJs, so the API takes already-read header bytes and all file IO stays
 * in caller-side glue (this module's jvmShared `sniffContainerFile` backs the
 * database backfill probe; player-video's PlayerSessionManager has its own
 * JVM glue for the playback fallback). Formerly lived in
 * `feature.player.video.engine` as a File-taking object — the detection logic
 * is byte-identical to that implementation.
 *
 * Returns one of the container codes recognized by the playback layer's
 * container→MIME mapping (`"mkv"`, `"webm"`, `"mp4"`, `"ts"`, `"flv"`,
 * `"avi"`), or `null` if no known magic signature matches.
 */
object ContainerSniffer {

    /** MPEG-TS packet size in bytes. */
    const val TS_PACKET_SIZE = 188

    /**
     * Header window a caller must read: MPEG-TS sync-byte detection needs at
     * least three packet boundaries (offsets 0, 188, 376) to be reliable;
     * every other signature fits in 16 bytes.
     */
    const val SNIFF_HEADER_BYTES = TS_PACKET_SIZE * 3

    /** Minimum header length that can carry a recognizable signature. */
    const val MIN_SNIFF_BYTES = 16

    /**
     * Detects the container code from the first [length] bytes of a media
     * file's header. Callers that read fewer than [MIN_SNIFF_BYTES] bytes
     * should not call this (too short to match anything reliably).
     */
    fun sniff(header: ByteArray, length: Int): String? {
        val buf = header
        val len = length
        // EBML header (Matroska/WebM): 1A 45 DF A3.
        if (len >= 4 &&
            (buf[0].toInt() and 0xFF) == 0x1A &&
            (buf[1].toInt() and 0xFF) == 0x45 &&
            (buf[2].toInt() and 0xFF) == 0xDF &&
            (buf[3].toInt() and 0xFF) == 0xA3
        ) {
            return distinguishWebm(buf, len)
        }

        // ISO BMFF (MP4/MOV/M4A): "ftyp" box at offset 4 → bytes 4-7 = "ftyp".
        if (len >= 8 &&
            buf[4] == 'f'.code.toByte() &&
            buf[5] == 't'.code.toByte() &&
            buf[6] == 'y'.code.toByte() &&
            buf[7] == 'p'.code.toByte()
        ) {
            return "mp4"
        }

        // FLV: ASCII "FLV".
        if (len >= 3 &&
            buf[0] == 'F'.code.toByte() &&
            buf[1] == 'L'.code.toByte() &&
            buf[2] == 'V'.code.toByte()
        ) {
            return "flv"
        }

        // RIFF → AVI ("RIFF"...."AVI ").
        if (len >= 12 &&
            buf[0] == 'R'.code.toByte() && buf[1] == 'I'.code.toByte() &&
            buf[2] == 'F'.code.toByte() && buf[3] == 'F'.code.toByte() &&
            buf[8] == 'A'.code.toByte() && buf[9] == 'V'.code.toByte() &&
            buf[10] == 'I'.code.toByte() && buf[11] == ' '.code.toByte()
        ) {
            return "avi"
        }

        // MPEG-TS: 0x47 sync byte at offsets 0, 188, 376.
        if (len >= TS_PACKET_SIZE * 3 &&
            (buf[0].toInt() and 0xFF) == 0x47 &&
            (buf[TS_PACKET_SIZE].toInt() and 0xFF) == 0x47 &&
            (buf[TS_PACKET_SIZE * 2].toInt() and 0xFF) == 0x47
        ) {
            return "ts"
        }

        return null
    }

    /**
     * Both WebM and MKV start with an EBML header. The DocType element
     * (`42 82 ...`) follows the EBML root and carries the string `"webm"` or
     * `"matroska"`. We only need a coarse answer, so scan the first ~24 bytes
     * for the ASCII literal `webm`; default to `mkv` otherwise.
     */
    private fun distinguishWebm(buf: ByteArray, len: Int): String {
        val window = if (len >= 32) 32 else len
        for (i in 0 until window - 4) {
            if (
                buf[i] == 'w'.code.toByte() && buf[i + 1] == 'e'.code.toByte() &&
                buf[i + 2] == 'b'.code.toByte() && buf[i + 3] == 'm'.code.toByte()
            ) {
                return "webm"
            }
        }
        return "mkv"
    }
}
