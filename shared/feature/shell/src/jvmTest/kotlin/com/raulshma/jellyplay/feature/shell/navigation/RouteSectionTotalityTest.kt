package com.raulshma.jellyplay.feature.shell.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.ui.navigation.NavigationState
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.ShortcutAction
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The route TOTALITY guard [ShellSectionRegistryTest] deliberately does not
 * give: the registry suite pins the ledger MECHANISM over hand-wired
 * miniatures, this suite pins route COVERAGE over the REAL graph — the same
 * [shellEntryProvider] construction both shells render with, backed by the
 * real ~20-section [appSections] (the only copy: no hand-list of "which
 * routes have a section" exists here to drift). Every [Route] sealed leaf
 * must resolve to a REAL entry (not the [UnregisteredEntryContentKey]
 * sentinel) — otherwise a forgotten section renders a BLANK NavDisplay
 * entry on Android (nav3's fallback entry; desktop at least dead-end-guards).
 *
 * The graph under test is the two-shell union: [appSections] (everything the
 * shared sections register) plus TEST DOUBLES for the two per-shell
 * registrations that cannot leave their shells:
 *
 *  - `Route.PlayOnCompanion` — the ANDROID shell's inline entry
 *    (app MainNavDisplay; its content needs the app module's
 *    PlayOnCompanionScreen over its activity-scoped PlayOnViewModel — no
 *    commonMain section exists);
 *  - `Route.VideoPlayer` — the DESKTOP shell's probe-conditional entry
 *    (DesktopNavScaffold; registered when a video-surface story exists). On
 *    Android the route never enters the in-nav graph at all — the
 *    PlaybackHostRouter navigateFilter hands it to PlayerActivity / the
 *    external player.
 *
 * The doubles register the registration FACT (a one-line `entry<>`), not a
 * copy of content. If one of these routes ever gains a real section in
 * [appSections], the duplicate registration fails LOUDLY here — remove the
 * double in the same commit.
 *
 * The [documentedAndroidOnlyLeaves] pins are the routes whose REAL builders
 * live in androidMain (player-live's `livePlayerSection`, subtitle-tester's
 * `subtitleTesterSection`) and so cannot cross to this JVM lane — the same
 * omissions DesktopAppRoot's KDoc records for desktop ("What is deliberately
 * NOT wired … LiveTvChannelPlayer — the live-TV surface has no desktop engine
 * host; SubtitleTester — androidMain-only, no commonMain section at all").
 * The Android shell registers both; desktop dead-ends them by policy. Each
 * pin carries its one-line reason and is pinned BOTH WAYS: a leaf that
 * resolves here while pinned is stale and must be dropped from the set.
 *
 * A new [Route] without a section fails with its name in the message — add a
 * section (preferred: commonMain, so both shells render it) or, if it is
 * genuinely shell-bound, a documented pin here plus the shell's own
 * registration/guard story.
 */
class RouteSectionTotalityTest {

    /**
     * The sealed [Route] leaves that resolve as UNREGISTERED through the
     * test's union graph, each with its one-line reason. Adding an entry here
     * is a documented product decision, never a convenience.
     */
    private val documentedAndroidOnlyLeaves: Map<KClass<out Route>, String> = mapOf(
        Route.LiveTvChannelPlayer::class to
            "Android-only via player-live's androidMain livePlayerSection — the live-TV " +
            "surface has no desktop engine host (DesktopAppRoot KDoc); desktop dead-ends it in the guard",
        Route.SubtitleTester::class to
            "Android-only via subtitle-tester's androidMain subtitleTesterSection — " +
            "androidMain-only screen, no commonMain section (DesktopAppRoot KDoc)",
    )

    /** The shared hooks double — the builders only READ these at render time. */
    private fun testHost(): ShellHostHooks = ShellHostHooks(
        home = ShellHomeHooks(
            homeMode = HomeMode.VIDEO,
            onHomeModeChange = {},
            playOnRedirect = null,
            surpriseRequests = emptyFlow(),
            shortcutActions = ShortcutActionSurface(
                focusHome = {},
                armSurprise = null,
                requestPlayOn = null,
                toggleOffline = null,
                onUnsupported = { },
            ),
        ),
        audio = ShellAudioClicks(onNowPlayingClick = {}, onAmbientClick = {}),
        settings = ShellSettingsHooks(onLogout = {}, onCheckForUpdates = {}),
        admin = ShellAdminHooks(isAdmin = { false }, isRefreshingAdmin = { false }, onRefreshAdmin = {}),
        search = ShellSearchHooks(
            pendingSearchQuery = MutableStateFlow(null),
            onConsumeSearchQuery = {},
        ),
    )

    /**
     * The REAL graph, the shells' own construction: [shellEntryProvider] over
     * a plain in-memory [NavigationState] (NavigatorTest's idiom — the
     * composition-bound rememberNavigationState is not needed to BUILD the
     * graph; entry content lambdas are never invoked at registration).
     */
    private fun shellGraph(): ShellSections {
        val navigationState = NavigationState(
            startRoute = Route.Home,
            topLevelRoute = mutableStateOf(Route.Home),
            backStacks = mapOf(Route.Home to NavBackStack(Route.Home)),
        )
        return shellEntryProvider(
            navigator = Navigator(navigationState),
            host = testHost(),
            registry = ShellSectionRegistry(),
        ) {
            // The two per-shell registrations the test doubles stand for —
            // see the class KDoc for which shell each belongs to and why.
            entry<Route.PlayOnCompanion> { }
            entry<Route.VideoPlayer> { }
        }
    }

    /**
     * One synthetic instance of the sealed leaf — objects as-is, data classes
     * over `callBy` of their non-default parameters (every leaf's required
     * parameters are primitives today). nav3 resolves entries by key CLASS,
     * so the fabricated values never matter.
     */
    private fun instanceOf(leaf: KClass<out Route>): Route {
        leaf.objectInstance?.let { return it }
        val constructor = leaf.primaryConstructor
            ?: error("Route leaf ${leaf.simpleName} has no primary constructor")
        val arguments = constructor.parameters
            .filterNot { it.isOptional }
            .associateWith { parameter ->
                when (parameter.type.classifier) {
                    String::class -> ""
                    Int::class -> 0
                    Long::class -> 0L
                    Boolean::class -> false
                    else -> error(
                        "RouteSectionTotalityTest cannot fabricate ${leaf.simpleName}." +
                            "${parameter.name}: ${parameter.type} — extend the synthetic-value table",
                    )
                }
            }
        return constructor.callBy(arguments)
    }

    @Test
    fun `every route leaf resolves to a real entry through the shell graph`() {
        val graph = shellGraph()
        val registry = graph.registry
        val leaves = Route::class.sealedSubclasses
        assertNotEquals(0, leaves.size, "the sealed Route hierarchy must enumerate its leaves")

        val unresolved = leaves
            .filterNot { it in documentedAndroidOnlyLeaves }
            .map { it to instanceOf(it) }
            .filterNot { (_, route) -> registry.isRegistered(route) }
            .map { (leaf, _) -> leaf.simpleName ?: leaf.toString() }

        assertTrue(
            unresolved.isEmpty(),
            "Routes with NO section registration (renders a blank NavDisplay entry on Android):\n" +
                unresolved.joinToString(separator = "\n  - ", prefix = "  - ") +
                "\nRegister the route in appSections / a shell's extra sections, or — if it is " +
                "genuinely shell-bound — add a documented pin to RouteSectionTotalityTest.",
        )
    }

    @Test
    fun `the documented android-only pins are actually unregistered here`() {
        val registry = shellGraph().registry

        documentedAndroidOnlyLeaves.forEach { (leaf, reason) ->
            assertFalse(
                registry.isRegistered(instanceOf(leaf)),
                "${leaf.simpleName} is pinned as Android-only ($reason) but now resolves through " +
                    "the shared graph — drop the stale pin",
            )
        }
    }

    @Test
    fun `every pin names a real sealed leaf`() {
        // A pin whose route was deleted (or renamed out of the hierarchy) is
        // dead documentation.
        documentedAndroidOnlyLeaves.keys.forEach { leaf ->
            assertTrue(
                leaf in Route::class.sealedSubclasses,
                "${leaf.simpleName} is pinned but is no longer a sealed Route leaf — drop the pin",
            )
        }
        assertEquals(
            documentedAndroidOnlyLeaves.keys.size,
            documentedAndroidOnlyLeaves.keys.toSet().size,
        )
    }
}
