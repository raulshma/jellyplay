package com.raulshma.jellyplay.desktop.discord

import java.io.Closeable
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The Discord Rich Presence IPC transport seam (feature 4.2): a blocking
 * byte pipe to the local Discord client. Discord exposes NINE endpoints
 * (`discord-ipc-0` … `discord-ipc-9`) — the first openable one wins, which
 * is how multiple installed Discord flavors (stable/PTB/canary) coexist.
 *
 * Two [openFirstAvailable] actuals:
 *  - Windows: the named pipe `\\.\pipe\discord-ipc-N`, driven through a
 *    `RandomAccessFile` (JVM sockets cannot open Win32 named pipes);
 *  - macOS/Linux: the Unix domain socket `discord-ipc-N`, resolved under
 *    `$XDG_RUNTIME_DIR`, then `$TMPDIR`, then `/tmp` (JDK 16+
 *    `UnixDomainSocketAddress`, the desktop shell's JVM is 17).
 *
 * Everything here is blocking IO — the client runs it on Dispatchers.IO.
 * Any failure while opening or reading means "Discord not there (yet)"; the
 * client treats it as a disconnect and backs off.
 */
internal interface DiscordIpcTransport : Closeable {

    /** Writes the whole array; blocking. Fails loudly (throws) — the caller owns retry. */
    fun write(bytes: ByteArray)

    /**
     * Blocking read of up to [length] bytes into [buffer] at [offset];
     * returns the count read, or -1 at end-of-stream (Discord closed the
     * pipe).
     */
    fun read(buffer: ByteArray, offset: Int, length: Int): Int

    companion object {

        /** The endpoint indexes Discord clients listen on (0–9 inclusive). */
        private const val ENDPOINT_COUNT = 10

        /**
         * Opens the first available `discord-ipc-N` endpoint for this OS, or
         * null when none answers (Discord not running).
         */
        fun openFirstAvailable(): DiscordIpcTransport? =
            if (isWindows) openWindows() else openUnix()

        internal val isWindows: Boolean
            get() = System.getProperty("os.name", "").lowercase().contains("windows")

        private fun openWindows(): DiscordIpcTransport? {
            for (endpoint in 0 until ENDPOINT_COUNT) {
                try {
                    val pipe = RandomAccessFile("\\\\.\\pipe\\discord-ipc-$endpoint", "rw")
                    return RandomAccessFileTransport(pipe)
                } catch (_: Exception) {
                    // This endpoint's pipe does not exist (or is not
                    // accepting) — try the next index.
                }
            }
            return null
        }

        private fun openUnix(): DiscordIpcTransport? {
            for (dir in unixCandidateDirs()) {
                for (endpoint in 0 until ENDPOINT_COUNT) {
                    val socketPath = dir.resolve("discord-ipc-$endpoint")
                    if (!Files.exists(socketPath)) continue
                    try {
                        val channel = SocketChannel.open(java.net.StandardProtocolFamily.UNIX)
                        channel.connect(java.net.UnixDomainSocketAddress.of(socketPath))
                        return SocketChannelTransport(channel)
                    } catch (_: Exception) {
                        // Not accepting — try the next candidate.
                    }
                }
            }
            return null
        }

        /** `$XDG_RUNTIME_DIR`, `$TMPDIR`, `/tmp` — the paths Discord uses. */
        private fun unixCandidateDirs(): List<Path> = buildList {
            System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() }?.let { add(Paths.get(it)) }
            System.getenv("TMPDIR")?.takeIf { it.isNotBlank() }?.let { add(Paths.get(it)) }
            add(Paths.get("/tmp"))
        }
    }
}

/** [RandomAccessFile] cannot implement Closeable read/write over pipes generically — thin holder. */
private class RandomAccessFileTransport(private val file: RandomAccessFile) : DiscordIpcTransport {
    override fun write(bytes: ByteArray) = file.write(bytes)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = file.read(buffer, offset, length)
    override fun close() = file.close()
}

private class SocketChannelTransport(private val channel: SocketChannel) : DiscordIpcTransport {
    override fun write(bytes: ByteArray) {
        channel.write(ByteBuffer.wrap(bytes))
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        channel.read(ByteBuffer.wrap(buffer, offset, length))

    override fun close() = channel.close()
}
