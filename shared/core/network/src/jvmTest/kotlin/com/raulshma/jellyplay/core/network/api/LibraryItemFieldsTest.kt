package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.network.library.LIST_PROJECTION_FIELDS
import org.jellyfin.sdk.model.api.ItemFields
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the hoisted SDK resolution of the commonMain list projection
 * ([LIST_PROJECTION_FIELDS] stays the policy home): the resolved
 * [LIST_ITEM_FIELDS] must agree wire-name-for-wire-name with the policy (the
 * resolver itself fails fast on SDK enum drift), and the shared genre
 * composition must be exactly the projection plus Genres.
 */
class LibraryItemFieldsTest {

    @Test
    fun `LIST_ITEM_FIELDS resolves the shared policy wire names against the SDK enum`() {
        assertEquals(LIST_PROJECTION_FIELDS, LIST_ITEM_FIELDS.map { it.serialName })
    }

    @Test
    fun `the genre composition is the list projection plus Genres`() {
        assertEquals(LIST_ITEM_FIELDS + ItemFields.GENRES, LIST_ITEM_FIELDS_WITH_GENRES)
    }
}
