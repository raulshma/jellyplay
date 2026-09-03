package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.ProviderSearchOutcome
import com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository
import com.raulshma.jellyplay.core.datastore.SubtitleProviderPreferencesStore
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the Subtitle-provider settings wiring (LibraryLayout jvmTest pattern:
 * mockk collaborators + real Result/[MutableStateFlow] stubs + inlined
 * Main-dispatcher rule). Saves route to the preferences/secure-credential
 * stores (blank Wyzie keys CLEAR the stored credential; OpenSubtitles saves
 * drop the cached JWT only when username/password actually changed), and the
 * Test action verifies the in-progress FORM text through the repository —
 * failing fast on blank credentials without a network round-trip, and never
 * writing to the stores.
 *
 * Later top-up round: also pins the remaining clear/test branches — a
 * null/blank OpenSubtitles username CLEARS the stored credential, a blank
 * password normalizes to null, `testOpenSubtitlesCredentials` verifies the
 * form text (Connected + blank fail-fast), and the Wyzie test surfaces the
 * repository's Error message verbatim plus the Skipped → "Provider not
 * configured" mapping.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubtitleProviderSettingsViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var preferencesStore: SubtitleProviderPreferencesStore
    private lateinit var subtitleProviderRepository: SubtitleProviderRepository
    private val preferencesState =
        MutableStateFlow(SubtitleProviderPreferences(wyzieEnabled = true))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        preferencesStore = mockk(relaxed = true)
        subtitleProviderRepository = mockk(relaxed = true)
        every { preferencesStore.preferences } returns preferencesState
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        SubtitleProviderSettingsViewModel(preferencesStore, subtitleProviderRepository)

    @Test
    fun `preferences seeds from the store flow once subscribed`() = runTest {
        val viewModel = viewModel()

        backgroundScope.launch { viewModel.preferences.collect {} }
        advanceUntilIdle()

        assertEquals(true, viewModel.preferences.value.wyzieEnabled)
    }

    @Test
    fun `credentialSnapshot reads the secure credential store`() {
        every { preferencesStore.getCredentials(SubtitleProviderKind.WYZIE) } returns
            SubtitleProviderCredentials.Wyzie("stored-key")
        val viewModel = viewModel()

        assertEquals(
            SubtitleProviderCredentials.Wyzie("stored-key"),
            viewModel.credentialSnapshot(SubtitleProviderKind.WYZIE),
        )
    }

    @Test
    fun `enable toggles route to the preferences store`() = runTest {
        val viewModel = viewModel()

        viewModel.setWyzieEnabled(true)
        viewModel.setOpenSubtitlesEnabled(false)
        advanceUntilIdle()

        coVerify(exactly = 1) { preferencesStore.setWyzieEnabled(true) }
        coVerify(exactly = 1) { preferencesStore.setOpenSubtitlesEnabled(false) }
    }

    @Test
    fun `saveWyzieApiKey trims and persists the key`() = runTest {
        val viewModel = viewModel()

        viewModel.saveWyzieApiKey("  key  ")
        advanceUntilIdle()

        verify(exactly = 1) {
            preferencesStore.setCredentials(SubtitleProviderKind.WYZIE, SubtitleProviderCredentials.Wyzie("key"))
        }
    }

    @Test
    fun `saveWyzieApiKey with a blank key clears the stored credential`() = runTest {
        val viewModel = viewModel()

        viewModel.saveWyzieApiKey("   ")
        advanceUntilIdle()

        verify(exactly = 1) { preferencesStore.clearCredentials(SubtitleProviderKind.WYZIE) }
    }

    @Test
    fun `testWyzieApiKey with a blank key fails fast without the repository`() = runTest {
        val viewModel = viewModel()

        viewModel.testWyzieApiKey("   ")
        advanceUntilIdle()

        assertEquals(
            SubtitleProviderSettingsViewModel.ProviderStatus.Error("Enter credentials first"),
            viewModel.providerStatus.value[SubtitleProviderKind.WYZIE],
        )
        coVerify(exactly = 0) {
            subtitleProviderRepository.verifyCredentials(SubtitleProviderKind.WYZIE, any())
        }
    }

    @Test
    fun `testWyzieApiKey success reports Connected`() = runTest {
        coEvery {
            subtitleProviderRepository.verifyCredentials(SubtitleProviderKind.WYZIE, SubtitleProviderCredentials.Wyzie("key"))
        } returns ProviderSearchOutcome.Success(emptyList())
        val viewModel = viewModel()

        viewModel.testWyzieApiKey(" key ")
        advanceUntilIdle()

        assertEquals(
            SubtitleProviderSettingsViewModel.ProviderStatus.Connected,
            viewModel.providerStatus.value[SubtitleProviderKind.WYZIE],
        )
        // Test probes the form text only — nothing is written to the stores.
        verify(exactly = 0) { preferencesStore.setCredentials(any(), any()) }
    }

    @Test
    fun `saveOpenSubtitlesCredentials drops the cached jwt when the password changes`() = runTest {
        every { preferencesStore.getCredentials(SubtitleProviderKind.OPENSUBTITLES) } returns
            SubtitleProviderCredentials.OpenSubtitles(
                username = "user", password = "old", jwt = "token", jwtExpiresAt = 42L,
            )
        val viewModel = viewModel()
        val saved = slot<SubtitleProviderCredentials>()

        viewModel.saveOpenSubtitlesCredentials("user", "new")
        advanceUntilIdle()

        verify(exactly = 1) {
            preferencesStore.setCredentials(eq(SubtitleProviderKind.OPENSUBTITLES), capture(saved))
        }
        val credentials = saved.captured as SubtitleProviderCredentials.OpenSubtitles
        assertEquals("user", credentials.username)
        assertEquals("new", credentials.password)
        assertNull(credentials.jwt)
        assertEquals(0L, credentials.jwtExpiresAt)
    }

    @Test
    fun `saveOpenSubtitlesCredentials preserves the jwt when credentials are unchanged`() = runTest {
        every { preferencesStore.getCredentials(SubtitleProviderKind.OPENSUBTITLES) } returns
            SubtitleProviderCredentials.OpenSubtitles(
                username = "user", password = "same", jwt = "token", jwtExpiresAt = 42L,
            )
        val viewModel = viewModel()
        val saved = slot<SubtitleProviderCredentials>()

        viewModel.saveOpenSubtitlesCredentials("user", "same")
        advanceUntilIdle()

        verify(exactly = 1) {
            preferencesStore.setCredentials(eq(SubtitleProviderKind.OPENSUBTITLES), capture(saved))
        }
        val credentials = saved.captured as SubtitleProviderCredentials.OpenSubtitles
        assertEquals("token", credentials.jwt)
        assertEquals(42L, credentials.jwtExpiresAt)
    }

    // ------------------------------------------------- top-ups: clear + test paths

    @Test
    fun `saveOpenSubtitlesCredentials with a blank username clears the stored credential`() = runTest {
        val viewModel = viewModel()

        viewModel.saveOpenSubtitlesCredentials("   ", "whatever")
        advanceUntilIdle()

        verify(exactly = 1) { preferencesStore.clearCredentials(SubtitleProviderKind.OPENSUBTITLES) }
        verify(exactly = 0) { preferencesStore.setCredentials(any(), any()) }
    }

    @Test
    fun `saveOpenSubtitlesCredentials with a null username clears the stored credential`() = runTest {
        val viewModel = viewModel()

        viewModel.saveOpenSubtitlesCredentials(null, null)
        advanceUntilIdle()

        verify(exactly = 1) { preferencesStore.clearCredentials(SubtitleProviderKind.OPENSUBTITLES) }
    }

    @Test
    fun `saveOpenSubtitlesCredentials trims and normalizes a blank password to null`() = runTest {
        every { preferencesStore.getCredentials(SubtitleProviderKind.OPENSUBTITLES) } returns null
        val viewModel = viewModel()
        val saved = slot<SubtitleProviderCredentials>()

        viewModel.saveOpenSubtitlesCredentials(" user ", "   ")
        advanceUntilIdle()

        val credentials = saved.captured as SubtitleProviderCredentials.OpenSubtitles
        assertEquals("user", credentials.username)
        assertNull(credentials.password, "a blank password must not be persisted as whitespace")
        assertNull(credentials.jwt)
        assertEquals(0L, credentials.jwtExpiresAt)
    }

    @Test
    fun `testOpenSubtitlesCredentials verifies the form text and reports Connected`() = runTest {
        coEvery {
            subtitleProviderRepository.verifyCredentials(
                SubtitleProviderKind.OPENSUBTITLES,
                SubtitleProviderCredentials.OpenSubtitles(username = "user", password = "pw"),
            )
        } returns ProviderSearchOutcome.Success(emptyList())
        val viewModel = viewModel()

        viewModel.testOpenSubtitlesCredentials(" user ", " pw ")
        advanceUntilIdle()

        assertEquals(
            SubtitleProviderSettingsViewModel.ProviderStatus.Connected,
            viewModel.providerStatus.value[SubtitleProviderKind.OPENSUBTITLES],
        )
        // Test probes the form text only — nothing is written to the stores.
        verify(exactly = 0) { preferencesStore.setCredentials(any(), any()) }
        verify(exactly = 0) { preferencesStore.clearCredentials(any()) }
    }

    @Test
    fun `testOpenSubtitlesCredentials blank form fails fast without the repository`() = runTest {
        val viewModel = viewModel()

        viewModel.testOpenSubtitlesCredentials("   ", "pw")
        advanceUntilIdle()

        assertEquals(
            SubtitleProviderSettingsViewModel.ProviderStatus.Error("Enter credentials first"),
            viewModel.providerStatus.value[SubtitleProviderKind.OPENSUBTITLES],
        )
        coVerify(exactly = 0) {
            subtitleProviderRepository.verifyCredentials(SubtitleProviderKind.OPENSUBTITLES, any())
        }
    }

    @Test
    fun `testWyzieApiKey surfaces the repository error message verbatim`() = runTest {
        coEvery {
            subtitleProviderRepository.verifyCredentials(SubtitleProviderKind.WYZIE, any())
        } returns ProviderSearchOutcome.Error("403 Forbidden")
        val viewModel = viewModel()

        viewModel.testWyzieApiKey("key")
        advanceUntilIdle()

        assertEquals(
            SubtitleProviderSettingsViewModel.ProviderStatus.Error("403 Forbidden"),
            viewModel.providerStatus.value[SubtitleProviderKind.WYZIE],
        )
    }

    @Test
    fun `a skipped provider verification surfaces the not-configured error`() = runTest {
        coEvery {
            subtitleProviderRepository.verifyCredentials(SubtitleProviderKind.WYZIE, any())
        } returns ProviderSearchOutcome.Skipped
        val viewModel = viewModel()

        viewModel.testWyzieApiKey("key")
        advanceUntilIdle()

        assertEquals(
            SubtitleProviderSettingsViewModel.ProviderStatus.Error("Provider not configured"),
            viewModel.providerStatus.value[SubtitleProviderKind.WYZIE],
        )
    }
}
