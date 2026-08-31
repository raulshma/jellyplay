package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [homeSurface] — the render-branch fold that replaced MainHomeContent's
 * three differently-derived branch conditions. Pure JVM: construct
 * [HomeUiState] + [OfflineHomeContent], assert the sealed surface. The fold
 * relies on the equivalence a non-online
 * [com.raulshma.jellyplay.core.model.OfflineMode] ⟺
 * [HomeRenderSource.Offline.Explicit] — that equivalence itself is pinned by
 * `HomeRenderSourceTest`; here it licenses driving every case through
 * `renderSource` alone, so the former `offlineMode != ONLINE` branch terms
 * and the render-source terms select the same surface.
 */
class HomeSurfaceTest {

    private val emptyOffline = OfflineHomeContent(
        library = emptyList(),
        episodes = emptyList(),
        sections = emptyList(),
        itemsById = emptyMap(),
    )

    private val populatedOffline = emptyOffline.copy(
        library = listOf(OfflineMediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)),
        sections = listOf(HomeSection(id = "movies", title = "Movies", type = HomeSectionType.LATEST_MEDIA, items = emptyList())),
    )

    private fun state(
        renderSource: HomeRenderSource = HomeRenderSource.Online,
        error: String? = null,
        sections: List<HomeSection> = emptyList(),
        homeMode: HomeMode = HomeMode.VIDEO,
        isGoingOnline: Boolean = false,
    ) = HomeUiState(
        renderSource = renderSource,
        error = error,
        sections = sections,
        homeMode = homeMode,
        isGoingOnline = isGoingOnline,
    )

    // ── HardError ────────────────────────────────────────────────────────────

    @Test
    fun `failed fetch over confirmed-empty offline library is HardError`() {
        val surface = homeSurface(
            state(renderSource = HomeRenderSource.Online, error = "boom", sections = emptyList()),
            emptyOffline,
        )
        assertTrue(surface is HomeSurface.HardError)
    }

    @Test
    fun `error with sections still on screen renders Content`() {
        val surface = homeSurface(
            state(
                error = "partial",
                sections = listOf(HomeSection(id = "movies", title = "Movies", type = HomeSectionType.LATEST_MEDIA, items = emptyList())),
            ),
            emptyOffline,
        )
        assertTrue(surface is HomeSurface.Content)
    }

    @Test
    fun `FallbackPending suppresses HardError even with error and empty sections`() {
        // Downloads may yet exist — the pending window renders loading content.
        val surface = homeSurface(
            state(renderSource = HomeRenderSource.FallbackPending, error = "boom"),
            emptyOffline,
        )
        val content = surface as HomeSurface.Content
        assertTrue(content.feed is HomeFeed.Offline)
        assertTrue((content.feed as HomeFeed.Offline).isLoading)
        assertEquals(HomeRenderSource.FallbackPending, content.renderSource)
    }

    @Test
    fun `HardError beats Music for a failed fetch in music mode`() {
        // The cross-branch precedence corner: MUSIC mode would delegate to
        // the music slot, but a failed fetch over a confirmed-empty library
        // wins first.
        val surface = homeSurface(
            state(error = "boom", homeMode = HomeMode.MUSIC),
            emptyOffline,
        )
        assertTrue(surface is HomeSurface.HardError)
    }

    // ── NoDownloads ──────────────────────────────────────────────────────────

    @Test
    fun `explicit offline with nothing downloaded is NoDownloads`() {
        val surface = homeSurface(
            state(renderSource = HomeRenderSource.Offline.Explicit, isGoingOnline = true),
            emptyOffline,
        )
        assertEquals(HomeSurface.NoDownloads(isGoingOnline = true), surface)
    }

    @Test
    fun `NoDownloads beats Music for explicit offline with nothing downloaded`() {
        // The precedence corner: MUSIC mode would delegate to the music slot,
        // but explicit-offline + empty library wins first.
        val surface = homeSurface(
            state(renderSource = HomeRenderSource.Offline.Explicit, homeMode = HomeMode.MUSIC),
            emptyOffline,
        )
        assertTrue(surface is HomeSurface.NoDownloads)
    }

    @Test
    fun `implicit offline never shows NoDownloads even with empty derived sections`() {
        // Implicit requires a populated library (the render-source fold), but
        // the mode filter can still derive zero sections — that renders the
        // (empty) offline content, not the go-online empty state.
        val surface = homeSurface(
            state(renderSource = HomeRenderSource.Offline.Implicit),
            emptyOffline.copy(library = listOf(OfflineMediaItem(id = "a1", name = "A", mediaType = MediaType.MUSIC))),
        )
        assertTrue(surface is HomeSurface.Content)
    }

    // ── Music ────────────────────────────────────────────────────────────────

    @Test
    fun `online music delegates to the music slot`() {
        val surface = homeSurface(state(homeMode = HomeMode.MUSIC), emptyOffline)
        assertEquals(HomeSurface.Music, surface)
    }

    @Test
    fun `music keeps the host slot during implicit fallback`() {
        val surface = homeSurface(
            state(renderSource = HomeRenderSource.Offline.Implicit, homeMode = HomeMode.MUSIC),
            populatedOffline,
        )
        assertEquals(HomeSurface.Music, surface)
    }

    @Test
    fun `explicit offline music renders the offline content instead`() {
        val surface = homeSurface(
            state(renderSource = HomeRenderSource.Offline.Explicit, homeMode = HomeMode.MUSIC),
            populatedOffline,
        )
        val content = surface as HomeSurface.Content
        assertTrue(content.feed is HomeFeed.Offline)
        assertEquals(HomeRenderSource.Offline.Explicit, content.renderSource)
    }

    // ── Content render source ────────────────────────────────────────────────

    @Test
    fun `online content carries the online feed and the online source`() {
        val sections = listOf(HomeSection(id = "movies", title = "Movies", type = HomeSectionType.LATEST_MEDIA, items = emptyList()))
        val surface = homeSurface(state(sections = sections), emptyOffline)
        val content = surface as HomeSurface.Content
        val feed = content.feed as HomeFeed.Online
        assertEquals(sections, feed.sections)
        assertFalse(feed.partialLoadError)
        assertEquals(HomeRenderSource.Online, content.renderSource)
    }

    @Test
    fun `explicit offline content carries the explicit source`() {
        val surface = homeSurface(
            state(renderSource = HomeRenderSource.Offline.Explicit),
            populatedOffline,
        )
        val content = surface as HomeSurface.Content
        assertEquals(HomeRenderSource.Offline.Explicit, content.renderSource)
        assertFalse((content.feed as HomeFeed.Offline).isLoading)
    }

    @Test
    fun `implicit offline content carries the implicit source`() {
        val surface = homeSurface(
            state(renderSource = HomeRenderSource.Offline.Implicit),
            populatedOffline,
        )
        val content = surface as HomeSurface.Content
        assertEquals(HomeRenderSource.Offline.Implicit, content.renderSource)
    }
}
