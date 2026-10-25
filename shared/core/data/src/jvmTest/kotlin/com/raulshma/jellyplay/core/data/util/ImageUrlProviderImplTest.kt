package com.raulshma.jellyplay.core.data.util

import com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [ImageUrlProviderImpl]'s memoisation + width policy (the single
 * jvmShared implementation that replaced the android/desktop twins; since the
 * image-URL builders retired off PlaybackRepository the impl builds directly
 * through [LibraryApiClient]):
 *  1. poster / backdrop / chapter URLs are memoised per (item, effective
 *     width) — a repeated read costs no client call;
 *  2. performance mode clamps the DEFAULT-width request to 300 (default 400);
 *     an explicit non-default width is honored verbatim in both modes (the
 *     migrated infra callers' per-surface sizes never rode the clamp);
 *  3. a null maxWidth (original-resolution request) bypasses the cache
 *     entirely;
 *  4. an empty client URL is never cached (a later login/server change
 *     must be able to start producing URLs);
 *  5. poster/backdrop/chapter cache keys never collide;
 *  6. the interface's tag-guard default ([ImageUrlProvider.getImageUrlOrNull]
 *     — inherited, not overridden): a null tag is empty with NO client
 *     call, any tag delegates to [ImageUrlProvider.getImageUrl] at its
 *     default width, the tag FORWARDED — the fold the Live TV ViewModels
 *     formerly hand-copied;
 *  7. the image tag is part of the cache key: a tagged URL is a distinct
 *     entry from the untagged one, and a changed tag is a fresh fetch — the
 *     mechanism that makes re-uploaded artwork appear without a cache clear.
 */
class ImageUrlProviderImplTest {

    private lateinit var libraryApiClient: LibraryApiClient
    private lateinit var appearanceStore: AppearanceStore
    private lateinit var provider: ImageUrlProviderImpl

    private val appearance = MutableStateFlow(AppearanceSlice())

    @BeforeTest
    fun setup() {
        libraryApiClient = mockk()
        appearanceStore = mockk()
        every { appearanceStore.appearance } returns appearance
        provider = ImageUrlProviderImpl(libraryApiClient, appearanceStore)
    }

    @Test
    fun `poster URLs are memoised per item and width`() {
        every { libraryApiClient.getImageUrl(ITEM, "Primary", 400) } returns "https://s/p400"

        assertEquals("https://s/p400", provider.getImageUrl(ITEM))
        assertEquals("https://s/p400", provider.getImageUrl(ITEM))

        verify(exactly = 1) { libraryApiClient.getImageUrl(ITEM, "Primary", 400) }
    }

    @Test
    fun `performance mode clamps the default width to 300`() {
        appearance.value = AppearanceSlice(performanceMode = true)
        every { libraryApiClient.getImageUrl(ITEM, "Primary", 300) } returns "https://s/p300"

        assertEquals("https://s/p300", provider.getImageUrl(ITEM))

        verify(exactly = 0) { libraryApiClient.getImageUrl(ITEM, "Primary", 400) }
    }

    @Test
    fun `an explicit non-default width is honored verbatim`() {
        every { libraryApiClient.getImageUrl(ITEM, "Primary", 600) } returns "https://s/p600"

        assertEquals("https://s/p600", provider.getImageUrl(ITEM, maxWidth = 600))

        verify(exactly = 1) { libraryApiClient.getImageUrl(ITEM, "Primary", 600) }
    }

    @Test
    fun `an explicit non-default width is NOT clamped under performance mode`() {
        appearance.value = AppearanceSlice(performanceMode = true)
        every { libraryApiClient.getImageUrl(ITEM, "Primary", 200) } returns "https://s/p200"

        assertEquals("https://s/p200", provider.getImageUrl(ITEM, maxWidth = 200))

        verify(exactly = 0) { libraryApiClient.getImageUrl(ITEM, "Primary", 300) }
    }

    @Test
    fun `a null maxWidth bypasses the cache for original-resolution requests`() {
        every { libraryApiClient.getImageUrl(ITEM, "Primary", null) } returns "https://s/original"

        assertEquals("https://s/original", provider.getImageUrl(ITEM, maxWidth = null))
        assertEquals("https://s/original", provider.getImageUrl(ITEM, maxWidth = null))

        verify(exactly = 2) { libraryApiClient.getImageUrl(ITEM, "Primary", null) }
    }

    @Test
    fun `backdrop URLs are memoised under their own key`() {
        every { libraryApiClient.getBackdropImageUrl(ITEM, 1920) } returns "https://s/b1920"

        assertEquals("https://s/b1920", provider.getBackdropUrl(ITEM))
        assertEquals("https://s/b1920", provider.getBackdropUrl(ITEM))

        verify(exactly = 1) { libraryApiClient.getBackdropImageUrl(ITEM, 1920) }
    }

    @Test
    fun `chapter URLs are memoised per item, index and tag`() {
        every { libraryApiClient.getImageUrl(ITEM, "Chapter", 400, 2, any()) } returns "https://s/c2"

        assertEquals("https://s/c2", provider.getChapterImageUrl(ITEM, 2, "tag"))
        assertEquals("https://s/c2", provider.getChapterImageUrl(ITEM, 2, "tag"))
        // A null tag is a different cache key and therefore a fresh fetch.
        assertEquals("https://s/c2", provider.getChapterImageUrl(ITEM, 2, null))

        verify(exactly = 2) { libraryApiClient.getImageUrl(ITEM, "Chapter", 400, 2, any()) }
    }

    @Test
    fun `an empty client URL is never cached`() {
        every { libraryApiClient.getImageUrl(ITEM, "Primary", 400) } returns ""

        provider.getImageUrl(ITEM)
        provider.getImageUrl(ITEM)

        verify(exactly = 2) { libraryApiClient.getImageUrl(ITEM, "Primary", 400) }
    }

    @Test
    fun `poster and backdrop reads do not share a cache key`() {
        every { libraryApiClient.getImageUrl(ITEM, "Primary", 400) } returns "https://s/poster"
        every { libraryApiClient.getBackdropImageUrl(ITEM, 400) } returns "https://s/backdrop"

        assertEquals("https://s/poster", provider.getImageUrl(ITEM))
        assertEquals("https://s/backdrop", provider.getBackdropUrl(ITEM, maxWidth = 400))
    }

    // ── the interface's tag-guard default (inherited, not overridden) ──────

    @Test
    fun `a null image tag yields the empty string without touching the client`() {
        assertEquals("", provider.getImageUrlOrNull(ITEM, null))

        verify(exactly = 0) { libraryApiClient.getImageUrl(any(), any(), any()) }
    }

    @Test
    fun `any image tag delegates to getImageUrl at the default width, tag forwarded`() {
        every { libraryApiClient.getImageUrl(ITEM, "Primary", 400, null, "tag") } returns "https://s/p400"

        assertEquals("https://s/p400", provider.getImageUrlOrNull(ITEM, "tag"))

        verify(exactly = 1) { libraryApiClient.getImageUrl(ITEM, "Primary", 400, null, "tag") }
    }

    // ── logo URLs (the "prefer logos" detail title) ───────────────────────

    @Test
    fun `logo URLs compose the Logo image type and are memoised per item`() {
        every { libraryApiClient.getImageUrl(ITEM, "Logo", 400) } returns "https://s/logo400"

        assertEquals("https://s/logo400", provider.getLogoUrl(ITEM))
        assertEquals("https://s/logo400", provider.getLogoUrl(ITEM))

        verify(exactly = 1) { libraryApiClient.getImageUrl(ITEM, "Logo", 400) }
    }

    @Test
    fun `logo URLs clamp to the perf width under performance mode`() {
        appearance.value = AppearanceSlice(performanceMode = true)
        every { libraryApiClient.getImageUrl(ITEM, "Logo", 300) } returns "https://s/logo300"

        assertEquals("https://s/logo300", provider.getLogoUrl(ITEM))

        verify(exactly = 0) { libraryApiClient.getImageUrl(ITEM, "Logo", 400) }
    }

    @Test
    fun `logo cache keys never collide with the poster variant`() {
        every { libraryApiClient.getImageUrl(ITEM, "Primary", 400) } returns "https://s/poster"
        every { libraryApiClient.getImageUrl(ITEM, "Logo", 400) } returns "https://s/logo"

        assertEquals("https://s/poster", provider.getImageUrl(ITEM))
        assertEquals("https://s/logo", provider.getLogoUrl(ITEM))
    }

    @Test
    fun `a null logo tag yields the empty string without touching the client`() {
        assertEquals("", provider.getLogoUrlOrNull(ITEM, null))

        verify(exactly = 0) { libraryApiClient.getImageUrl(any(), any(), any()) }
    }

    @Test
    fun `any logo tag delegates to getLogoUrl, tag forwarded`() {
        every { libraryApiClient.getImageUrl(ITEM, "Logo", 400, null, "tag") } returns "https://s/logo400"

        assertEquals("https://s/logo400", provider.getLogoUrlOrNull(ITEM, "tag"))

        verify(exactly = 1) { libraryApiClient.getImageUrl(ITEM, "Logo", 400, null, "tag") }
    }

    // ── tag threading (the stale-artwork fix: a new tag must be a new key) ──

    @Test
    fun `a tagged poster is a distinct cache entry from the untagged one`() {
        every { libraryApiClient.getImageUrl(ITEM, "Primary", 400, null, null) } returns "https://s/p-old"
        every { libraryApiClient.getImageUrl(ITEM, "Primary", 400, null, "new-tag") } returns "https://s/p-new"

        assertEquals("https://s/p-old", provider.getImageUrl(ITEM))
        assertEquals("https://s/p-new", provider.getImageUrl(ITEM, tag = "new-tag"))
        // Both entries memoise independently — the untagged read did not poison
        // the tagged one (this is the pairing that makes re-uploaded art appear).
        assertEquals("https://s/p-new", provider.getImageUrl(ITEM, tag = "new-tag"))

        verify(exactly = 1) { libraryApiClient.getImageUrl(ITEM, "Primary", 400, null, null) }
        verify(exactly = 1) { libraryApiClient.getImageUrl(ITEM, "Primary", 400, null, "new-tag") }
    }

    @Test
    fun `a changed tag is a fresh fetch, not a stale hit`() {
        every { libraryApiClient.getImageUrl(ITEM, "Primary", 400, null, "t1") } returns "https://s/v1"
        every { libraryApiClient.getImageUrl(ITEM, "Primary", 400, null, "t2") } returns "https://s/v2"

        assertEquals("https://s/v1", provider.getImageUrl(ITEM, tag = "t1"))
        assertEquals("https://s/v2", provider.getImageUrl(ITEM, tag = "t2"))
    }

    @Test
    fun `tagged backdrop and logo keys are distinct from their untagged variants`() {
        every { libraryApiClient.getBackdropImageUrl(ITEM, 1920, null) } returns "https://s/b-old"
        every { libraryApiClient.getBackdropImageUrl(ITEM, 1920, "bt") } returns "https://s/b-new"
        every { libraryApiClient.getImageUrl(ITEM, "Logo", 400, null, null) } returns "https://s/l-old"
        every { libraryApiClient.getImageUrl(ITEM, "Logo", 400, null, "lt") } returns "https://s/l-new"

        assertEquals("https://s/b-old", provider.getBackdropUrl(ITEM))
        assertEquals("https://s/b-new", provider.getBackdropUrl(ITEM, tag = "bt"))
        assertEquals("https://s/l-old", provider.getLogoUrl(ITEM))
        assertEquals("https://s/l-new", provider.getLogoUrl(ITEM, tag = "lt"))
    }

    private companion object {
        const val ITEM = "item-1"
    }
}
