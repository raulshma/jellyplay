package com.raulshma.jellyplay.core.data.util

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaImageTags
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PersonInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [DefaultArtworkUrls]' slot policy — the ONE home of the
 * tag-selection rules (which tag belongs to which URL target):
 *  1. [ArtworkUrls.poster] targets the item's own id + own primary tag;
 *  2. [ArtworkUrls.parentPoster] targets parentId + the parent tag, with the
 *     album tag as the music-track fallback, and degrades to "" without a
 *     parent;
 *  3. [ArtworkUrls.seriesPoster] targets seriesId + the series tag, "" without
 *     a series;
 *  4. backdrop/logo/person slots forward their own tags at the historical
 *     widths (1920 backdrop, 400 poster/logo default);
 *  5. [ArtworkUrls.EMPTY] renders every slot empty — the preview stand-in.
 *
 * A null tag forwarding as null matters as much as a non-null one: untagged
 * URLs stay byte-identical to the pre-tag-threading emission.
 */
class ArtworkUrlsTest {

    private lateinit var provider: ImageUrlProvider
    private lateinit var artwork: DefaultArtworkUrls

    @BeforeTest
    fun setup() {
        provider = mockk(relaxed = true)
        artwork = DefaultArtworkUrls(provider)
    }

    @Test
    fun `poster targets the item id with its own primary tag`() {
        artwork.poster(item(primary = "p1"))

        verify(exactly = 1) { provider.getImageUrl("m1", maxWidth = 400, tag = "p1") }
    }

    @Test
    fun `poster honors an explicit width and passes a null tag through as null`() {
        artwork.poster(item(primary = null), maxWidth = 300)

        verify(exactly = 1) { provider.getImageUrl("m1", maxWidth = 300, tag = null) }
    }

    @Test
    fun `parentPoster targets the parent id with the parent tag`() {
        artwork.parentPoster(item(primary = "own", parentId = "season-1", tags = MediaImageTags(parentPrimary = "season-tag")))

        verify(exactly = 1) { provider.getImageUrl("season-1", maxWidth = 400, tag = "season-tag") }
    }

    @Test
    fun `parentPoster falls back to the album tag for tracks`() {
        artwork.parentPoster(item(parentId = "album-1", tags = MediaImageTags(parentPrimary = null, album = "album-tag")))

        verify(exactly = 1) { provider.getImageUrl("album-1", maxWidth = 400, tag = "album-tag") }
    }

    @Test
    fun `parentPoster degrades to empty without a parent`() {
        assertEquals("", artwork.parentPoster(item(parentId = null, tags = MediaImageTags(parentPrimary = "orphan-tag"))))

        verify(exactly = 0) { provider.getImageUrl(any(), any(), any()) }
    }

    @Test
    fun `seriesPoster targets the series id with the series tag`() {
        artwork.seriesPoster(item(seriesId = "series-1", tags = MediaImageTags(seriesPrimary = "series-tag")))

        verify(exactly = 1) { provider.getImageUrl("series-1", maxWidth = 400, tag = "series-tag") }
    }

    @Test
    fun `seriesPoster degrades to empty without a series`() {
        assertEquals("", artwork.seriesPoster(item(seriesId = null, tags = MediaImageTags(seriesPrimary = "orphan-tag"))))

        verify(exactly = 0) { provider.getImageUrl(any(), any(), any()) }
    }

    @Test
    fun `posterFor resolves the tagged own poster when the id matches the candidate`() {
        artwork.posterFor(item(primary = "p1"), itemId = "m1", maxWidth = 300)

        verify(exactly = 1) { provider.getImageUrl("m1", maxWidth = 300, tag = "p1") }
    }

    @Test
    fun `posterFor falls back to the raw id-level URL when the candidate misses`() {
        // Both miss shapes — a null candidate (nothing hydrated) and a
        // candidate for a DIFFERENT id (the parent-series leg) — resolve the
        // raw id-level URL with no tag.
        artwork.posterFor(null, itemId = "m1", maxWidth = 300)
        artwork.posterFor(item(id = "other", primary = "p1"), itemId = "m1", maxWidth = 300)

        verify(exactly = 2) { provider.getImageUrl("m1", maxWidth = 300, tag = null) }
    }

    @Test
    fun `backdrop targets the item id with the backdrop tag at the historical width`() {
        artwork.backdrop(item(backdrop = "b1"))

        verify(exactly = 1) { provider.getBackdropUrl("m1", maxWidth = 1920, tag = "b1") }
    }

    @Test
    fun `backdrop honors an explicit width`() {
        artwork.backdrop(item(backdrop = "b1"), maxWidth = 1280)

        verify(exactly = 1) { provider.getBackdropUrl("m1", maxWidth = 1280, tag = "b1") }
    }

    @Test
    fun `logo targets the item id with the logo tag`() {
        artwork.logo(item(logo = "l1"))

        verify(exactly = 1) { provider.getLogoUrl("m1", tag = "l1") }
    }

    @Test
    fun `personPortrait targets the person id with their own tag`() {
        val portrait = PersonInfo(id = "p-1", name = "Ada", role = null, type = "Actor", primaryImageTag = "face-tag")
        every { provider.getImageUrl("p-1", maxWidth = 400, tag = "face-tag") } returns "https://s/ada"

        assertEquals("https://s/ada", artwork.personPortrait(portrait))

        verify(exactly = 1) { provider.getImageUrl("p-1", maxWidth = 400, tag = "face-tag") }
    }

    @Test
    fun `EMPTY renders every slot empty`() {
        val item = item(primary = "p1", parentId = "par", seriesId = "ser", backdrop = "b1", logo = "l1")

        assertEquals("", ArtworkUrls.EMPTY.poster(item))
        assertEquals("", ArtworkUrls.EMPTY.parentPoster(item))
        assertEquals("", ArtworkUrls.EMPTY.seriesPoster(item))
        assertEquals("", ArtworkUrls.EMPTY.backdrop(item))
        assertEquals("", ArtworkUrls.EMPTY.logo(item))
        assertEquals("", ArtworkUrls.EMPTY.personPortrait(PersonInfo(id = "p", name = "n", role = null, type = "", primaryImageTag = null)))
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private fun item(
        id: String = "m1",
        primary: String? = null,
        backdrop: String? = null,
        logo: String? = null,
        parentId: String? = null,
        seriesId: String? = null,
        tags: MediaImageTags? = null,
    ) = MediaItem(
        id = id,
        name = "Item",
        mediaType = MediaType.MOVIE,
        parentId = parentId,
        seriesId = seriesId,
        imageTags = tags ?: MediaImageTags(primary = primary, backdrop = backdrop, logo = logo),
    )
}
