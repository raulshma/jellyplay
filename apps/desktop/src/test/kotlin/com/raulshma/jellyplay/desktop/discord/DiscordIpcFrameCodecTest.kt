package com.raulshma.jellyplay.desktop.discord

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the hand-rolled Discord IPC frame layer byte-for-byte (feature 4.2):
 * the 8-byte little-endian header (`op:int32 LE` + `length:int32 LE`) and the
 * UTF-8 payload, the fail-soft decode (short/truncated/oversized input →
 * null), and the pure handshake payload shape. No pipe involved.
 */
class DiscordIpcFrameCodecTest {

    @Test
    fun header_isTwoLittleEndianInt32s() {
        val bytes = DiscordIpcFrameCodec.encode(DiscordIpcFrame(DiscordIpcOpCode.OP_HANDSHAKE, "{}"))
        assertEquals(8 + 2, bytes.size)
        // op = 0 → [00, 00, 00, 00]
        for (i in 0..3) assertEquals(0, bytes[i].toInt() and 0xFF, "op byte $i")
        // length = 2 → [02, 00, 00, 00]
        assertEquals(2, bytes[4].toInt() and 0xFF)
        for (i in 5..7) assertEquals(0, bytes[i].toInt() and 0xFF, "length byte $i")
        assertEquals('{'.code, bytes[8].toInt())
        assertEquals('}'.code, bytes[9].toInt())
    }

    @Test
    fun opcode_encodesLittleEndian() {
        val bytes = DiscordIpcFrameCodec.encode(DiscordIpcFrame(DiscordIpcOpCode.OP_PONG, ""))
        assertEquals(DiscordIpcOpCode.OP_PONG, bytes[0].toInt() and 0xFF)
        for (i in 1..3) assertEquals(0, bytes[i].toInt() and 0xFF, "op byte $i")
    }

    @Test
    fun encodeThenDecode_roundTripsEveryOpcode() {
        for (op in listOf(
            DiscordIpcOpCode.OP_HANDSHAKE,
            DiscordIpcOpCode.OP_FRAME,
            DiscordIpcOpCode.OP_CLOSE,
            DiscordIpcOpCode.OP_PING,
            DiscordIpcOpCode.OP_PONG,
        )) {
            val payload = """{"cmd":"PING","nonce":"abc"}"""
            val decoded = DiscordIpcFrameCodec.decode(DiscordIpcFrameCodec.encode(DiscordIpcFrame(op, payload)))
            assertNotNull(decoded, "opcode $op must round-trip")
            assertEquals(op, decoded.opCode)
            assertEquals(payload, decoded.payload)
        }
    }

    @Test
    fun decode_ofAMultiByteLengthFrame_roundTrips() {
        val payload = "x".repeat(1_000)
        val decoded = DiscordIpcFrameCodec.decode(
            DiscordIpcFrameCodec.encode(DiscordIpcFrame(DiscordIpcOpCode.OP_FRAME, payload)),
        )
        assertEquals(1_000, decoded?.payload?.length)
    }

    @Test
    fun decode_headerShorterThanEightBytes_isNull() {
        assertNull(DiscordIpcFrameCodec.decodeHeader(ByteArray(7)))
    }

    @Test
    fun decode_truncatedPayload_isNull() {
        val frame = DiscordIpcFrameCodec.encode(DiscordIpcFrame(DiscordIpcOpCode.OP_FRAME, "12345"))
        val truncated = frame.copyOf(frame.size - 2)
        assertNull(DiscordIpcFrameCodec.decode(truncated), "a mid-frame pipe loss reads as a disconnect, not a crash")
    }

    @Test
    fun decode_oversizedDeclaredLength_isNull() {
        val header = ByteArray(8)
        // length = MAX + 1, little-endian at offset 4.
        val oversized = DiscordIpcFrameCodec.MAX_PAYLOAD_BYTES + 1
        header[4] = (oversized and 0xFF).toByte()
        header[5] = ((oversized shr 8) and 0xFF).toByte()
        header[6] = ((oversized shr 16) and 0xFF).toByte()
        header[7] = ((oversized shr 24) and 0xFF).toByte()
        assertNull(DiscordIpcFrameCodec.decode(header))
    }

    @Test
    fun decode_atAnOffset_readsTheRightFrame() {
        val first = DiscordIpcFrameCodec.encode(DiscordIpcFrame(DiscordIpcOpCode.OP_FRAME, """{"a":1}"""))
        val second = DiscordIpcFrameCodec.encode(DiscordIpcFrame(DiscordIpcOpCode.OP_PING, "pong"))
        val both = first + second
        val decoded = DiscordIpcFrameCodec.decode(both, offset = first.size)
        assertNotNull(decoded)
        assertEquals(DiscordIpcOpCode.OP_PING, decoded.opCode)
        assertEquals("pong", decoded.payload)
    }

    @Test
    fun handshakePayload_carriesTheProtocolVersionAndClientId() {
        val payload = DiscordIpcFrameCodec.handshakePayload("1234567890")
        assertTrue(payload.contains("\"v\":1"))
        assertTrue(payload.contains("\"client_id\":\"1234567890\""))
    }

    @Test
    fun encode_ofAnEmptyPayload_yieldsAHeaderOnlyFrame() {
        val bytes = DiscordIpcFrameCodec.encode(DiscordIpcFrame(DiscordIpcOpCode.OP_FRAME, ""))
        assertEquals(8, bytes.size)
        val decoded = DiscordIpcFrameCodec.decode(bytes)
        assertEquals("", decoded?.payload)
    }

    @Test
    fun decodeHeader_atANegativeOffset_isNull() {
        assertNull(DiscordIpcFrameCodec.decodeHeader(ByteArray(16), offset = -1))
    }
}
