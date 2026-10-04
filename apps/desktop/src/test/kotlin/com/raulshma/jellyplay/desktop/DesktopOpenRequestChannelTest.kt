package com.raulshma.jellyplay.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

/**
 * Pins [DesktopOpenRequestChannel] — the second-instance → first-instance
 * argv forward (the file-drop message half beside the lock-only
 * [DesktopSingleInstanceGuard]):
 *
 *  - the contended writer's drop is atomic-shaped (temp + move) and the
 *    reader's delete-after-read acknowledgement means a payload drains
 *    exactly once;
 *  - a link-less second launch (`.m3u`/`.strm` paths, junk — the verified
 *    no-file-open-flow cut) writes nothing;
 *  - the running instance's boot-time cleanup deletes a stale payload;
 *  - the watcher's poll loop emits each payload once and then goes quiet.
 */
class DesktopOpenRequestChannelTest {

    private lateinit var configDir: Path

    @BeforeTest
    fun setUp() {
        configDir = Files.createTempDirectory("jellyplay-open-channel")
    }

    @AfterTest
    fun tearDown() {
        configDir.toFile().deleteRecursively()
    }

    private val requestFile: Path
        get() = configDir.resolve(DesktopOpenRequestChannel.REQUEST_FILE_NAME)

    @Test
    fun `drain on a quiet config dir returns null`() {
        assertNull(DesktopOpenRequestChannel.drain(configDir))
    }

    @Test
    fun `enqueue then drain round-trips the payload and the file is consumed`() {
        DesktopOpenRequestChannel.enqueue(
            configDir,
            listOf("jellyplay://media/a", "jellyplay://search"),
        )
        assertTrue(requestFile.exists())

        assertEquals(
            listOf("jellyplay://media/a", "jellyplay://search"),
            DesktopOpenRequestChannel.drain(configDir),
        )
        // The deletion IS the acknowledgement: the next poll sees nothing.
        assertFalse(requestFile.exists())
        assertNull(DesktopOpenRequestChannel.drain(configDir))
    }

    @Test
    fun `a link-less second launch writes nothing`() {
        DesktopOpenRequestChannel.enqueue(configDir, emptyList())
        assertFalse(requestFile.exists())
        assertNull(DesktopOpenRequestChannel.drain(configDir))
    }

    @Test
    fun `a newer launch replaces the previous payload — latest launch wins`() {
        DesktopOpenRequestChannel.enqueue(configDir, listOf("jellyplay://media/old"))
        DesktopOpenRequestChannel.enqueue(configDir, listOf("jellyplay://media/new"))

        assertEquals(
            listOf("jellyplay://media/new"),
            DesktopOpenRequestChannel.drain(configDir),
        )
    }

    @Test
    fun `blank lines in a payload are skipped by the drain`() {
        Files.createDirectories(configDir)
        Files.writeString(requestFile, "\njellyplay://media/a\n\n")
        assertEquals(
            listOf("jellyplay://media/a"),
            DesktopOpenRequestChannel.drain(configDir),
        )
    }

    @Test
    fun `boot-time clear deletes a payload left by a dead launch`() {
        DesktopOpenRequestChannel.enqueue(configDir, listOf("jellyplay://media/stale"))
        DesktopOpenRequestChannel.clear(configDir)
        assertFalse(requestFile.exists())
        assertNull(DesktopOpenRequestChannel.drain(configDir))
    }

    @Test
    fun `clear on a quiet dir is a no-op`() {
        DesktopOpenRequestChannel.clear(configDir)
        assertNull(DesktopOpenRequestChannel.drain(configDir))
    }

    @Test
    fun `enqueue creates a missing config dir (the guard has not made one yet)`() {
        val nested = configDir.resolve("config")
        DesktopOpenRequestChannel.enqueue(nested, listOf("jellyplay://search"))
        assertTrue(nested.resolve(DesktopOpenRequestChannel.REQUEST_FILE_NAME).exists())
    }

    @Test
    fun `watch emits the payload once and the drain consumed it`() = runTest {
        DesktopOpenRequestChannel.enqueue(configDir, listOf("jellyplay://media/watched"))
        val payloads = DesktopOpenRequestChannel.watch(configDir, intervalMs = 10)
            .take(1)
            .toList()
        assertEquals(listOf(listOf("jellyplay://media/watched")), payloads)
        assertFalse(requestFile.exists())
    }
}
