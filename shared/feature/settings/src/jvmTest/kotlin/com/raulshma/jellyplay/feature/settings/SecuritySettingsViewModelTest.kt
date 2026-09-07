package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.datastore.PreferencesEditScope
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.SecurityPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the Security settings preference-mirror wiring (LibraryLayout jvmTest
 * pattern): the screen's state is the [PreferenceProjections.securityPreferences]
 * slice, every write persists through the security store inside the VM's
 * `edit { }` command (captured and replayed against a stub scope, since a
 * relaxed editor mock never runs the block — hashing + verification policy
 * lives in the SecurityStore, pinned there), and Quick Connect authorization
 * routes the repository Result into the screen callback's (success, error)
 * pair with the failure message verbatim.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SecuritySettingsViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var store: UserPreferencesStore
    private lateinit var projections: PreferenceProjections
    private lateinit var appearanceStore: AppearanceStore
    private lateinit var editor: PreferencesEditor
    private lateinit var authRepository: AuthRepository
    private lateinit var editScope: PreferencesEditScope
    private lateinit var securityStore: SecurityStore

    /** Every `edit { }` block the VM hands the editor, in call order. */
    private val editBlocks = mutableListOf<suspend PreferencesEditScope.() -> Unit>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        store = mockk(relaxed = true)
        projections = mockk(relaxed = true)
        appearanceStore = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        editScope = mockk(relaxed = true)
        securityStore = mockk(relaxed = true)
        editBlocks.clear()
        every { projections.securityPreferences } returns MutableStateFlow(SecurityPreferences())
        every { appearanceStore.showAdvancedSettings } returns MutableStateFlow(false)
        every { editScope.security } returns securityStore
        every { editor.edit(capture(editBlocks)) } returns mockk<Job>()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        SecuritySettingsViewModel(
            store,
            projections,
            AdvancedSettingsGate(appearanceStore, editor),
            editor,
            authRepository,
        )

    @Test
    fun `securityPreferences exposes the security projection flow`() = runTest {
        val seeded = MutableStateFlow(SecurityPreferences(pinLockEnabled = true))
        every { projections.securityPreferences } returns seeded
        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue(viewModel.securityPreferences.value.pinLockEnabled)
    }

    @Test
    fun `pin and lock writes persist through the security store inside edit`() = runTest {
        val viewModel = viewModel()

        viewModel.edit { it.security.setPin("1234") }
        viewModel.edit { it.security.clearPin() }
        viewModel.edit { it.security.setBiometricLockEnabled(true) }
        viewModel.edit { it.security.setUsePinForPlayerLock(false) }
        viewModel.edit { it.security.setAutoLockTimerMs(60_000) }
        viewModel.edit { it.security.setRemoteControlEnabled(true) }
        advanceUntilIdle()
        editBlocks.forEach { it.invoke(editScope) }

        coVerify(exactly = 1) { securityStore.setPin("1234") }
        coVerify(exactly = 1) { securityStore.clearPin() }
        coVerify(exactly = 1) { securityStore.setBiometricLockEnabled(true) }
        coVerify(exactly = 1) { securityStore.setUsePinForPlayerLock(false) }
        coVerify(exactly = 1) { securityStore.setAutoLockTimerMs(60_000) }
        coVerify(exactly = 1) { securityStore.setRemoteControlEnabled(true) }
    }

    @Test
    fun `verifyPin delegates to the editor`() = runTest {
        coEvery { editor.verifyPin("9999") } returns false
        coEvery { editor.verifyPin("1234") } returns true
        val viewModel = viewModel()

        assertTrue(viewModel.verifyPin("1234"))
        assertFalse(viewModel.verifyPin("9999"))
    }

    @Test
    fun `authorizeQuickConnect reports success without an error`() = runTest {
        coEvery { authRepository.authorizeQuickConnect("ABC-DEF") } returns Result.success(true)
        val viewModel = viewModel()

        var success: Boolean? = null
        var error: String? = "sentinel"
        viewModel.authorizeQuickConnect("ABC-DEF") { ok, e ->
            success = ok
            error = e
        }
        advanceUntilIdle()

        assertEquals(true, success)
        assertNull(error)
    }

    @Test
    fun `authorizeQuickConnect reports the server rejection and failures verbatim`() = runTest {
        coEvery { authRepository.authorizeQuickConnect("USED") } returns Result.success(false)
        coEvery { authRepository.authorizeQuickConnect("DOWN") } returns
            Result.failure(RuntimeException("connection refused"))
        val viewModel = viewModel()

        var rejected: Pair<Boolean, String?>? = null
        var failed: Pair<Boolean, String?>? = null
        viewModel.authorizeQuickConnect("USED") { ok, e -> rejected = ok to e }
        viewModel.authorizeQuickConnect("DOWN") { ok, e -> failed = ok to e }
        advanceUntilIdle()

        assertEquals(false to "Code not found or already used", rejected)
        assertEquals(false to "connection refused", failed)
        coVerify(exactly = 1) { authRepository.authorizeQuickConnect("USED") }
    }
}
