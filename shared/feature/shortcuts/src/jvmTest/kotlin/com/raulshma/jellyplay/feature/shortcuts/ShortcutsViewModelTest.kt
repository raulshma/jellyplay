package com.raulshma.jellyplay.feature.shortcuts

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.ui.navigation.Route
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Shortcuts ViewModel coverage (requests/syncplay conveyor test style — no
 * legacy suite existed): the requiresAdmin gate over currentUser
 * (null/non-admin/admin), category grouping with declaration-order keys and
 * per-group ordering, the WhileSubscribed(5_000) upstream lifecycle (empty
 * until subscribed, silent after the grace period lapses, recomputes on
 * resubscribe), and 18-item catalog integrity (every Route type resolvable
 * against shared/core:ui NavKey, one distinct route per item).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShortcutsViewModelTest {

    // The conveyor pattern's MainDispatcherRule (:core:testing in legacy
    // suites), inlined — jvmTest has no access to that module
    // (requests/downloads precedent).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var currentUser: MutableStateFlow<UserInfo?>
    private lateinit var authRepository: AuthRepository

    private fun user(isAdmin: Boolean): UserInfo = UserInfo(
        id = "u1",
        name = "User",
        serverAddress = "https://jelly.example",
        accessToken = "token",
        isAdmin = isAdmin,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        currentUser = MutableStateFlow(null)
        authRepository = mockk()
        every { authRepository.currentUser } returns currentUser
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── WhileSubscribed(5_000) lifecycle ────────────────────────────────────

    @Test
    fun `uiState starts empty before the first subscriber`() = runTest {
        val viewModel = ShortcutsViewModel(authRepository)

        // No collector has ever attached: WhileSubscribed never started the
        // upstream map, so the initialValue (empty categories) is what a
        // would-be first frame would see.
        assertEquals(ShortcutsUiState(), viewModel.uiState.value)
    }

    @Test
    fun `upstream stops after the WhileSubscribed grace period and recomputes on resubscribe`() = runTest {
        val viewModel = ShortcutsViewModel(authRepository)

        val states = mutableListOf<ShortcutsUiState>()
        val job = launch { viewModel.uiState.toList(states) }
        advanceUntilIdle()
        assertEquals(15, states.last().categories.values.sumOf { it.size })

        // Detach and let the 5_000 ms grace period lapse; an admin emission
        // in that window must NOT recompute the state value (upstream gone).
        job.cancel()
        advanceTimeBy(5_001)
        currentUser.value = user(isAdmin = true)
        advanceUntilIdle()
        assertEquals(15, viewModel.uiState.value.categories.values.sumOf { it.size })

        // A fresh subscriber restarts the upstream against the CURRENT user.
        val states2 = mutableListOf<ShortcutsUiState>()
        val job2 = launch { viewModel.uiState.toList(states2) }
        advanceUntilIdle()
        assertEquals(18, states2.last().categories.values.sumOf { it.size })
        job2.cancel()
    }

    // ── requiresAdmin filtering ─────────────────────────────────────────────

    @Test
    fun `null user sees only the 15 non-admin shortcuts`() = runTest {
        val viewModel = ShortcutsViewModel(authRepository)
        val states = mutableListOf<ShortcutsUiState>()
        val job = launch { viewModel.uiState.toList(states) }
        advanceUntilIdle()

        val items = states.last().categories.values.flatten()
        assertEquals(15, items.size)
        assertFalse(items.any { it.requiresAdmin })
        job.cancel()
    }

    @Test
    fun `non-admin user sees only the 15 non-admin shortcuts`() = runTest {
        currentUser.value = user(isAdmin = false)
        val viewModel = ShortcutsViewModel(authRepository)
        val states = mutableListOf<ShortcutsUiState>()
        val job = launch { viewModel.uiState.toList(states) }
        advanceUntilIdle()

        assertEquals(15, states.last().categories.values.sumOf { it.size })
        assertFalse(states.last().categories.values.flatten().any { it.requiresAdmin })
        job.cancel()
    }

    @Test
    fun `admin user sees all 18 shortcuts including the three admin ones`() = runTest {
        currentUser.value = user(isAdmin = true)
        val viewModel = ShortcutsViewModel(authRepository)
        val states = mutableListOf<ShortcutsUiState>()
        val job = launch { viewModel.uiState.toList(states) }
        advanceUntilIdle()

        val items = states.last().categories.values.flatten()
        assertEquals(18, items.size)
        val adminRoutes = items.filter { it.requiresAdmin }.map { it.route::class }
        assertEquals(
            listOf(Route.ServerManagement()::class, Route.UserManagement()::class, Route.AdminDashboard::class),
            adminRoutes,
        )
        job.cancel()
    }

    @Test
    fun `admin toggle on a live collector re-filters the catalog`() = runTest {
        val viewModel = ShortcutsViewModel(authRepository)
        val states = mutableListOf<ShortcutsUiState>()
        val job = launch { viewModel.uiState.toList(states) }
        advanceUntilIdle()
        assertEquals(15, states.last().categories.values.sumOf { it.size })

        currentUser.value = user(isAdmin = true)
        advanceUntilIdle()
        assertEquals(18, states.last().categories.values.sumOf { it.size })

        currentUser.value = null
        advanceUntilIdle()
        assertEquals(15, states.last().categories.values.sumOf { it.size })
        job.cancel()
    }

    // ── Category grouping ───────────────────────────────────────────────────

    @Test
    fun `category keys follow enum declaration order via groupBy encounter ordering`() = runTest {
        currentUser.value = user(isAdmin = true)
        val viewModel = ShortcutsViewModel(authRepository)
        val states = mutableListOf<ShortcutsUiState>()
        val job = launch { viewModel.uiState.toList(states) }
        advanceUntilIdle()

        // groupBy preserves first-encounter key order; the catalog declares
        // LIBRARY → SERVICES → SYSTEM, and ShortcutFilter chips render off
        // these keys directly, so the order is user-visible.
        assertEquals(
            listOf(ShortcutCategory.LIBRARY, ShortcutCategory.SERVICES, ShortcutCategory.SYSTEM),
            states.last().categories.keys.toList(),
        )
        job.cancel()
    }

    @Test
    fun `items inside each category keep catalog declaration order`() = runTest {
        currentUser.value = user(isAdmin = true)
        val viewModel = ShortcutsViewModel(authRepository)
        val states = mutableListOf<ShortcutsUiState>()
        val job = launch { viewModel.uiState.toList(states) }
        advanceUntilIdle()

        val categories = states.last().categories
        // Every item route class, per category, in catalog declaration order.
        assertEquals(
            listOf(
                Route.Downloads::class,
                Route.Favorites::class,
                Route.WatchProgressHeatmap::class,
                Route.LiveTv::class,
                Route.Playlists::class,
            ),
            categories.getValue(ShortcutCategory.LIBRARY).map { it.route::class },
        )
        assertEquals(
            listOf(
                Route.SyncPlay::class,
                Route.Requests::class,
                Route.Newsletter::class,
                Route.SeerrSettings()::class,
                Route.ArrQueue::class,
                Route.UpcomingCalendar::class,
                Route.ArrSettings()::class,
            ),
            categories.getValue(ShortcutCategory.SERVICES).map { it.route::class },
        )
        assertEquals(
            listOf(
                Route.Settings::class,
                Route.ServerManagement()::class,
                Route.UserManagement()::class,
                Route.AdminDashboard::class,
                Route.Onboarding::class,
                Route.About::class,
            ),
            categories.getValue(ShortcutCategory.SYSTEM).map { it.route::class },
        )
        job.cancel()
    }

    // ── Catalog integrity ───────────────────────────────────────────────────

    @Test
    fun `catalog holds 18 items with 18 distinct routes and distinct labels`() = runTest {
        currentUser.value = user(isAdmin = true)
        val viewModel = ShortcutsViewModel(authRepository)
        val states = mutableListOf<ShortcutsUiState>()
        val job = launch { viewModel.uiState.toList(states) }
        advanceUntilIdle()

        val items = states.last().categories.values.flatten()
        assertEquals(18, items.size)
        // One distinct route per item — no duplicated destination (Route is a
        // NavKey; identity for objects, structural for the data-class ones).
        assertEquals(18, items.map { it.route::class }.distinct().size)
        // Every item carries its own title/description resource pair.
        assertEquals(18, items.map { it.titleRes }.distinct().size)
        assertEquals(18, items.map { it.descriptionRes }.distinct().size)

        // All 18 route classes resolve against shared/core:ui's NavKey Route
        // (the audit's resolvability check, expressed as the expected set).
        val expected: List<KClass<out Route>> = listOf(
            Route.Downloads::class, Route.Favorites::class, Route.WatchProgressHeatmap::class,
            Route.LiveTv::class, Route.Playlists::class, Route.SyncPlay::class,
            Route.Requests::class, Route.Newsletter::class, Route.SeerrSettings()::class,
            Route.ArrQueue::class, Route.UpcomingCalendar::class, Route.ArrSettings()::class,
            Route.Settings::class, Route.ServerManagement()::class, Route.UserManagement()::class,
            Route.AdminDashboard::class, Route.Onboarding::class, Route.About::class,
        )
        assertEquals(expected, items.map { it.route::class })
        job.cancel()
    }
}
