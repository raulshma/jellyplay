package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the MetadataRefreshOption → endpoint-parameter mapping (the four
 * jellyfin-web "Refresh metadata" modes onto the metadata/image refresh modes
 * + replace flags of POST /Items/{itemId}/Refresh).
 */
class MetadataRefreshOptionTest {

    @Test
    fun `default scans for missing metadata and images only`() {
        assertEquals(
            MetadataRefreshParams("Default", "Default", replaceAllMetadata = false, replaceAllImages = false),
            MetadataRefreshOption.DEFAULT.toRefreshParams(),
        )
    }

    @Test
    fun `full validation re-scans metadata without replacing images`() {
        assertEquals(
            MetadataRefreshParams("FullRefresh", "Default", replaceAllMetadata = false, replaceAllImages = false),
            MetadataRefreshOption.FULL_VALIDATION.toRefreshParams(),
        )
    }

    @Test
    fun `replace all metadata runs a full refresh with the replace flag`() {
        assertEquals(
            MetadataRefreshParams("FullRefresh", "FullRefresh", replaceAllMetadata = true, replaceAllImages = false),
            MetadataRefreshOption.REPLACE_ALL_METADATA.toRefreshParams(),
        )
    }

    @Test
    fun `replace images skips metadata entirely`() {
        assertEquals(
            MetadataRefreshParams("None", "FullRefresh", replaceAllMetadata = false, replaceAllImages = true),
            MetadataRefreshOption.REPLACE_IMAGES.toRefreshParams(),
        )
    }

    @Test
    fun `every option resolves distinct parameters`() {
        val all = MetadataRefreshOption.entries.map { it.toRefreshParams() }
        assertEquals(all.size, all.toSet().size)
    }
}
