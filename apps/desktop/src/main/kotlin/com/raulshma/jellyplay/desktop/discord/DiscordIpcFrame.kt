package com.raulshma.jellyplay.desktop.discord

/**
 * The Discord Rich Presence IPC frame layer (feature 4.2) — the pure half of
 * the hand-rolled client. The protocol is small JSON frames over the local
 * pipe/socket, every frame prefixed by an 8-byte little-endian header:
 * `op:int32 LE` + `length:int32 LE`, then [length] UTF-8 payload bytes.
 *
 * Opcodes (the ten-year-stable set every Discord client speaks):
 *  - [OP_HANDSHAKE] (0) — the first frame, `{"v":1,"client_id":"…"}`;
 *  - [OP_FRAME] (1) — every command/event frame after the handshake;
 *  - [OP_CLOSE] (2) — either side closing gracefully;
 *  - [OP_PING] (3) / [OP_PONG] (4) — a liveness probe and its echo (the
 *    payload is returned verbatim).
 *
 * Pure byte plumbing — pinned byte-for-byte by DiscordIpcFrameCodecTest,
 * no pipe involved.
 */
internal object DiscordIpcOpCode {
    const val OP_HANDSHAKE = 0
    const val OP_FRAME = 1
    const val OP_CLOSE = 2
    const val OP_PING = 3
    const val OP_PONG = 4
}

/** One decoded IPC frame: [opCode] + the UTF-8 [payload]. */
internal data class DiscordIpcFrame(val opCode: Int, val payload: String)

/**
 * The frame encoder/decoder. Encoding is total (a header + the payload's
 * bytes); decoding is fail-soft — a short or truncated buffer yields null
 * instead of throwing, so a mid-frame pipe loss reads as a disconnect, not a
 * crash.
 */
internal object DiscordIpcFrameCodec {

    /** op:int32 LE + length:int32 LE. */
    const val HEADER_SIZE = 8

    /** The maximum payload Discord accepts (its documented Rich Presence cap). */
    const val MAX_PAYLOAD_BYTES = 8_192

    /** The one statement of the protocol's payload-length bound (0..cap). */
    fun isValidPayloadLength(length: Int): Boolean = length in 0..MAX_PAYLOAD_BYTES

    /**
     * Encodes [frame] into the wire bytes: the little-endian header followed
     * by the UTF-8 payload.
     */
    fun encode(frame: DiscordIpcFrame): ByteArray {
        val payload = frame.payload.encodeToByteArray()
        val bytes = ByteArray(HEADER_SIZE + payload.size)
        writeIntLE(bytes, 0, frame.opCode)
        writeIntLE(bytes, 4, payload.size)
        payload.copyInto(bytes, HEADER_SIZE)
        return bytes
    }

    /**
     * Decodes the header at [offset]: the frame's opcode + payload length.
     * Returns null when fewer than [HEADER_SIZE] bytes remain.
     */
    fun decodeHeader(buffer: ByteArray, offset: Int = 0): DiscordIpcHeader? {
        if (offset < 0 || offset + HEADER_SIZE > buffer.size) return null
        return DiscordIpcHeader(
            opCode = readIntLE(buffer, offset),
            payloadLength = readIntLE(buffer, offset + 4),
        )
    }

    /**
     * Decodes a complete frame (header + payload) from [buffer] at [offset].
     * Returns null when the buffer does not hold the whole frame or the
     * length is not in `0 until [MAX_PAYLOAD_BYTES]` — never throws.
     */
    fun decode(buffer: ByteArray, offset: Int = 0): DiscordIpcFrame? {
        val header = decodeHeader(buffer, offset) ?: return null
        if (!isValidPayloadLength(header.payloadLength)) return null
        if (offset + HEADER_SIZE + header.payloadLength > buffer.size) return null
        return DiscordIpcFrame(
            opCode = header.opCode,
            payload = buffer.decodeToString(
                startIndex = offset + HEADER_SIZE,
                endIndex = offset + HEADER_SIZE + header.payloadLength,
            ),
        )
    }

    /** The `v:1` handshake frame payload for [clientId]. */
    fun handshakePayload(clientId: String): String =
        """{"v":1,"client_id":"$clientId"}"""

    private fun writeIntLE(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xFF).toByte()
        target[offset + 1] = ((value shr 8) and 0xFF).toByte()
        target[offset + 2] = ((value shr 16) and 0xFF).toByte()
        target[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun readIntLE(source: ByteArray, offset: Int): Int =
        (source[offset].toInt() and 0xFF) or
            ((source[offset + 1].toInt() and 0xFF) shl 8) or
            ((source[offset + 2].toInt() and 0xFF) shl 16) or
            ((source[offset + 3].toInt() and 0xFF) shl 24)
}

/** The decoded [DiscordIpcFrameCodec.HEADER_SIZE] fields. */
internal data class DiscordIpcHeader(val opCode: Int, val payloadLength: Int)
