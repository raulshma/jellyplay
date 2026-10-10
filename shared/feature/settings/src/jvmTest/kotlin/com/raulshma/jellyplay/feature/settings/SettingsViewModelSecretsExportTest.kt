package com.raulshma.jellyplay.feature.settings

import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.BackupSecrets
import com.raulshma.jellyplay.core.datastore.BackupSecretsCodec
import com.raulshma.jellyplay.core.datastore.BackupSliceKey
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.PreferencesJson
import com.raulshma.jellyplay.core.datastore.SeerrSecrets
import com.raulshma.jellyplay.core.datastore.SettingsBackup
import com.raulshma.jellyplay.core.datastore.ServerEntrySecret
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentity
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import com.raulshma.jellyplay.core.datastore.search.SettingsRecentsStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.SettingsScreenPreferences
import com.raulshma.jellyplay.core.model.UserInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Wave-3 EXPORT half: `exportSettings(uri, includeSecrets, passphrase)`
 * attaches the encrypted envelope + origin ids ONLY when the user opted in,
 * decrypts back to the gathered payload, zeroes the passphrase material, and
 * degrades to the standard "Export failed" surface when the assembler seam is
 * absent. Complements [SettingsViewModelBackupExportTest] (which pins the
 * no-secrets write path).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelSecretsExportTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var settingsBackupIo: SettingsBackupIo
    private lateinit var preferencesStore: UserPreferencesStore
    private lateinit var projections: PreferenceProjections
    private lateinit var authRepository: AuthRepository
    private lateinit var seerrRepository: SeerrRepository
    private lateinit var serverAdminActions: ServerAdminActions
    private lateinit var editor: PreferencesEditor
    private lateinit var recentsStore: SettingsRecentsStore
    private lateinit var assembler: SecretsBackupAssembler
    private lateinit var serverIdentityStore: ServerIdentityStore

    private val identity = MutableStateFlow(ServerIdentity(activeServerId = "srv-1", activeUserId = "user-1"))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        settingsBackupIo = mockk()
        preferencesStore = mockk(relaxed = true)
        projections = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        seerrRepository = mockk(relaxed = true)
        serverAdminActions = mockk<ServerAdminActions>(relaxed = true).apply { every { isSupported } returns true }
        editor = mockk(relaxed = true)
        recentsStore = mockk(relaxed = true)
        assembler = mockk()
        serverIdentityStore = mockk()

        every { projections.settingsScreenPreferences } returns MutableStateFlow(SettingsScreenPreferences())
        every { authRepository.currentServer } returns MutableStateFlow<ServerInfo?>(null)
        every { authRepository.currentUser } returns MutableStateFlow<UserInfo?>(null)
        every { authRepository.currentServerUsers } returns MutableStateFlow<List<UserInfo>>(emptyList())
        every { seerrRepository.pendingRequestCount } returns MutableStateFlow(0)
        every { recentsStore.recents } returns MutableStateFlow(emptyList())
        every { serverIdentityStore.identity } returns identity
        coEvery { preferencesStore.snapshotForBackup() } returns UserPreferencesStore.SettingsBackupSnapshot(
            slices = mapOf(BackupSliceKey.APPEARANCE to JsonPrimitive("stub-slice")),
            extras = AppRuntimeState(),
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Every VM created here; all cancelled in [vmTest]'s finally. */
    private val createdViewModels = mutableListOf<SettingsViewModel>()

    private fun vmTest(block: suspend TestScope.() -> Unit): Unit = runTest(testDispatcher) {
        try {
            block()
        } finally {
            createdViewModels.forEach { it.viewModelScope.cancel() }
            createdViewModels.clear()
        }
    }

    private fun viewModel(
        assemblerOverride: SecretsBackupAssembler? = assembler,
    ): SettingsViewModel = SettingsViewModel(
        settingsBackupIo = settingsBackupIo,
        preferencesStore = preferencesStore,
        projections = projections,
        authRepository = authRepository,
        seerrRepository = seerrRepository,
        serverAdminActions = serverAdminActions,
        editor = editor,
        recentsStore = recentsStore,
        secretsBackupAssembler = assemblerOverride,
        serverIdentityStore = serverIdentityStore,
    ).also { createdViewModels += it }

    private suspend fun TestScope.awaitUntil(description: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            advanceUntilIdle()
            if (condition()) break
            assertTrue(
                System.currentTimeMillis() < deadline,
                "$description (timed out waiting for the VM's coroutine)",
            )
            withContext(Dispatchers.IO) { delay(10) }
        }
        advanceUntilIdle()
    }

    private var writtenPayload: String? = null

    private fun stubWrite(uri: String) {
        writtenPayload = null
        coEvery { settingsBackupIo.writeExportPayload(uri, any()) } answers {
            writtenPayload = arg<String>(1)
            true
        }
    }

    @Test
    fun `export with secrets attaches a decryptable envelope plus origin ids`() = vmTest {
        stubWrite("backup:secrets")
        coEvery { assembler.gather() } returns BackupSecrets(
            servers = listOf(ServerEntrySecret("srv-1", "Home", "https://home.local", userNames = listOf("alice"))),
            seerr = SeerrSecrets(apiKey = "seerr-key"),
        )
        val vm = viewModel()

        vm.exportSettings("backup:secrets", includeSecrets = true, passphrase = "correct horse battery".toCharArray())
        awaitUntil("the export completes") { vm.backupRestoreStatus != null }

        assertEquals("Settings exported successfully", vm.backupRestoreStatus)
        // Raw wire shape: the opted-in export carries ALL THREE Wave-3 keys.
        val raw = writtenPayload.orEmpty()
        for (key in listOf("\"secrets\"", "\"originUserId\"", "\"originServerId\"")) {
            assertTrue(key in raw, "the secrets export must carry the $key key: $raw")
        }
        val decoded = PreferencesJson.import.decodeFromString(SettingsBackup.serializer(), raw)
        assertEquals("user-1", decoded.originUserId, "the exporting session's user id rides the envelope")
        assertEquals("srv-1", decoded.originServerId)
        val secrets = BackupSecretsCodec.decrypt(
            assertNotNull(decoded.secrets, "the opted-in export must carry the envelope"),
            "correct horse battery".toCharArray(),
        )
        assertEquals("seerr-key", secrets.seerr?.apiKey)
        assertEquals(listOf("alice"), secrets.servers.single().userNames)
    }

    @Test
    fun `export without opt-in writes no secrets block and never calls the assembler`() = vmTest {
        stubWrite("backup:plain")
        val vm = viewModel()

        vm.exportSettings("backup:plain")
        awaitUntil("the export completes") { vm.backupRestoreStatus != null }

        val raw = writtenPayload.orEmpty()
        // Raw wire shape: a PLAIN export must not carry any of the three
        // Wave-3 keys — not even as explicit nulls (`@EncodeDefault(Mode.NEVER)`
        // keeps the byte-shape parity with every pre-Wave-3 export).
        for (key in listOf("\"secrets\"", "\"originUserId\"", "\"originServerId\"")) {
            assertFalse(key in raw, "the plain export must not carry the $key key: $raw")
        }
        val decoded = PreferencesJson.import.decodeFromString(SettingsBackup.serializer(), raw)
        assertNull(decoded.secrets, "the plain export must stay byte-shape compatible")
        assertNull(decoded.originUserId)
        assertNull(decoded.originServerId)
        coVerify(exactly = 0) { assembler.gather() }
    }

    @Test
    fun `export with secrets zeroes the passphrase material`() = vmTest {
        stubWrite("backup:zeroed")
        coEvery { assembler.gather() } returns BackupSecrets()
        val vm = viewModel()

        val passphrase = "wipe-me-please".toCharArray()
        vm.exportSettings("backup:zeroed", includeSecrets = true, passphrase = passphrase)
        awaitUntil("the export completes") { vm.backupRestoreStatus != null }

        assertTrue(passphrase.all { it == '\u0000' }, "the passphrase must be zeroed after use")
    }

    @Test
    fun `export with secrets without the assembler seam fails instead of crashing`() = vmTest {
        stubWrite("backup:noseam")
        val vm = viewModel(assemblerOverride = null)

        vm.exportSettings("backup:noseam", includeSecrets = true, passphrase = "pw-12345678".toCharArray())
        awaitUntil("the failure surfaces") { vm.backupRestoreStatus != null }

        assertTrue("Export failed" in vm.backupRestoreStatus.orEmpty(), "got: ${vm.backupRestoreStatus}")
        assertNull(writtenPayload, "nothing may be written when the seam is absent")
    }
}
