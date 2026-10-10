package com.raulshma.jellyplay.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * The second-instance → first-instance argv forward (the open-with glue
 * over [DesktopSingleInstanceGuard], which is LOCK-ONLY — no sockets, no
 * message passing; its KDoc records that boundary). When the OS "Open with"
 * chain launches a SECOND JellyPlay while one is running, the second JVM
 * cannot own the window (Main.kt exits it 0), so the links its argv carried
 * are dropped into a one-line drop file under the config dir; the running
 * instance polls for it and routes them like locally-pasted links.
 *
 * A FILE DROP, not a socket: a loopback ServerSocket risks the Windows
 * firewall prompt on packaged builds, and a port broker is more machinery
 * than a convenience channel earns — the config dir is already this shell's
 * IPC substrate (crash markers, window-state.properties, the lock file).
 *
 * Protocol (deliberately three steps, each side failing soft):
 *  1. writer (second JVM): [enqueue] — the parsable-link argv entries, one
 *     per line, written to a temp file first and MOVED into place so the
 *     reader never sees a half-written payload;
 *  2. reader (running JVM): [watch] polls [REQUEST_FILE_NAME], and when the
 *     file appears, [drain]s it (read + delete — the deletion IS the
 *     acknowledgement, so a payload can never route twice);
 *  3. first instance at boot: [clear] — a payload left by a launch that
 *     died mid-handshake is stale by definition (its JVM is gone) and is
 *     deleted unread.
 *
 * Failure posture: every malformed/blank line in a payload is skipped (the
 * running instance re-validates through [DesktopLinkOpenPolicy] — the
 * writer only forwards links, but the file is user-writable disk). A lost
 * race (payload lands between two drains) costs one forwarded launch, never
 * state corruption — there is nothing on the other side of this channel
 * but a navigation request.
 */
object DesktopOpenRequestChannel {

    /** The drop file inside the config dir (a sibling of the lock file). */
    const val REQUEST_FILE_NAME: String = "open-request.txt"
    private const val TEMP_SUFFIX = ".tmp"

    /** Writer side (the contended second JVM in Main.kt): drop [lines] into [configDir]. */
    fun enqueue(configDir: Path, lines: List<String>) {
        if (lines.isEmpty()) return
        Files.createDirectories(configDir)
        val temp = Files.createTempFile(configDir, REQUEST_FILE_NAME, TEMP_SUFFIX)
        Files.writeString(temp, lines.joinToString(separator = "\n", postfix = "\n"))
        try {
            moveIntoPlace(temp, configDir.resolve(REQUEST_FILE_NAME))
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    /**
     * Reader side, one drain: the payload's lines (or `null` when no request
     * is pending). Delete-after-read is best-effort, not a true ack: a payload
     * that lands while the drain holds an older one can be deleted unread
     * (single-slot file). Two known races, both acceptable for a convenience
     * channel and self-healing on the next successful enqueue/drain: (1) the
     * delete can lose against a concurrent enqueue's move on Windows — the
     * file survives one extra poll as a duplicate, filtered by the scaffold's
     * consume-once drain; (2) a newer enqueue's move landing between the read
     * and the delete discards that newer payload.
     */
    fun drain(configDir: Path): List<String>? {
        val requestFile = configDir.resolve(REQUEST_FILE_NAME)
        if (!requestFile.exists()) return null
        val payload = try {
            requestFile.readText()
        } catch (_: java.io.IOException) {
            // Vanished between the exists-check and the read (the other
            // side's cleanup, or a second writer's move) — not a request.
            return null
        }
        try {
            Files.deleteIfExists(requestFile)
        } catch (_: java.io.IOException) {
            // Windows: a concurrent enqueue's move may still hold the name.
            // Content is consumed; the leftover is re-drained (and deleted)
            // on a later poll.
        }
        return payload.lines().filter { it.isNotBlank() }
    }

    /** Boot-time cleanup: an unread payload from a dead launch is stale. */
    fun clear(configDir: Path) {
        try {
            Files.deleteIfExists(configDir.resolve(REQUEST_FILE_NAME))
        } catch (_: java.io.IOException) {
            // Nothing depends on the delete succeeding — the watcher drains
            // and re-validates whatever it finds anyway.
        }
    }

    /**
     * The running instance's poll loop as a cold [Flow]: drains every
     * [intervalMs] until cancellation, emitting each non-empty payload's
     * lines. The polling cadence (2 s) is coarse on purpose — a human
     * launching an app twice is not latency-sensitive, and the channel must
     * stay invisible to the idle-power profile.
     */
    fun watch(configDir: Path, intervalMs: Long = WATCH_INTERVAL_MS): Flow<List<String>> = flow {
        while (true) {
            drain(configDir)?.let { lines -> emit(lines) }
            delay(intervalMs)
        }
    }

    /**
     * The Main.kt-facing arm: collects [watch] on a process-lifetime daemon
     * scope (the EngineActivityRecorder idiom — daemon workers never block
     * JVM exit, so no shutdown choreography exists or is needed), handing
     * each drained payload to [onLines]. Main.kt's lambda re-validates the
     * lines through [DesktopLinkOpenPolicy] and seeds
     * [DesktopLinkOpenQueue].
     */
    fun startDaemonWatcher(configDir: Path, onLines: (List<String>) -> Unit) {
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
        ).launch {
            watch(configDir).collect(onLines)
        }
    }

    private fun moveIntoPlace(temp: Path, target: Path) {
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.io.IOException) {
            // ATOMIC_MOVE is unsupported on some filesystems; the fallback
            // move may still race a concurrent reader — it then loses one
            // payload, never corrupts one.
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.io.IOException) {
                // The forward is best-effort by contract — a lost drop is
                // the second launch's "not delivered" outcome.
            }
        }
    }

    /** The watcher's default cadence. */
    const val WATCH_INTERVAL_MS: Long = 2_000
}
