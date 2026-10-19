package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.HomeLayoutRestoreFetch
import com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior
import com.raulshma.jellyplay.core.model.HomeLayoutConfig
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.PinnedHomeSection
import com.raulshma.jellyplay.core.model.PinnedSectionType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the restore confirmation dialog's content contract — the pure
 * [homeLayoutRestorePreview] builder the timestamp-diff dialog renders: the
 * server write stamp plus BOTH layouts' enabled-section counts, with no
 * formatting or compose dependencies (the dialog's string substitution reads
 * these fields verbatim).
 */
class HomeLayoutRestorePreviewTest {

    @Test
    fun `preview carries the server stamp and both sections' counts`() {
        val serverConfig = HomeLayoutConfig(
            enabledHomeSectionTypes = setOf(HomeSectionType.NEXT_UP, HomeSectionType.RECENTLY_ADDED),
            pinnedHomeSections = listOf(PinnedHomeSection(PinnedSectionType.COLLECTION, "col1", "Movies")),
            continueWatchingClickBehavior = ContinueWatchingClickBehavior.PLAY,
        )
        val offer = HomeLayoutRestoreFetch.Offer(
            config = serverConfig,
            savedAtEpochMillis = 1_760_000_000_000L,
            schemaVersion = 1,
        )

        val preview = homeLayoutRestorePreview(offer, localEnabledSectionCount = 5)

        assertEquals(1_760_000_000_000L, preview.serverSavedAtEpochMillis)
        assertEquals(2, preview.serverSectionCount)
        assertEquals(5, preview.localSectionCount)
    }

    @Test
    fun `preview survives a client-clock-less server stamp`() {
        // The DisplayPreferences transport carries no server timestamp — an
        // older writer may have none; the dialog must degrade, not crash.
        val offer = HomeLayoutRestoreFetch.Offer(
            config = HomeLayoutConfig(),
            savedAtEpochMillis = null,
            schemaVersion = null,
        )

        val preview = homeLayoutRestorePreview(offer, localEnabledSectionCount = 0)

        assertEquals(null, preview.serverSavedAtEpochMillis)
        assertEquals(HomeSectionType.CONFIGURABLE.size, preview.serverSectionCount)
        assertEquals(0, preview.localSectionCount)
    }
}
