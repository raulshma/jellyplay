package com.raulshma.jellyplay.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.Test

class HomeSectionDescriptorTest {

    @Test
    fun staticTypes_carryNetworkRowIdentity() {
        // The ids/titles the network impl previously hardcoded at every
        // construction site — pinned here so the descriptor can never silently
        // change a persisted/cache-keyed id or a rendered row header (the
        // header is the displayName; the old separate `title` field once
        // drifted to "NextUp" while every other surface showed "Next Up").
        assertEquals("continue_watching" to "Continue Watching", staticIdentity(HomeSectionType.CONTINUE_WATCHING))
        assertEquals("continue_reading" to "Continue Reading", staticIdentity(HomeSectionType.CONTINUE_READING))
        assertEquals("next_up" to "Next Up", staticIdentity(HomeSectionType.NEXT_UP))
        assertEquals("recently_added" to "Recently Added", staticIdentity(HomeSectionType.RECENTLY_ADDED))
        assertEquals("recommendations" to "Recommended For You", staticIdentity(HomeSectionType.RECOMMENDATIONS))
    }

    @Test
    fun staticTypes_renderRowHeaderFromDisplayName() {
        // Guards the drift this catalogue fixed: NEXT_UP's network title once
        // read "NextUp" while every other surface showed "Next Up". The row
        // header is built from the descriptor's single displayName now, so
        // this pins that section() keeps using it.
        HomeSectionType.entries
            .filter { it.descriptor.id != null }
            .forEach { type ->
                assertEquals(type.displayName, type.descriptor.section(items = emptyList()).title)
            }
    }

    @Test
    fun latestMedia_resolvesPerLibraryIdentityAtFetchTime() {
        val descriptor = HomeSectionType.LATEST_MEDIA.descriptor
        assertNull(descriptor.id)
        assertEquals(
descriptor.idFor("folder-42"),
"latest_folder-42",
)
        assertEquals(
descriptor.titleFor("Movies"),
"Latest Movies",
)
    }

    @Test
    fun pinned_resolvesCompositeIdButCarriesInstanceTitle() {
        val descriptor = HomeSectionType.PINNED.descriptor
        assertNull(descriptor.id)
        assertEquals(
descriptor.idFor("COLLECTION_7"),
"pinned_COLLECTION_7",
)
    }

    @Test
    fun pluginRow_resolvesInstanceIdsUnderTheJellyplayPrefix_andIsNotConfigurable() {
        // The plugin rows (companion server plugin, ADR 0010) are gated by
        // the capability registry, never the layout config — pinned here so
        // PLUGIN_ROW can never silently become user-configurable (which
        // would add it to the persisted default order).
        val descriptor = HomeSectionType.PLUGIN_ROW.descriptor
        assertNull(descriptor.id)
        assertFalse(descriptor.isConfigurable)
        assertEquals("jellyplay_seasonal", descriptor.idFor("seasonal"))
        assertEquals("jellyplay_custom_Top 10 of 2025", descriptor.idFor("custom_Top 10 of 2025"))
        assertEquals("seasonal", descriptor.instanceIdFor("jellyplay_seasonal"))
        assertEquals("custom_Top 10 of 2025", descriptor.instanceIdFor("jellyplay_custom_Top 10 of 2025"))
        assertNull(descriptor.instanceIdFor("discover_row-1"), "foreign prefixes resolve to no instance id")
    }

    @Test
    fun neverFetchedTypes_haveNoStaticIdentity() {
        listOf(HomeSectionType.FAVORITES, HomeSectionType.LIVE_TV, HomeSectionType.DOWNLOADED)
            .forEach { type ->
                assertNull(type.descriptor.id)
            }
    }

    @Test
    fun enumProperties_delegateToDescriptor() {
        // The enum keeps delegating accessors so ~47 referencing files compile
        // unchanged; this pins the delegation contract.
        HomeSectionType.entries.forEach { type ->
            assertEquals(type.descriptor.displayName, type.displayName)
            assertEquals(type.descriptor.description, type.description)
            assertEquals(type.descriptor.isConfigurable, type.isConfigurable)
        }
    }

    @Test
    fun configurableList_mirrorsDescriptorFlags_inDefaultOrder() {
        // CONFIGURABLE's ORDER defines the default home section order, so it is
        // spelled out on the enum; this guards it against drifting from the
        // descriptor's isConfigurable flags.
        assertEquals(
            HomeSectionType.entries.filter { it.descriptor.isConfigurable }.toSet(),
            HomeSectionType.CONFIGURABLE.toSet(),
        )
        assertEquals(
            listOf(
                HomeSectionType.CONTINUE_WATCHING,
                HomeSectionType.CONTINUE_READING,
                HomeSectionType.NEXT_UP,
                HomeSectionType.LATEST_MEDIA,
                HomeSectionType.RECENTLY_ADDED,
                HomeSectionType.RECOMMENDATIONS,
                HomeSectionType.DISCOVER,
            ),
            HomeSectionType.CONFIGURABLE,
        )
    }

    private fun staticIdentity(type: HomeSectionType): Pair<String, String> {
        val descriptor = type.descriptor
        return requireNotNull(descriptor.id) to descriptor.displayName
    }
}
