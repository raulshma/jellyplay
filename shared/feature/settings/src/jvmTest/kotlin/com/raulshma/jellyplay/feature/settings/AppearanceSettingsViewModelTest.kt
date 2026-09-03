package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.PreferencesEditScope
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.datastore.navigation.NavigationStore
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.AppFontScale
import com.raulshma.jellyplay.core.model.AppearanceScreenPreferences
import com.raulshma.jellyplay.core.model.ColorBlindMode
import com.raulshma.jellyplay.core.model.ColorStyle
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior
import com.raulshma.jellyplay.core.model.DateFormatPreference
import com.raulshma.jellyplay.core.model.DreamImageCategory
import com.raulshma.jellyplay.core.model.DreamTransitionStyle
import com.raulshma.jellyplay.core.model.HandMode
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.NavigationCustomizationPreferences
import com.raulshma.jellyplay.core.model.NewsletterSectionType
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.ThemeMode
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertSame

/**
 * Tops up [AppearanceSettingsViewModel] beyond the home-backdrop setter
 * already pinned by [AppearanceSettingsViewModelHomeBackdropTest]: the screen's
 * three state exposures are the injected projection/flows, and — via the
 * AudioSettingsViewModelTest capture-replay harness (a relaxed editor mock
 * never runs the `edit { }` block, so each block is captured and replayed
 * against a stub [PreferencesEditScope]) — every setter group persists through
 * its OWNING store slice. That routing is the invariant the per-screen
 * "recomposes this screen only on its own slice" contract depends on: e.g.
 * `setHideSearchHistory` must land on the experimental store, not appearance,
 * or the appearance recompose-scope would not fire (and vice versa).
 * `resetCategory` stays a direct editor delegation (shared coverage-guarded
 * key list — LibraryLayout jvmTest pattern).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceSettingsViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var store: UserPreferencesStore
    private lateinit var projections: PreferenceProjections
    private lateinit var appearanceStore: AppearanceStore
    private lateinit var editor: PreferencesEditor
    private lateinit var editScope: PreferencesEditScope
    private lateinit var homeDiscoveryStore: HomeDiscoveryStore
    private lateinit var libraryStore: LibraryStore
    private lateinit var experimentalStore: ExperimentalStore
    private lateinit var notificationStore: NotificationStore
    private lateinit var screensaverStore: ScreensaverStore
    private lateinit var navigationStore: NavigationStore

    /** Every `edit { }` block the VM hands the editor, in call order. */
    private val editBlocks =
        mutableListOf<suspend PreferencesEditScope.() -> Unit>()

    private val appearanceScreenPrefs = MutableStateFlow(AppearanceScreenPreferences())
    private val navigationPrefs = MutableStateFlow(NavigationCustomizationPreferences())
    private val showAdvanced = MutableStateFlow(true)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        store = mockk(relaxed = true)
        projections = mockk(relaxed = true)
        appearanceStore = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        editScope = mockk(relaxed = true)
        homeDiscoveryStore = mockk(relaxed = true)
        libraryStore = mockk(relaxed = true)
        experimentalStore = mockk(relaxed = true)
        notificationStore = mockk(relaxed = true)
        screensaverStore = mockk(relaxed = true)
        navigationStore = mockk(relaxed = true)
        editBlocks.clear()

        every { projections.appearanceScreenPreferences } returns appearanceScreenPrefs
        every { projections.navigationCustomizationPreferences } returns navigationPrefs
        every { appearanceStore.showAdvancedSettings } returns showAdvanced
        every { editScope.appearance } returns appearanceStore
        every { editScope.homeDiscovery } returns homeDiscoveryStore
        every { editScope.library } returns libraryStore
        every { editScope.experimental } returns experimentalStore
        every { editScope.notification } returns notificationStore
        every { editScope.screensaver } returns screensaverStore
        every { editScope.navigation } returns navigationStore
        every { editor.edit(capture(editBlocks)) } returns mockk<Job>()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Replays every captured `edit { }` block against the stub scope. */
    private suspend fun replayEdits() = editBlocks.forEach { it.invoke(editScope) }

    private fun viewModel() =
        AppearanceSettingsViewModel(store, projections, appearanceStore, editor)

    // ---------------------------------------------------------------- state

    @Test
    fun `state flows are the injected projections and advanced-settings flag`() {
        val viewModel = viewModel()

        assertSame(appearanceScreenPrefs, viewModel.preferences)
        assertSame(navigationPrefs, viewModel.navigationCustomizationPreferences)
        assertSame(showAdvanced, viewModel.showAdvancedSettings)
    }

    // ------------------------------------------------------- appearance store

    @Test
    fun `theme-color setters persist through the appearance store`() = runTest {
        val viewModel = viewModel()

        // All seven are NAMED editor conveniences (editor.setX), not edit
        // blocks — so the capture-replay harness sees nothing; the routing
        // contract is the editor call itself.
        viewModel.setThemeMode(ThemeMode.DARK)
        viewModel.setDynamicTheming(true)
        viewModel.setOledMode(true)
        viewModel.setContrastLevel(ContrastLevel.HIGH)
        viewModel.setPerformanceMode(false)
        viewModel.setColorStyle(ColorStyle.VIBRANT)
        viewModel.setAccentColorSwatch("#FF5733")
        replayEdits()

        verify(exactly = 1) { editor.setThemeMode(ThemeMode.DARK) }
        verify(exactly = 1) { editor.setDynamicTheming(true) }
        verify(exactly = 1) { editor.setOledMode(true) }
        verify(exactly = 1) { editor.setContrastLevel(ContrastLevel.HIGH) }
        verify(exactly = 1) { editor.setPerformanceMode(false) }
        verify(exactly = 1) { editor.setColorStyle(ColorStyle.VIBRANT) }
        verify(exactly = 1) { editor.setAccentColorSwatch("#FF5733") }
    }

    @Test
    fun `variant, blue-light and motion setters persist through the appearance store`() = runTest {
        val viewModel = viewModel()

        viewModel.setThemeVariant("midnight")
        viewModel.setVariantAccent("midnight", "#00C853")
        viewModel.setBlueLightFilterEnabled(true)
        viewModel.setBlueLightFilterStrength(0.4f)
        viewModel.setReduceMotionEnabled(true)
        viewModel.setBackdropThemeMusicEnabled(true)
        replayEdits()

        coVerify(exactly = 1) { appearanceStore.setThemeVariant("midnight") }
        coVerify(exactly = 1) { appearanceStore.setVariantAccent("midnight", "#00C853") }
        coVerify(exactly = 1) { appearanceStore.setBlueLightFilterEnabled(true) }
        coVerify(exactly = 1) { appearanceStore.setBlueLightFilterStrength(0.4f) }
        coVerify(exactly = 1) { appearanceStore.setReduceMotionEnabled(true) }
        coVerify(exactly = 1) { appearanceStore.setBackdropThemeMusicEnabled(true) }
    }

    @Test
    fun `locale-format and comfort setters persist through the appearance store`() = runTest {
        val viewModel = viewModel()

        // All named editor conveniences — verified on the editor itself.
        viewModel.setColorBlindMode(ColorBlindMode.DEUTERANOPIA)
        viewModel.setScheduledThemeStartHour(21)
        viewModel.setScheduledThemeEndHour(7)
        viewModel.setHandMode(HandMode.LEFT)
        viewModel.setAppFontScale(AppFontScale.LARGE)
        viewModel.setDateFormatPreference(DateFormatPreference.ISO)
        viewModel.setHapticsEnabled(false)
        replayEdits()

        verify(exactly = 1) { editor.setColorBlindMode(ColorBlindMode.DEUTERANOPIA) }
        verify(exactly = 1) { editor.setScheduledThemeStartHour(21) }
        verify(exactly = 1) { editor.setScheduledThemeEndHour(7) }
        verify(exactly = 1) { editor.setHandMode(HandMode.LEFT) }
        verify(exactly = 1) { editor.setAppFontScale(AppFontScale.LARGE) }
        verify(exactly = 1) { editor.setDateFormatPreference(DateFormatPreference.ISO) }
        verify(exactly = 1) { editor.setHapticsEnabled(false) }
    }

    // -------------------------------------------------- home discovery store

    @Test
    fun `home visibility and mode setters persist through the homeDiscovery store`() = runTest {
        val viewModel = viewModel()

        // Mixed routing: setSectionVisible/setShowUnwatchedBadge/... are edit
        // blocks (replay lands them on the homeDiscovery slice), while
        // setHomeMode and setHomeHeroEnabled are named editor conveniences.
        viewModel.setSectionVisible(HomeSectionType.NEXT_UP, false)
        viewModel.setHomeMode(HomeMode.MUSIC)
        viewModel.setHomeHeroEnabled(true)
        viewModel.setShowUnwatchedBadge(true)
        viewModel.setShowWatchedCheckmark(false)
        viewModel.setHideWatchedItems(true)
        viewModel.setShowExternalRatings(true)
        viewModel.setShowClockOnHome(false)
        viewModel.setHideTopHeaderOnScroll(true)
        replayEdits()

        verify(exactly = 1) { editor.setHomeMode(HomeMode.MUSIC) }
        verify(exactly = 1) { editor.setHomeHeroEnabled(true) }
        coVerify(exactly = 1) { homeDiscoveryStore.setSectionVisible(HomeSectionType.NEXT_UP, false) }
        coVerify(exactly = 1) { homeDiscoveryStore.setShowUnwatchedBadge(true) }
        coVerify(exactly = 1) { homeDiscoveryStore.setShowWatchedCheckmark(false) }
        coVerify(exactly = 1) { homeDiscoveryStore.setHideWatchedItems(true) }
        coVerify(exactly = 1) { homeDiscoveryStore.setShowExternalRatings(true) }
        coVerify(exactly = 1) { homeDiscoveryStore.setShowClockOnHome(false) }
        coVerify(exactly = 1) { homeDiscoveryStore.setHideTopHeaderOnScroll(true) }
    }

    @Test
    fun `continue-watching and next-up behavior persists through the homeDiscovery store`() = runTest {
        val viewModel = viewModel()

        viewModel.setHomeSectionOrder(listOf(HomeSectionType.PINNED, HomeSectionType.NEXT_UP))
        viewModel.setContinueWatchingClickBehavior(ContinueWatchingClickBehavior.PLAY)
        viewModel.setMergeContinueWatchingAndNextUp(true)
        viewModel.unhideAllCwItems()
        viewModel.setNextUpMaxDays(14)
        viewModel.setNextUpRewatching(true)
        replayEdits()

        coVerify(exactly = 1) {
            homeDiscoveryStore.setHomeSectionOrder(listOf(HomeSectionType.PINNED, HomeSectionType.NEXT_UP))
        }
        coVerify(exactly = 1) {
            homeDiscoveryStore.setContinueWatchingClickBehavior(ContinueWatchingClickBehavior.PLAY)
        }
        coVerify(exactly = 1) { homeDiscoveryStore.setMergeContinueWatchingAndNextUp(true) }
        coVerify(exactly = 1) { homeDiscoveryStore.unhideAllCwItems() }
        coVerify(exactly = 1) { homeDiscoveryStore.setNextUpMaxDays(14) }
        coVerify(exactly = 1) { homeDiscoveryStore.setNextUpRewatching(true) }
    }

    // -------------------------------------------------------- library store

    @Test
    fun `library browsing toggles persist through the library store`() = runTest {
        val viewModel = viewModel()

        viewModel.setHideEpisodeThumbnails(true)
        viewModel.setSkipSpecials(true)
        viewModel.setCompactEpisodeList(true)
        viewModel.setConfirmLibraryReset(false)
        viewModel.setLibraryViewMode(LibraryViewMode.MASONRY)
        replayEdits()

        coVerify(exactly = 1) { libraryStore.setHideEpisodeThumbnails(true) }
        coVerify(exactly = 1) { libraryStore.setSkipSpecials(true) }
        coVerify(exactly = 1) { libraryStore.setCompactEpisodeList(true) }
        coVerify(exactly = 1) { libraryStore.setConfirmLibraryReset(false) }
        coVerify(exactly = 1) { libraryStore.setLibraryViewMode(LibraryViewMode.MASONRY) }
    }

    // --------------------------------------------------- experimental store

    @Test
    fun `share and search-history toggles persist through the experimental store`() = runTest {
        val viewModel = viewModel()

        viewModel.setHideSearchHistory(true)
        viewModel.setShowShareMediaOption(true)
        replayEdits()

        coVerify(exactly = 1) { experimentalStore.setHideSearchHistory(true) }
        coVerify(exactly = 1) { experimentalStore.setShowShareMediaOption(true) }
    }

    // ---------------------------------------------------- notification store

    @Test
    fun `newsletter setters persist through the notification store`() = runTest {
        val viewModel = viewModel()

        viewModel.setEnabledNewsletterSections(setOf(NewsletterSectionType.NEXT_UP))
        viewModel.setNewsletterSectionOrder(listOf(NewsletterSectionType.CURATED_PICKS))
        viewModel.setNewsletterEnabled(true)
        viewModel.setNewsletterDayOfWeek(5)
        replayEdits()

        coVerify(exactly = 1) {
            notificationStore.setEnabledNewsletterSections(setOf(NewsletterSectionType.NEXT_UP))
        }
        coVerify(exactly = 1) {
            notificationStore.setNewsletterSectionOrder(listOf(NewsletterSectionType.CURATED_PICKS))
        }
        coVerify(exactly = 1) { notificationStore.setNewsletterEnabled(true) }
        coVerify(exactly = 1) { notificationStore.setNewsletterDayOfWeek(5) }
    }

    // ----------------------------------------------------- screensaver store

    @Test
    fun `screensaver setters persist through the screensaver store`() = runTest {
        val viewModel = viewModel()

        viewModel.setDreamShowTitle(true)
        viewModel.setDreamImageCategories(setOf(DreamImageCategory.MOVIES, DreamImageCategory.SERIES))
        viewModel.setDreamSlideshowIntervalMs(30_000L)
        viewModel.setDreamKenBurnsEnabled(false)
        viewModel.setDreamTransitionStyle(DreamTransitionStyle.SLIDE)
        replayEdits()

        coVerify(exactly = 1) { screensaverStore.setDreamShowTitle(true) }
        coVerify(exactly = 1) {
            screensaverStore.setDreamImageCategories(setOf(DreamImageCategory.MOVIES, DreamImageCategory.SERIES))
        }
        coVerify(exactly = 1) { screensaverStore.setDreamSlideshowIntervalMs(30_000L) }
        coVerify(exactly = 1) { screensaverStore.setDreamKenBurnsEnabled(false) }
        coVerify(exactly = 1) { screensaverStore.setDreamTransitionStyle(DreamTransitionStyle.SLIDE) }
    }

    // ------------------------------------------------------ navigation store

    @Test
    fun `navigation-bar setters persist through the navigation store`() = runTest {
        val viewModel = viewModel()

        viewModel.setHideBottomNavOnScroll(true)
        viewModel.setHiddenNavItems(setOf("LiveTv"))
        viewModel.setNavItemOrder(listOf("Search", "Home"))
        replayEdits()

        coVerify(exactly = 1) { navigationStore.setHideBottomNavOnScroll(true) }
        coVerify(exactly = 1) { navigationStore.setHiddenNavItems(setOf("LiveTv")) }
        coVerify(exactly = 1) { navigationStore.setNavItemOrder(listOf("Search", "Home")) }
    }

    // ------------------------------------------------------ reset + advanced

    @Test
    fun `home-search and nav-label rows use the editor's named setters`() = runTest {
        val viewModel = viewModel()

        viewModel.setShowSettingsInHomeSearch(true)
        viewModel.setNavBarShowLabels(false)
        advanceUntilIdle()

        // Shared rows pinned to the named setters so onboarding and settings
        // cannot drift onto different write paths.
        verify(exactly = 1) { editor.setShowSettingsInHomeSearch(true) }
        verify(exactly = 1) { editor.setNavBarShowLabels(false) }
    }

    @Test
    fun `resetCategory delegates to the shared editor machinery`() {
        val viewModel = viewModel()

        viewModel.resetCategory(PreferenceResetCategory.APPEARANCE)

        verify(exactly = 1) { editor.resetCategory(PreferenceResetCategory.APPEARANCE) }
    }

    @Test
    fun `advanced-settings toggle persists through the appearance store`() = runTest {
        val viewModel = viewModel()

        viewModel.setShowAdvancedSettings(false)
        replayEdits()

        coVerify(exactly = 1) { appearanceStore.setShowAdvancedSettings(false) }
    }
}
