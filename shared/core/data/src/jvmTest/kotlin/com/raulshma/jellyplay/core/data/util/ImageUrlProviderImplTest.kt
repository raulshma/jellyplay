package com.raulshma.jellyplay.core.data.util

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [ImageUrlProviderImpl]'s memoisation + perf-mode clamping (the single
 * jvmShared implementation that replaced the android/desktop twins):
 *  1. poster / backdrop / chapter URLs are memoised per (item, effective
 *     width) — a repeated read costs no repository call;
 *  2. performance mode clamps the effective width to 300 (default 400);
 *  3. a null maxWidth (original-resolution request) bypasses the cache
 *     entirely;
 *  4. an empty repository URL is never cached (a later login/server change
 *     must be able to start producing URLs);
 *  5. poster/backdrop/chapter cache keys never collide;
 *  6. the interface's tag-guard default ([ImageUrlProvider.getImageUrlOrNull]
 *     — inherited, not overridden): a null tag is empty with NO repository
 *     call, any tag delegates to [ImageUrlProvider.getImageUrl] at its
 *     default width — the fold the Live TV ViewModels formerly hand-copied.
 */
class ImageUrlProviderImplTest {

    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var appearanceStore: AppearanceStore
    private lateinit var provider: ImageUrlProviderImpl

    private val appearance = MutableStateFlow(AppearanceSlice())

    @BeforeTest
    fun setup() {
        playbackRepository = mockk()
        appearanceStore = mockk()
        every { appearanceStore.appearance } returns appearance
        provider = ImageUrlProviderImpl(playbackRepository, appearanceStore)
    }

    @Test
    fun `poster URLs are memoised per item and width`() {
        every { playbackRepository.getImageUrl(ITEM, "Primary", 400) } returns "https://s/p400"

        assertEquals("https://s/p400", provider.getImageUrl(ITEM))
        assertEquals("https://s/p400", provider.getImageUrl(ITEM))

        verify(exactly = 1) { playbackRepository.getImageUrl(ITEM, "Primary", 400) }
    }

    @Test
    fun `performance mode clamps the effective width to 300`() {
        appearance.value = AppearanceSlice(performanceMode = true)
        every { playbackRepository.getImageUrl(ITEM, "Primary", 300) } returns "https://s/p300"

        assertEquals("https://s/p300", provider.getImageUrl(ITEM))

        verify(exactly = 0) { playbackRepository.getImageUrl(ITEM, "Primary", 400) }
    }

    @Test
    fun `a null maxWidth bypasses the cache for original-resolution requests`() {
        every { playbackRepository.getImageUrl(ITEM, "Primary", null) } returns "https://s/original"

        assertEquals("https://s/original", provider.getImageUrl(ITEM, maxWidth = null))
        assertEquals("https://s/original", provider.getImageUrl(ITEM, maxWidth = null))

        verify(exactly = 2) { playbackRepository.getImageUrl(ITEM, "Primary", null) }
    }

    @Test
    fun `backdrop URLs are memoised under their own key`() {
        every { playbackRepository.getBackdropUrl(ITEM, 1920) } returns "https://s/b1920"

        assertEquals("https://s/b1920", provider.getBackdropUrl(ITEM))
        assertEquals("https://s/b1920", provider.getBackdropUrl(ITEM))

        verify(exactly = 1) { playbackRepository.getBackdropUrl(ITEM, 1920) }
    }

    @Test
    fun `chapter URLs are memoised per item, index and tag`() {
        every { playbackRepository.getChapterImageUrl(ITEM, 2, any(), 400) } returns "https://s/c2"

        assertEquals("https://s/c2", provider.getChapterImageUrl(ITEM, 2, "tag"))
        assertEquals("https://s/c2", provider.getChapterImageUrl(ITEM, 2, "tag"))
        // A null tag is a different cache key and therefore a fresh fetch.
        assertEquals("https://s/c2", provider.getChapterImageUrl(ITEM, 2, null))

        verify(exactly = 2) { playbackRepository.getChapterImageUrl(ITEM, 2, any(), 400) }
    }

    @Test
    fun `an empty repository URL is never cached`() {
        every { playbackRepository.getImageUrl(ITEM, "Primary", 400) } returns ""

        provider.getImageUrl(ITEM)
        provider.getImageUrl(ITEM)

        verify(exactly = 2) { playbackRepository.getImageUrl(ITEM, "Primary", 400) }
    }

    @Test
    fun `poster and backdrop reads do not share a cache key`() {
        every { playbackRepository.getImageUrl(ITEM, "Primary", 400) } returns "https://s/poster"
        every { playbackRepository.getBackdropUrl(ITEM, 400) } returns "https://s/backdrop"

        assertEquals("https://s/poster", provider.getImageUrl(ITEM))
        assertEquals("https://s/backdrop", provider.getBackdropUrl(ITEM, maxWidth = 400))
    }

    // ── the interface's tag-guard default (inherited, not overridden) ──────

    @Test
    fun `a null image tag yields the empty string without touching the repository`() {
        assertEquals("", provider.getImageUrlOrNull(ITEM, null))

        verify(exactly = 0) { playbackRepository.getImageUrl(any(), any(), any()) }
    }

    @Test
    fun `any image tag delegates to getImageUrl at the default width`() {
        every { playbackRepository.getImageUrl(ITEM, "Primary", 400) } returns "https://s/p400"

        assertEquals("https://s/p400", provider.getImageUrlOrNull(ITEM, "tag"))

        verify(exactly = 1) { playbackRepository.getImageUrl(ITEM, "Primary", 400) }
    }

    private companion object {
        const val ITEM = "item-1"
    }
}
