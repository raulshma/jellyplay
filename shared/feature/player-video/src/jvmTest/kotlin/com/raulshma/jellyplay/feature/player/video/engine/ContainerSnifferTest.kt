package com.raulshma.jellyplay.feature.player.video.engine

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
    private fun pad(header: ByteArray, minLen: Int = 16): ByteArray =
        if (header.size >= minLen) header else header + ByteArray(minLen - header.size)

    @Test
    fun matroskaEbmlHeader_detectedAsMkv() {
        val file = writeBytes(pad(b(0x1A, 0x45, 0xDF, 0xA3, 0x42, 0x82, 0x88, 0x6D)))
        assertEquals(ContainerSniffer.sniff(file), "mkv")
    }

    @Test
    fun webmEbmlHeader_detectedAsWebm() {
        // EBML header + DocType payload containing the literal "webm".
        val file = writeBytes(pad(b(0x1A, 0x45, 0xDF, 0xA3, 0x42, 0x82, 0x84, 0x77, 0x65, 0x62, 0x6D)))
        assertEquals(ContainerSniffer.sniff(file), "webm")
    }

    @Test
    fun mp4FtypBox_detectedAsMp4() {
        // 4-byte size + "ftyp" + major brand "isom"
        val file = writeBytes(pad(b(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6F, 0x6D)))
        assertEquals(ContainerSniffer.sniff(file), "mp4")
    }

    @Test
    fun flvHeader_detectedAsFlv() {
        val file = writeBytes(pad(b(0x46, 0x4C, 0x56, 0x01, 0x05, 0x00, 0x00, 0x00)))
        assertEquals(ContainerSniffer.sniff(file), "flv")
    }

    @Test
    fun aviRiffHeader_detectedAsAvi() {
        val file = writeBytes(pad(b(0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x41, 0x56, 0x49, 0x20)))
        assertEquals(ContainerSniffer.sniff(file), "avi")
    }

    @Test
    fun mpegTs_syncBytesAtPacketBoundaries_detectedAsTs() {
        // Sync byte 0x47 at offsets 0, 188, 376 (three TS packets).
        val buf = ByteArray(188 * 3)
        buf[0] = 0x47
        buf[188] = 0x47
        buf[376] = 0x47
        val file = writeBytes(buf)
        assertEquals(ContainerSniffer.sniff(file), "ts")
    }

    @Test
    fun mpegTs_missingThirdSyncByte_returnsNull() {
        val buf = ByteArray(188 * 3)
        buf[0] = 0x47
        buf[188] = 0x47
        // No sync byte at 376 → must not false-positive.
        val file = writeBytes(buf)
        assertNull(ContainerSniffer.sniff(file))
    }

    @Test
    fun unknownBytes_returnNull() {
        val file = writeBytes(ByteArray(64) { 0xFF.toByte() })
        assertNull(ContainerSniffer.sniff(file))
    }

    @Test
    fun tooFewBytes_returnNull() {
        // Less than the 16-byte minimum header window.
        val file = writeBytes(b(0x1A, 0x45, 0xDF, 0xA3))
        assertNull(ContainerSniffer.sniff(file))
    }

    @Test
    fun missingFile_returnNull() {
        val ghost = File(tempFolder.root, "does-not-exist")
        assertNull(ContainerSniffer.sniff(ghost))
    }

    private fun writeBytes(data: ByteArray): File {
        val file = tempFolder.newFile()
        file.writeBytes(data)
        return file
    }
}
