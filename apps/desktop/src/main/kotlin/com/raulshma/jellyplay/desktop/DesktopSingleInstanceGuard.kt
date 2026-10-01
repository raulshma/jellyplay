package com.raulshma.jellyplay.desktop

import java.io.Closeable
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

/**
 * Cross-process single-instance guard for the desktop shell: the second
 * `main()` over the same [DesktopPaths] tree would otherwise run a second
 * JVM against the same DataStore/Room files (multi-writer corruption, two
 * windows, two tray icons) — Main.kt never read its args and there is no
 * file-association/protocol registration to route a second launch into the
 * first instance.
 *
 * A file lock, not a PID file: an OS-level file lock is released by the
 * KERNEL the moment the holding process dies — crash, kill -9, power loss —
 * so a stale lock cannot outlive its owner and no liveness probing is
 * needed. A PID file has exactly the opposite property (the pid is stale
 * the instant the owner dies, and a recycled pid makes it lie), which is
 * why this guard is [FileChannel.tryLock] all the way down.
 *
 * Semantics (pinned by DesktopSingleInstanceGuardTest, which drives the
 * contended case through a real child process — on Linux/macOS file locks
 * are PER-PROCESS, so a same-JVM second acquire does not reliably fail and
 * cannot serve as the test's contention source):
 *
 *  - Returns a [Handle] on success; the caller must keep the reference for
 *    the process lifetime (a GC'd channel closes and releases the lock) and
 *    may close it to release early — JVM exit releases it anyway.
 *  - Returns `null` when another process holds the lock: Main.kt prints a
 *    clear message and exits 0. NOTE: activating/focusing the existing
 *    instance's window is deliberately OUT OF SCOPE here (no OS-level
 *    single-instance protocol exists on this shell — no associations, no
 *    named-pipe handshake); a second launch just exits. Windows users get
 *    the taskbar's existing window; this note records that boundary.
 *  - Throws [java.io.IOException] only for unexpected filesystem failures
 *    (a config dir that cannot even be created) — Main.kt treats those as
 *    fatal, since DesktopPaths' stores could not open there either.
 */
object DesktopSingleInstanceGuard {

    /** The conventional lock file name inside the config dir. */
    const val LOCK_FILE_NAME: String = "single-instance.lock"

    /**
     * Tries to take the single-instance lock at [lockFile] (conventionally
     * `DesktopPaths.configDirNio/<LOCK_FILE_NAME>`); returns `null` when
     * another process already holds it.
     */
    fun acquire(lockFile: Path): Handle? {
        lockFile.parent?.let { Files.createDirectories(it) }
        val file = RandomAccessFile(lockFile.toFile(), "rw")
        val channel = file.channel
        val lock = try {
            channel.tryLock()
        } catch (_: OverlappingFileLockException) {
            // Same JVM already holds this file's lock through another
            // channel — treat exactly like cross-process contention.
            null
        }
        if (lock == null) {
            channel.close()
            file.close()
            return null
        }
        return Handle(lockFile, file, lock)
    }

    /**
     * Owns the open lock for the process lifetime. NOT Closeable-strict in
     * the resource-block sense — main() pins it with a shutdown hook (a GC
     * root, so the channel cannot be collected mid-run) that calls [close]
     * on the way out; close() also exists for tests and explicit teardown.
     * The release-then-delete inside [close] has a theoretical window where
     * another process acquires the lock between the two steps and the marker
     * file is deleted out from under it (a third launch would then re-create
     * it); accepted because it needs two launches to interleave inside one
     * process exit, and Windows cannot delete the open file at all.
     */
    class Handle internal constructor(
        private val lockFile: Path,
        private val file: RandomAccessFile,
        private val lock: FileLock,
    ) : Closeable {

        val path: Path get() = lockFile

        override fun close() {
            try {
                lock.release()
            } finally {
                file.close()
                Files.deleteIfExists(lockFile)
            }
        }

        override fun toString(): String =
            "DesktopSingleInstanceGuard.Handle(${lockFile.nameWithoutExtension})"
    }
}
