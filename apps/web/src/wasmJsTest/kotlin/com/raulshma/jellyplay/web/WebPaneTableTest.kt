package com.raulshma.jellyplay.web

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Browser-free pin of the web shell's PANE TABLE derivation — the twin-list
 * fold that replaced the hand-mirrored landing affordances + `entry<…>` blocks
 * ([WebPane] / [buildWebPanes] / [registerWebPanes] in WebAppRoot.kt;
 * [com.raulshma.jellyplay.feature.shell.navigation.ShellSectionRegistryTest]
 * is the desktop model). Same lane as [WebBackStackMirrorTest]: kotlin.test on
 * the browser test lane, no compose rendering — provider resolution only BUILDS
 * entries, it never invokes composable content, so the table is exercised
 * without a controller instance or a Composer.
 *
 * Pinned here:
 *  - EVERY pane key resolves to a real (non-fallback) entry — a row that
 *    renders a landing button but forgets its registration is the exact drift
 *    this fold exists to kill, so each key is resolved through the same
 *    class-keyed provider form [registerWebPanes] installs;
 *  - keys OUTSIDE the table fall to the sentinel — the derived set is exactly
 *    the table, not more (Route.Settings is unrouted on web; Route.SeerrDetail /
 *    Route.ArrSettings are deliberate non-rows, pushed programmatically);
 *  - the landing vocabulary itself: labels (in order — the e2e lane anchors on
 *    accessible names), the single outlined row, and key uniqueness (a
 *    duplicate would also fail registration with nav3's require).
 */
class WebPaneTableTest {

    /**
     * contentKey stamped on the fallback entry: reference identity against it
     * is the exact registered/unregistered test (the desktop sentinel pattern).
     */
    private val unregisteredContentKey = Any()

    /** The production table, with a controller provider that can never fire. */
    private fun panes(): List<WebPane<*>> = buildWebPanes(
        seerrController = { error("pane content is never rendered by this test") },
        addEntry = { },
    )

    /**
     * A resolver over ONLY the derived registrations — the test-sized twin of
     * WebAppRoot's entryProvider block (whose remaining hand-written entries
     * — WebLanding/ArrSettings/SeerrDetail — are deliberately absent here).
     */
    private fun providerFor(panes: List<WebPane<*>>): (NavKey) -> NavEntry<NavKey> =
        entryProvider(
            fallback = { key ->
                NavEntry(key, unregisteredContentKey, emptyMap<String, Any>(), {})
            },
        ) {
            registerWebPanes(scope = this, panes = panes, onBack = { })
        }

    // ── every affordance key is registered ──────────────────────────────────

    @Test
    fun everyPaneKeyResolvesToARealEntry() {
        val panes = panes()
        val provider = providerFor(panes)

        panes.forEach { pane ->
            val entry = provider(pane.key)
            assertNotSame(
                unregisteredContentKey,
                entry.contentKey,
                "pane '${pane.label}' renders a landing button but is not registered",
            )
        }
    }

    @Test
    fun keysOutsideThePaneTableFallToTheSentinel() {
        val provider = providerFor(panes())

        // Route.Settings: no web pane (the settings root stays unrouted —
        // Main.kt's settingsModule note).
        assertSame(unregisteredContentKey, provider(Route.Settings).contentKey)
        // The deliberate non-rows: pushed programmatically from other panes,
        // never landing buttons, so the table alone must not register them.
        assertSame(unregisteredContentKey, provider(Route.SeerrDetail(550, "movie")).contentKey)
        assertSame(unregisteredContentKey, provider(Route.ArrSettings()).contentKey)
    }

    // ── table invariants (the landing contract) ─────────────────────────────

    @Test
    fun theTableCarriesTheLandingVocabularyInOrder() {
        // The order + wording are load-bearing: tools/e2e/web-verify.mjs finds
        // the ConnectedCard buttons by accessible name, top to bottom.
        assertEquals(
            listOf(
                "Connection details",
                "Requests",
                "Calendar",
                "Seerr",
                "Arr queue",
                "Onboarding",
                "Diagnostics",
            ),
            panes().map { it.label },
        )
    }

    @Test
    fun onlyTheGatedDiagnosticsRowIsOutlined() {
        val panes = panes()

        assertEquals(
            listOf("Diagnostics"),
            panes.filter { it.isOutlined }.map { it.label },
            "exactly the secondary tooling row reads as outlined",
        )
    }

    @Test
    fun paneKeysAreUnique() {
        val panes = panes()

        // Class identity is the registration identity — a duplicate class key
        // would crash nav3's builder with `require`; pin the same fact as a
        // table invariant so it surfaces here first.
        assertEquals(panes.size, panes.map { it.key::class }.toSet().size)
        assertTrue(panes.isNotEmpty(), "the optionality contract cannot be empty-by-accident")
    }
}
