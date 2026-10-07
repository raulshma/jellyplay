package com.raulshma.jellyplay.desktop

import java.io.File
import java.lang.ProcessBuilder.Redirect
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins [DesktopSingleInstanceGuard]:
 *
 *  1. a fresh lock file acquires, stays held, releases, and re-acquires;
 *  2. a lock held by ANOTHER PROCESS rejects the second acquire — driven
 *     through a real child JVM, because file locks are PER-PROCESS on
 *     Linux/macOS: a same-JVM second channel would succeed there (POSIX
 *     locks don't conflict within a process), so an in-process "double
 *     acquire" test would pin Windows-only semantics. The child runs the
 *     production [DesktopSingleInstanceGuard.acquire] path itself
 *     ([LockHolderMain] lives in this same test source set), so the
 *     contended-launch behavior Main.kt relies on is exercised end to end
 *     without booting the Compose app.
 */
class DesktopSingleInstanceGuardTest {

    private val tempDirs = mutableListOf<Path>()
    private val childProcesses = mutableListOf<Process>()

    @AfterTest
    fun tearDown() {
        childProcesses.forEach { it.destroyForcibly() }
        tempDirs.forEach { dir -> dir.toFile().deleteRecursively() }
    }

    private fun tempDir(): Path =
        Files.createTempDirectory("single-instance-guard-test").also { tempDirs.add(it) }

    @Test
    fun `fresh lock acquires, releases, and re-acquires`() {
        val lockFile = tempDir().resolve(DesktopSingleInstanceGuard.LOCK_FILE_NAME)

        val first = DesktopSingleInstanceGuard.acquire(lockFile)
        assertNotNull(first, "fresh lock file must acquire")
        assertEquals(lockFile, first.path)

        // While held, the lock file itself exists (it is the marker).
        assertTrue(Files.exists(lockFile), "lock file must exist while held")

        first.close()
        assertTrue(!Files.exists(lockFile), "close() removes the lock file")

        // The OS released the lock with close() — acquiring again must work
        // (this is the process-restart happy path: no stale lock).
        val second = DesktopSingleInstanceGuard.acquire(lockFile)
        assertNotNull(second, "post-release re-acquire must succeed")
        second.close()
    }

    @Test
    fun `acquire while another process holds the lock is rejected`() {
        val dir = tempDir()
        val lockFile = dir.resolve(DesktopSingleInstanceGuard.LOCK_FILE_NAME)
        val sentinel = dir.resolve("holder.status")

        val holder = startLockHolder(lockFile, sentinel)
        childProcesses.add(holder)

        // Wait until the child reports it holds the lock (or failed).
        val reported = awaitContent(sentinel, timeoutMs = 30_000, holder = holder, logDir = lockFile.parent)
        assertEquals(
            "locked",
            reported,
            "child lock holder failed to acquire — environment problem, not a guard bug",
        )

        // The contended acquire from THIS process must be rejected.
        val contended = DesktopSingleInstanceGuard.acquire(lockFile)
        assertNull(contended, "second acquire while the child process holds the lock must return null")

        // Release the child; a fresh acquire must then succeed — the
        // process-death-releases-the-lock path Main.kt depends on.
        sentinel.toFile().delete()
        val exitCode = waitForExit(holder, timeoutMs = 30_000)
        assertEquals(0, exitCode, "lock holder should exit cleanly once told to stop")

        val afterRelease = DesktopSingleInstanceGuard.acquire(lockFile)
        assertNotNull(afterRelease, "acquire after the holder process died must succeed")
        afterRelease.close()
    }

    /**
     * Launches [LockHolderMain] in a child JVM over the compiled classes it
     * needs — the test classes dir (this file), the main classes dir (the
     * guard), and the kotlin-stdlib jar from THIS JVM's classpath (both
     * compiled class trees are Kotlin and intrinsically reference the
     * stdlib; the Gradle test worker loads the test classpath through its
     * own loader, so java.class.path cannot be reused). Output lands in
     * holder.out.log / holder.err.log beside the sentinel so a child that
     * dies before reporting is diagnosable.
     */
    private fun startLockHolder(lockFile: Path, sentinel: Path): Process {
        val mainClassesDir = Path.of(
            DesktopSingleInstanceGuard::class.java.protectionDomain.codeSource.location.toURI(),
        )
        val testClassesDir = Path.of(
            DesktopSingleInstanceGuardTest::class.java.protectionDomain.codeSource.location.toURI(),
        )
        // kotlin.io.path extensions + Intrinsics checks make the stdlib a
        // hard link-time dependency of every Kotlin class the child loads.
        val stdlibJar = Path.of(
            kotlin.Unit::class.java.protectionDomain.codeSource.location.toURI(),
        )
        val javaBin = Path.of(System.getProperty("java.home"), "bin", javaExecutableName())
        assertTrue(Files.isRegularFile(javaBin), "java launcher not found at $javaBin")
        val dir = lockFile.parent
        return ProcessBuilder(
            javaBin.toString(),
            "-cp",
            listOf(mainClassesDir, testClassesDir, stdlibJar).joinToString(File.pathSeparator) { it.toString() },
            "com.raulshma.jellyplay.desktop.LockHolderMain",
            lockFile.toString(),
            sentinel.toString(),
        )
            .redirectOutput(Redirect.to(dir.resolve("holder.out.log").toFile()))
            .redirectError(Redirect.to(dir.resolve("holder.err.log").toFile()))
            .start()
    }

    private fun javaExecutableName(): String =
        if (System.getProperty("os.name").lowercase().contains("win")) "java.exe" else "java"

    /**
     * Waits until the sentinel carries content and returns it. Polls CONTENT,
     * not mere existence: a plain `writeText` on Windows makes the file
     * visible in the directory before its bytes land, so an exists-check can
     * read an empty sentinel (observed once as `expected: <locked> but was:
     * <>`); the holder also publishes atomically (see [LockHolderMain.main]),
     * and this read loop stays safe even for a non-atomic writer.
     */
    private fun awaitContent(sentinel: Path, timeoutMs: Long, holder: Process, logDir: Path): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            // Fail fast when the child already died without reporting.
            assertTrue(
                holder.isAlive,
                "lock holder exited before reporting status; stderr: " +
                    readTail(logDir.resolve("holder.err.log")) +
                    " stdout: " + readTail(logDir.resolve("holder.out.log")),
            )
            if (Files.isRegularFile(sentinel)) {
                val content = Files.readString(sentinel).trim()
                if (content.isNotEmpty()) return content
            }
            Thread.sleep(25)
        }
        throw AssertionError(
            "lock holder never reported status within ${timeoutMs}ms; stderr: " +
                readTail(logDir.resolve("holder.err.log")) +
                " stdout: " + readTail(logDir.resolve("holder.out.log")),
        )
    }

    private fun readTail(path: Path): String =
        if (Files.isRegularFile(path)) Files.readString(path).takeLast(2000) else "(unavailable)"

    private fun waitForExit(process: Process, timeoutMs: Long): Int {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (process.isAlive && System.currentTimeMillis() < deadline) {
            Thread.sleep(25)
        }
        assertTrue(!process.isAlive, "lock holder did not exit within ${timeoutMs}ms")
        return process.exitValue()
    }
}

/**
 * The contention source for [DesktopSingleInstanceGuardTest] (run as a child
 * JVM over the test classes dir): acquires the guard at `args[0]` through
 * the production entry point, reports `locked`/`contended` into `args[1]`,
 * and holds the lock until the sentinel is deleted (or 60s — the test's
 * leak valve).
 */
class LockHolderMain {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val lockFile = Path.of(args[0])
            val sentinel = Path.of(args[1])
            val handle = DesktopSingleInstanceGuard.acquire(lockFile)
            if (handle == null) {
                reportAtomically(sentinel, "contended")
                kotlin.system.exitProcess(3)
            }
            reportAtomically(sentinel, "locked")
            val deadline = System.currentTimeMillis() + 60_000
            while (Files.exists(sentinel) && System.currentTimeMillis() < deadline) {
                Thread.sleep(25)
            }
            handle.close()
        }

        /**
         * Publishes the status via temp-file + atomic move so the test's
         * sentinel read never observes a created-but-empty file.
         */
        private fun reportAtomically(sentinel: Path, content: String) {
            val tmp = sentinel.resolveSibling(sentinel.fileName.toString() + ".tmp")
            Files.writeString(tmp, content)
            try {
                Files.move(tmp, sentinel, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(tmp, sentinel)
            }
        }
    }
}
