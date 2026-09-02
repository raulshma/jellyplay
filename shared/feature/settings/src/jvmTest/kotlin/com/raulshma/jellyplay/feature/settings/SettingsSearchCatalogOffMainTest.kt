package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.ui.settingssearch.ResolvedSettingsItem
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the structural half of the settings-open ANR fix: the whole-catalog
 * string resolve (one blocking compose-resources read per entry when cold)
 * runs on Dispatchers.Default, never on the caller's thread — the caller in
 * these tests plays the composition/main-dispatcher role the screen's
 * produceState producers run on. Uses a fake resolver seam, so no real
 * compose-resources runtime is involved (headless JVM tests cannot drive its
 * desktop system environment).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsSearchCatalogOffMainTest {

    private fun resolver(
        seenThreads: MutableList<String>? = null,
    ): suspend (List<SettingsSearchItem>) -> List<ResolvedSettingsItem> = { items ->
        seenThreads?.add(Thread.currentThread().name)
        items.map { ResolvedSettingsItem(it, "t:${it.id}", "s:${it.id}", "c") }
    }

    @Test
    fun `resolved runs the catalog resolve on a Default worker`() = runTest {
        val callerThread = Thread.currentThread().name
        val threads = mutableListOf<String>()

        SettingsSearchCatalog.resolved(resolver(threads))

        assertTrue(threads.isNotEmpty(), "resolver must have run")
        assertTrue(
            threads.single().contains("DefaultDispatcher-worker"),
            "catalog resolve ran on '${threads.single()}', expected a DefaultDispatcher worker " +
                "(caller was '$callerThread')",
        )
    }

    @Test
    fun `resolved hands the full catalog to the resolver`() = runTest {
        var resolvedIds: List<String>? = null
        val fake: suspend (List<SettingsSearchItem>) -> List<ResolvedSettingsItem> = { items ->
            resolvedIds = items.map { it.id }
            emptyList()
        }

        SettingsSearchCatalog.resolved(fake)

        assertEquals(SettingsSearchCatalog.items.map { it.id }, resolvedIds)
    }

    @Test
    fun `recentItems projects ids in recency order and drops stale ids`() = runTest {
        val resolved = SettingsSearchCatalog.recentItems(
            recentIds = listOf("experimental", "unknown_setting", "logout", "experimental"),
            resolveCatalog = resolver(),
        )

        // Most-recent-first projection (recency order, not catalog order);
        // duplicates and stale ids preserved/dropped like the old screen code.
        assertEquals(listOf("experimental", "logout", "experimental"), resolved.map { it.id })
    }

    @Test
    fun `recentItems with no recents never resolves the catalog`() = runTest {
        var resolveCalls = 0
        val counting: suspend (List<SettingsSearchItem>) -> List<ResolvedSettingsItem> = { items ->
            resolveCalls++
            items.map { ResolvedSettingsItem(it, "t", "s", "c") }
        }

        val resolved = SettingsSearchCatalog.recentItems(emptyList(), counting)

        assertTrue(resolved.isEmpty())
        assertEquals(0, resolveCalls, "empty recents must short-circuit before the catalog resolve")
    }

    @Test
    fun `recentItems resolve also runs on a Default worker`() = runTest {
        val threads = mutableListOf<String>()

        SettingsSearchCatalog.recentItems(listOf("logout"), resolver(threads))

        assertTrue(
            threads.single().contains("DefaultDispatcher-worker"),
            "recents projection must not resolve the catalog on the caller's thread",
        )
    }
}
