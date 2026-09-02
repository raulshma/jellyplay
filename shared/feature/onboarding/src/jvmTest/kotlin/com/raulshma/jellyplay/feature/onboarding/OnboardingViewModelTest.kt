package com.raulshma.jellyplay.feature.onboarding

import com.raulshma.jellyplay.core.datastore.PreferencesEditScope
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.navigation.NavigationStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.model.ColorStyle
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.OnboardingPreferences
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.seerr.SeerrAuthMethod
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * OnboardingViewModel coverage (requests/downloads conveyor test style — no
 * legacy suite existed): step-state coercion and skip semantics, the
 * completion write through the real PreferencesEditor (fire-and-forget edit
 * over the owning AppRuntimeStateStore), every named editor delegation the
 * wizard surfaces (appearance / home / navigation / playback / video / audio
 * / subtitle / security), and the Seerr credential fan-out (prefs store vs
 * secure-credentials store routing).
 *
 * The editor under test is REAL, built over a PreferencesEditScope of relaxed
 * store mocks on the test scheduler — that way `setThemeMode(DARK)` is
 * verified against appearance.setThemeMode(DARK) through the actual edit{}
 * launch, not against a mocked editor's shape.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    // MainDispatcherRule (:core:testing), inlined — jvmTest has no access to
    // that module (requests/downloads conveyor port pattern). The ViewModel
    // has no init-time coroutine work (both flows are plain property
    // assignments), but viewModelScope itself needs Main installed.
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var projections: PreferenceProjections
    private lateinit var seerrPreferencesStore: SeerrPreferencesStore
    private lateinit var seerrSecureCredentialsStore: SeerrSecureCredentialsStore

    // PreferencesEditScope stores behind the real editor (relaxed: the edit
    // block's Unit-returning suspend setters just need to be callable).
    private lateinit var playbackStore: PlaybackStore
    private lateinit var appearanceStore: AppearanceStore
    private lateinit var videoPlayerStore: VideoPlayerStore
    private lateinit var homeDiscoveryStore: HomeDiscoveryStore
    private lateinit var audioStore: AudioStore
    private lateinit var navigationStore: NavigationStore
    private lateinit var securityStore: SecurityStore
    private lateinit var subtitleStore: SubtitleLanguageStore
    private lateinit var appRuntimeStateStore: AppRuntimeStateStore

    private val onboardingPreferences = MutableStateFlow(OnboardingPreferences())
    private val seerrPreferences = MutableStateFlow(SeerrPreferences())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        projections = mockk()
        seerrPreferencesStore = mockk(relaxed = true)
        seerrSecureCredentialsStore = mockk(relaxed = true)
        playbackStore = mockk(relaxed = true)
        appearanceStore = mockk(relaxed = true)
        videoPlayerStore = mockk(relaxed = true)
        homeDiscoveryStore = mockk(relaxed = true)
        audioStore = mockk(relaxed = true)
        navigationStore = mockk(relaxed = true)
        securityStore = mockk(relaxed = true)
        subtitleStore = mockk(relaxed = true)
        appRuntimeStateStore = mockk(relaxed = true)

        every { projections.onboardingPreferences } returns onboardingPreferences
        every { seerrPreferencesStore.preferences } returns seerrPreferences
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Real editor over mocked stores. The editor's fire-and-forget scope is
     * the TestScope itself (NOT backgroundScope, whose tasks
     * advanceUntilIdle does not flush in this coroutines-test version) —
     * every edit{} launch is a scheduler task that advanceUntilIdle drains.
     */
    private fun TestScope.buildEditor() = PreferencesEditor(
        scope = this,
        editScope = PreferencesEditScope(
            playback = playbackStore,
            appearance = appearanceStore,
            videoPlayer = videoPlayerStore,
            // Stores the wizard never touches — relaxed filler for the
            // 19-store scope ctor.
            downloads = mockk(relaxed = true),
            engine = mockk(relaxed = true),
            homeDiscovery = homeDiscoveryStore,
            audio = audioStore,
            audioEffects = mockk(relaxed = true),
            audioCache = mockk(relaxed = true),
            library = mockk(relaxed = true),
            navigation = navigationStore,
            networkOffline = mockk(relaxed = true),
            notification = mockk(relaxed = true),
            screensaver = mockk(relaxed = true),
            security = securityStore,
            subtitle = subtitleStore,
            syncPlayCast = mockk(relaxed = true),
            experimental = mockk(relaxed = true),
            appRuntimeState = appRuntimeStateStore,
        ),
        store = mockk<UserPreferencesStore>(relaxed = true),
    )

    private fun TestScope.newViewModel() = OnboardingViewModel(
        projections = projections,
        seerrPreferencesStore = seerrPreferencesStore,
        seerrSecureCredentialsStore = seerrSecureCredentialsStore,
        editor = buildEditor(),
    )

    @Test
    fun `init exposes projected preferences and seerr flow`() = runTest(mainDispatcher) {
        val vm = newViewModel()

        assertSame(onboardingPreferences, vm.preferences)
        assertSame(seerrPreferences, vm.seerrPreferences)
        assertEquals(0, vm.currentStep.value)
    }

    @Test
    fun `setStep coerces out-of-range values into the wizard bounds`() = runTest(mainDispatcher) {
        val vm = newViewModel()

        vm.setStep(-3)
        assertEquals(0, vm.currentStep.value)

        vm.setStep(OnboardingStep.count + 5)
        assertEquals(OnboardingStep.count - 1, vm.currentStep.value)

        vm.setStep(4)
        assertEquals(4, vm.currentStep.value)
    }

    @Test
    fun `nextStep advances by exactly one page`() = runTest(mainDispatcher) {
        val vm = newViewModel()

        vm.nextStep()
        vm.nextStep()

        assertEquals(2, vm.currentStep.value)
    }

    @Test
    fun `skipOnboarding jumps to the final review step instead of completing`() = runTest(mainDispatcher) {
        val vm = newViewModel()

        vm.skipOnboarding()

        assertEquals(OnboardingStep.count - 1, vm.currentStep.value)
        coVerify(exactly = 0) { appRuntimeStateStore.setOnboardingCompleted(any()) }
    }

    @Test
    fun `completeOnboarding persists the completion flag through the editor`() = runTest(mainDispatcher) {
        val vm = newViewModel()

        vm.completeOnboarding()
        advanceUntilIdle()

        coVerify(exactly = 1) { appRuntimeStateStore.setOnboardingCompleted(true) }
    }

    @Test
    fun `appearance delegations reach the appearance store`() = runTest(mainDispatcher) {
        val vm = newViewModel()

        vm.setThemeMode(ThemeMode.DARK)
        vm.setDynamicTheming(true)
        vm.setOledMode(true)
        vm.setContrastLevel(ContrastLevel.HIGH)
        vm.setAccentColorSwatch("sunset")
        vm.setColorStyle(ColorStyle.VIBRANT)
        vm.setPerformanceMode(true)
        advanceUntilIdle()

        coVerify(exactly = 1) { appearanceStore.setThemeMode(ThemeMode.DARK) }
        coVerify(exactly = 1) { appearanceStore.setDynamicTheming(true) }
        coVerify(exactly = 1) { appearanceStore.setOledMode(true) }
        coVerify(exactly = 1) { appearanceStore.setContrastLevel(ContrastLevel.HIGH) }
        coVerify(exactly = 1) { appearanceStore.setAccentColorSwatch("sunset") }
        coVerify(exactly = 1) { appearanceStore.setColorStyle(ColorStyle.VIBRANT) }
        coVerify(exactly = 1) { appearanceStore.setPerformanceMode(true) }
    }

    @Test
    fun `home and navigation delegations reach their owning stores`() = runTest(mainDispatcher) {
        val vm = newViewModel()
        val sections = setOf(HomeSectionType.CONFIGURABLE.first())

        vm.setHomeHeroEnabled(false)
        vm.setHomeMode(HomeMode.MUSIC)
        vm.setEnabledHomeSectionTypes(sections)
        vm.setNavBarShowLabels(false)
        advanceUntilIdle()

        coVerify(exactly = 1) { homeDiscoveryStore.setHomeHeroEnabled(false) }
        coVerify(exactly = 1) { homeDiscoveryStore.setHomeMode(HomeMode.MUSIC) }
        coVerify(exactly = 1) { homeDiscoveryStore.setEnabledHomeSectionTypes(sections) }
        coVerify(exactly = 1) { navigationStore.setNavBarShowLabels(false) }
    }

    @Test
    fun `playback and video player delegations reach their owning stores`() = runTest(mainDispatcher) {
        val vm = newViewModel()

        vm.setPreferredPlayer(PlayerType.MPV)
        vm.setStreamingQuality(StreamingQuality.FHD_1080P)
        vm.setVideoSeekDurationMs(30_000L)
        vm.setVideoGesturesEnabled(false)
        vm.setVideoDefaultOrientation(OrientationMode.SENSOR)
        vm.setVideoAutoplayNext(false)
        advanceUntilIdle()

        coVerify(exactly = 1) { playbackStore.setPreferredPlayer(PlayerType.MPV) }
        coVerify(exactly = 1) { playbackStore.setStreamingQuality(StreamingQuality.FHD_1080P) }
        coVerify(exactly = 1) { videoPlayerStore.setVideoSeekDurationMs(30_000L) }
        coVerify(exactly = 1) { videoPlayerStore.setVideoGesturesEnabled(false) }
        coVerify(exactly = 1) { videoPlayerStore.setVideoDefaultOrientation(OrientationMode.SENSOR) }
        coVerify(exactly = 1) { videoPlayerStore.setVideoAutoplayNext(false) }
    }

    @Test
    fun `audio delegations reach the audio store`() = runTest(mainDispatcher) {
        val vm = newViewModel()

        vm.setAudioDefaultSpeed(1.5f)
        vm.setGaplessEnabled(false)
        vm.setCrossfadeDurationMs(5_000L)
        vm.setAudioNormalizationEnabled(true)
        vm.setAudioAutoplayNext(false)
        advanceUntilIdle()

        coVerify(exactly = 1) { audioStore.setAudioDefaultSpeed(1.5f) }
        coVerify(exactly = 1) { audioStore.setAudioGaplessEnabled(false) }
        coVerify(exactly = 1) { audioStore.setAudioCrossfadeDurationMs(5_000L) }
        coVerify(exactly = 1) { audioStore.setAudioNormalizationEnabled(true) }
        coVerify(exactly = 1) { audioStore.setAudioAutoplayNext(false) }
    }

    @Test
    fun `subtitle delegations reach the subtitle store`() = runTest(mainDispatcher) {
        val vm = newViewModel()
        val style = SubtitleStyle(fontSize = 32)

        vm.setSubtitleStyle(style)
        vm.setPreferredSubtitleLanguage("de")
        advanceUntilIdle()

        coVerify(exactly = 1) { subtitleStore.setSubtitleStyle(style) }
        coVerify(exactly = 1) { subtitleStore.setPreferredSubtitleLanguage("de") }
    }

    @Test
    fun `security delegations reach the security store and hashPin passes through`() = runTest(mainDispatcher) {
        val vm = newViewModel()
        every { securityStore.hashPin("1234") } returns "hashed-1234"

        vm.setPinLockEnabled(true)
        vm.setPinHash("hashed-1234")
        vm.setBiometricLockEnabled(true)
        vm.setAutoLockTimerMs(60_000L)
        assertEquals("hashed-1234", vm.hashPin("1234"))
        advanceUntilIdle()

        coVerify(exactly = 1) { securityStore.setPinLockEnabled(true) }
        coVerify(exactly = 1) { securityStore.setPinHash("hashed-1234") }
        coVerify(exactly = 1) { securityStore.setBiometricLockEnabled(true) }
        coVerify(exactly = 1) { securityStore.setAutoLockTimerMs(60_000L) }
    }

    @Test
    fun `seerr preference setters fan out to the prefs store`() = runTest(mainDispatcher) {
        val vm = newViewModel()

        vm.setSeerrServerUrl("http://seerr.local")
        vm.setSeerrAuthMethod(SeerrAuthMethod.JELLYFIN)
        vm.setSeerrUsername("user")
        vm.setSeerrEmail("user@example.com")
        vm.setSeerrEnabled(true)
        vm.setSeerrSearchEnabled(true)
        vm.setSeerrRecommendationsEnabled(true)
        vm.setSeerrDiscoverEnabled(true)
        vm.setSeerrStreamingRegion("DE")
        vm.setSeerrDiscoverRegion("FR")
        vm.seerrDisconnect()
        advanceUntilIdle()

        coVerify(exactly = 1) { seerrPreferencesStore.setServerUrl("http://seerr.local") }
        coVerify(exactly = 1) { seerrPreferencesStore.setAuthMethod(SeerrAuthMethod.JELLYFIN) }
        coVerify(exactly = 1) { seerrPreferencesStore.setUsername("user") }
        coVerify(exactly = 1) { seerrPreferencesStore.setEmail("user@example.com") }
        coVerify(exactly = 1) { seerrPreferencesStore.setEnabled(true) }
        coVerify(exactly = 1) { seerrPreferencesStore.setSearchEnabled(true) }
        coVerify(exactly = 1) { seerrPreferencesStore.setRecommendationsEnabled(true) }
        coVerify(exactly = 1) { seerrPreferencesStore.setDiscoverEnabled(true) }
        coVerify(exactly = 1) { seerrPreferencesStore.setStreamingRegion("DE") }
        coVerify(exactly = 1) { seerrPreferencesStore.setDiscoverRegion("FR") }
        coVerify(exactly = 1) { seerrPreferencesStore.disconnect() }
    }

    @Test
    fun `seerr credentials route to the secure credentials store`() = runTest(mainDispatcher) {
        val vm = newViewModel()

        vm.setSeerrApiKey("api-key")
        vm.setSeerrPassword("pw")
        advanceUntilIdle()

        verify(exactly = 1) { seerrSecureCredentialsStore.setApiKey("api-key") }
        verify(exactly = 1) { seerrSecureCredentialsStore.setPassword("pw") }
        assertTrue(vm.seerrPreferences.value.serverUrl.isEmpty())
    }
}
