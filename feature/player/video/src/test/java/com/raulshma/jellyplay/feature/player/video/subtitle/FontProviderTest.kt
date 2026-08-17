package com.raulshma.jellyplay.feature.player.video.subtitle

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for [FontProvider]:
 * - [FontProvider.parseFontFamily] (pure JVM): returns null — never throws —
 *   for a corrupt or non-font file.
 * - Font-bytes caching (Robolectric: a real `Context.cacheDir` plus the
 *   bundled `subfont.ttf` asset served from `src/main/assets`): `prewarm`
 *   seeds the cache with the bundled fallback, an unchanged file is served
 *   from the stamp-keyed cache (the SAME `ByteArray` instance, proving the
 *   Main-thread `cachedFontBytes()` path does not re-read disk), a rewritten
 *   file is invalidated by its `(length, lastModified)` stamp, and a
 *   user-dropped `.ttf` is picked up by an idempotent re-`prewarm` while
 *   non-`.ttf` files in the dir are ignored.
 */
@RunWith(RobolectricTestRunner::class)
class FontProviderTest {

    private lateinit var context: Context
    private lateinit var provider: FontProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        provider = FontProvider(context)
    }

    @Test
    fun parseFontFamily_corruptOrMissingFile_returnsNull() {
        val dir = createTempDirectory(prefix = "fp_test").toFile()
        try {
            val bogus = File(dir, "not-a-font.ttf")
            bogus.writeBytes(byteArrayOf(0, 1, 2, 3))
            val family = FontProvider.parseFontFamily(bogus)
            assertEquals(null, family)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun prewarm_thenCachedFontBytes_containsBundledFallback() = runBlocking {
        provider.prewarm()
        val bytes = provider.cachedFontBytes()
        val bundled = bytes["subfont"]
        assertNotNull(bundled)
        assertTrue(bundled!!.isNotEmpty())
        // Byte-identical to the bundled asset it was copied from.
        val expected = context.assets.open("subfont.ttf").use { it.readBytes() }
        assertArrayEquals(expected, bundled)
    }

    @Test
    fun fontBytes_unchangedFile_returnsSameInstance() = runBlocking {
        provider.prewarm()
        val first = provider.cachedFontBytes()
        val second = provider.cachedFontBytes()
        // Stamp-validated cache hit: a disk re-read would allocate a fresh
        // array, so instance identity is the proof of no re-read.
        assertSame(first.getValue("subfont"), second.getValue("subfont"))
    }

    @Test
    fun fontBytes_rewrittenFile_invalidatedByStamp() = runBlocking {
        provider.prewarm()
        val stale = provider.cachedFontBytes().getValue("subfont")
        val ttf = File(provider.provideFontsDir(), "subfont.ttf")
        val fresh = byteArrayOf(0x7f, 0x0d, 0x0a)
        ttf.writeBytes(fresh)
        // Fast rewrites can land on the same ms stamp; force a distinct one
        // (the shorter length also differs, double-guarding the invalidation).
        ttf.setLastModified(ttf.lastModified() + 10_000)
        val bytes = provider.cachedFontBytes()
        assertArrayEquals(fresh, bytes.getValue("subfont"))
        assertFalse(bytes.getValue("subfont").contentEquals(stale))
    }

    @Test
    fun prewarm_picksUpUserFont_ignoresNonTtfFiles() = runBlocking {
        provider.prewarm()
        val dir = provider.provideFontsDir()
        File(dir, "userfont.ttf").writeBytes(byteArrayOf(1, 2, 3))
        File(dir, "readme.txt").writeBytes("not a font".toByteArray())
        provider.prewarm() // idempotent; the new file must surface
        val bytes = provider.cachedFontBytes()
        assertNotNull(bytes["userfont"])
        assertFalse(bytes.containsKey("readme"))
        assertNotNull(bytes["subfont"])
    }
}
