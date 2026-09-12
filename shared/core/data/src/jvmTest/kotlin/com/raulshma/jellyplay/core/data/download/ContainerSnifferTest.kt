package com.raulshma.jellyplay.core.data.download

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Rule
import kotlin.test.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ContainerSnifferTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun b(vararg ints: Int): ByteArray = ByteArray(ints.size) { ints[it].toByte() }

    /**
     * Pads a header to the sniffer's minimum sniffable length (16 bytes) with
     * zeros so the fixture exercises the magic-byte logic rather than the
     * too-short-header early-return. Real media files are kilobytes minimum.
     */
    private fun pad(header: ByteArray, minLen: Int = ContainerSniffer.MIN_SNIFF_BYTES): ByteArray =
        if (header.size >= minLen) header else header + ByteArray(minLen - header.size)

    private fun sniff(header: ByteArray): String? = ContainerSniffer.sniff(header, header.size)

    @Test
    fun matroskaEbmlHeader_detectedAsMkv() {
        assertEquals("mkv", sniff(pad(b(0x1A, 0x45, 0xDF, 0xA3, 0x42, 0x82, 0x88, 0x6D))))
    }

    @Test
    fun webmEbmlHeader_detectedAsWebm() {
        // EBML header + DocType payload containing the literal "webm".
        assertEquals("webm", sniff(pad(b(0x1A, 0x45, 0xDF, 0xA3, 0x42, 0x82, 0x84, 0x77, 0x65, 0x62, 0x6D))))
    }

    @Test
    fun mp4FtypBox_detectedAsMp4() {
        // 4-byte size + "ftyp" + major brand "isom"
        assertEquals("mp4", sniff(pad(b(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6F, 0x6D))))
    }

    @Test
    fun flvHeader_detectedAsFlv() {
        assertEquals("flv", sniff(pad(b(0x46, 0x4C, 0x56, 0x01, 0x05, 0x00, 0x00, 0x00))))
    }

    @Test
    fun aviRiffHeader_detectedAsAvi() {
        assertEquals("avi", sniff(pad(b(0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x41, 0x56, 0x49, 0x20))))
    }

    @Test
    fun mpegTs_syncBytesAtPacketBoundaries_detectedAsTs() {
        // Sync byte 0x47 at offsets 0, 188, 376 (three TS packets).
        val buf = ByteArray(188 * 3)
        buf[0] = 0x47
        buf[188] = 0x47
        buf[376] = 0x47
        assertEquals("ts", sniff(buf))
    }

    @Test
    fun mpegTs_missingThirdSyncByte_returnsNull() {
        val buf = ByteArray(188 * 3)
        buf[0] = 0x47
        buf[188] = 0x47
        // No sync byte at 376 → must not false-positive.
        assertNull(sniff(buf))
    }

    @Test
    fun unknownBytes_returnNull() {
        assertNull(sniff(ByteArray(64) { 0xFF.toByte() }))
    }

    @Test
    fun tooFewBytes_returnNull() {
        // Fewer bytes than the shortest signature (EBML needs 4): the
        // byte-level API has no minimum-length policy of its own — the
        // 16-byte floor is glue-owned (see fileGlue_tooShortFile_returnsNull).
        assertNull(sniff(b(0x1A, 0x45, 0xDF)))
    }

    // ── jvmShared file glue (backs the database backfill probe) ───────────

    @Test
    fun fileGlue_readsHeaderAndDelegates() {
        val file = writeBytes(pad(b(0x1A, 0x45, 0xDF, 0xA3, 0x42, 0x82, 0x88, 0x6D)))
        assertEquals("mkv", sniffContainerFile(file.absolutePath))
    }

    @Test
    fun fileGlue_tooShortFile_returnsNull() {
        val file = writeBytes(b(0x1A, 0x45, 0xDF, 0xA3))
        assertNull(sniffContainerFile(file.absolutePath))
    }

    @Test
    fun fileGlue_missingFile_returnNull() {
        val ghost = File(tempFolder.root, "does-not-exist")
        assertNull(sniffContainerFile(ghost.absolutePath))
    }

    private fun writeBytes(data: ByteArray): File {
        val file = tempFolder.newFile()
        file.writeBytes(data)
        return file
    }
}
