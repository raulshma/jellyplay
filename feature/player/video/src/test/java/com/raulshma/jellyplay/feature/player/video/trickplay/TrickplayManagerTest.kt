package com.raulshma.jellyplay.feature.player.video.trickplay

import android.graphics.Bitmap
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.TrickplayInfo
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Robolectric tests for [TrickplayManager]. Covers the lifecycle (initialize /
 * clear), the divide-by-zero guard on malformed [TrickplayInfo], the
 * local-cache fast path, and the index → sprite-sheet arithmetic — the pure
 * logic that does not require a real JPEG decoder. Robolectric provides a
 * functional `BitmapFactory`/`Canvas` shim so the manager's native Android
 * calls run under the JVM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TrickplayManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repo: PlaybackRepository

    private val info = TrickplayInfo(
        width = 160,
        height = 90,
        tileWidth = 5,
        tileHeight = 5,
        thumbnailCount = 25,
        interval = 1_000,
        bandwidth = 100_000,
    )

    @Before
    fun setUp() {
        // Relaxed so background preload/adjacent-sheet fetches that the tests
        // don't care about don't throw "no answer found"; specific calls are
        // asserted with explicit `coEvery`/`coVerify` per test.
        repo = mockk(relaxed = true)
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────────

    @Test
    fun getThumbnail_returnsNullBeforeInitialize() = runBlocking {
        val manager = TrickplayManager(repo, lowRamDevice = false)
        assertNull(manager.getThumbnail(0L))
    }

    @Test
    fun clear_afterInitialize_isIdempotent() = runBlocking {
        val manager = TrickplayManager(repo, lowRamDevice = false)
        manager.initialize("item-1", info)
        // Two consecutive clears must not throw.
        manager.clear()
        manager.clear()
        // And getThumbnail goes back to null after clear (no info).
        assertNull(manager.getThumbnail(0L))
    }

    @Test
    fun reinitialize_replacesPreviousItem() = runBlocking {
        coEvery { repo.getTrickplayTileImage(any(), any(), any()) } returns null
        val manager = TrickplayManager(repo, lowRamDevice = false)

        manager.initialize("item-1", info)
        manager.initialize("item-2", info.copy(thumbnailCount = 10))

        // No exception; the new item's info is in place (interval honoured).
        // Returns null because the repo hands back no bytes.
        assertNull(manager.getThumbnail(0L))
    }

    // ── Malformed-info divide-by-zero guard ─────────────────────────────────────

    @Test
    fun getThumbnail_withZeroInterval_doesNotThrow() = runBlocking {
        coEvery { repo.getTrickplayTileImage(any(), any(), any()) } returns null
        val manager = TrickplayManager(repo, lowRamDevice = false)
        // interval/tileWidth/tileHeight == 0 previously caused
        // ArithmeticException on every seek; the manager must coerceAtLeast(1).
        manager.initialize("item-zero", info.copy(interval = 0, tileWidth = 0, tileHeight = 0))

        // Must return null (no data) rather than throwing.
        assertNull(manager.getThumbnail(5_000L))
    }

    @Test
    fun getThumbnail_clampsIndexPastEnd() = runBlocking {
        coEvery { repo.getTrickplayTileImage(any(), any(), any()) } returns null
        val manager = TrickplayManager(repo, lowRamDevice = false)
        manager.initialize("item-1", info)

        // Position far beyond the last thumbnail must not overflow the index.
        assertNull(manager.getThumbnail(1_000_000L))
    }

    // ── Local-cache fast path ───────────────────────────────────────────────────

    @Test
    fun initializeLocal_readsSheet0FromCacheWithoutNetworkCallForSheet0() = runBlocking {
        val cacheDir = File(tempFolder.root, "cache").apply { mkdirs() }
        // Plant the primary sprite-sheet file so the local-cache branch is taken
        // for sheet 0. Robolectric's BitmapFactory shim returns a bitmap for the
        // bytes, so getThumbnail yields a thumbnail — but sheet 0 must be served
        // from disk, never from the network.
        File(cacheDir, "trickplay_0.jpg").writeBytes(ByteArray(64) { 0 })

        val manager = TrickplayManager(repo, lowRamDevice = false)
        manager.initializeLocal("item-1", info, cacheDir)

        assertNotNull("local cache should still produce a thumbnail under Robolectric", manager.getThumbnail(0L))
        manager.clear()
        // Sheet 0 (the one we cached) is never fetched from the repository.
        io.mockk.coVerify(exactly = 0) {
            repo.getTrickplayTileImage("item-1", info.width, 0)
        }
    }

    // ── Low-RAM budget ──────────────────────────────────────────────────────────

    @Test
    fun lowRamDevice_doesNotThrowOnInitializeAndClear() = runBlocking {
        coEvery { repo.getTrickplayTileImage(any(), any(), any()) } returns null
        val manager = TrickplayManager(repo, lowRamDevice = true)
        manager.initialize("item-1", info)
        manager.clear()
        assertNull(manager.getThumbnail(0L))
    }

    // ── Pre-populated cache path (happy path through the sprite-sheet decoder) ──

    @Test
    fun getThumbnail_withValidSpriteSheet_returnsDecodedTile() = runBlocking {
        // Build a real sprite-sheet bitmap, encode it to PNG (Robolectric
        // supports PNG), and have the repo hand those bytes back. The manager's
        // bounds guard accepts a sheet whose dimensions match width*tileWidth ×
        // height*tileHeight; a 2x cap is allowed, so the exact-size sheet passes.
        val sheetW = info.width * info.tileWidth
        val sheetH = info.height * info.tileHeight
        val sheet = Bitmap.createBitmap(sheetW, sheetH, Bitmap.Config.ARGB_8888)
        val out = java.io.ByteArrayOutputStream()
        assertTrue("sprite sheet must encode to PNG", sheet.compress(Bitmap.CompressFormat.PNG, 100, out))
        val bytes = out.toByteArray()

        coEvery { repo.getTrickplayTileImage("item-1", info.width, 0) } returns bytes
        val manager = TrickplayManager(repo, lowRamDevice = false)
        manager.initialize("item-1", info)

        val thumb = manager.getThumbnail(0L)
        assertNotNull("a valid sprite sheet must decode to a thumbnail", thumb)
    }
}
