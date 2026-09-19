package com.raulshma.jellyplay.core.network.library

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the getChildItemImageUrls fold ([toChildItemImageUrls]): the
 * post-fetch half both client twins run over their fetched rows —
 * Primary-tagged survivors only, each mapped through the caller's width-200
 * Primary URL seam. The request shape ([buildChildItemImagesQuerySpec]) is
 * pinned in [LibraryItemsQuerySpecTest].
 */
class ChildItemImageUrlsTest {

    @Test
    fun `only Primary-tagged rows produce urls, in row order`() {
        val rows = listOf(
            ChildItemImageRow(id = "p1", hasPrimaryImage = true),
            ChildItemImageRow(id = "p2", hasPrimaryImage = false),
            ChildItemImageRow(id = "p3", hasPrimaryImage = true),
        )

        assertEquals(
            listOf("url/p1", "url/p3"),
            rows.toChildItemImageUrls { id -> "url/$id" },
        )
    }

    @Test
    fun `a row without artwork is dropped, not blanked`() {
        // The pre-fold twins emitted a URL only for tagged rows (`else null`)
        // — a cover slot without artwork is no slot, not an empty-string one.
        val rows = listOf(ChildItemImageRow(id = "p2", hasPrimaryImage = false))

        assertEquals(emptyList(), rows.toChildItemImageUrls { id -> "url/$id" })
    }

    @Test
    fun `the url seam sees exactly the surviving ids`() {
        val rows = listOf(
            ChildItemImageRow(id = "a", hasPrimaryImage = true),
            ChildItemImageRow(id = "b", hasPrimaryImage = false),
            ChildItemImageRow(id = "c", hasPrimaryImage = true),
        )
        val seen = mutableListOf<String>()

        rows.toChildItemImageUrls { id ->
            seen.add(id)
            "u:$id"
        }

        assertEquals(listOf("a", "c"), seen)
    }
}
