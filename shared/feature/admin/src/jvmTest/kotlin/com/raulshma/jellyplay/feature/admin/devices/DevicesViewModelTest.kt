package com.raulshma.jellyplay.feature.admin.devices

import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.model.DeviceInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the devices admin flow (`DevicesViewModel`):
 *
 *  - load populates the device list and routes failures into state.error;
 *  - refresh failures are silent: the stale list stays, no error is set;
 *  - delete requires a selected device (no selection → no repository call)
 *    and closes the dialog + reloads on success;
 *  - rename pre-fills the custom name, persists blank as null (clearing a
 *    server-side name), closes the dialog and reloads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DevicesViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music/livetv conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var adminRepository: AdminRepository

    private val phone = DeviceInfo(id = "d-1", name = "Pixel", customName = "My Phone")
    private val tv = DeviceInfo(id = "d-2", name = "Living Room TV")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        adminRepository = mockk(relaxed = true)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.loadedViewModel(devices: List<DeviceInfo> = listOf(phone, tv)): DevicesViewModel {
        coEvery { adminRepository.getDevices() } returns Result.success(devices)
        return DevicesViewModel(adminRepository).also { advanceUntilIdle() }
    }

    // ── load / refresh / error ──

    @Test
    fun `load populates the device list`() = runTest(mainDispatcher) {
        val viewModel = loadedViewModel()

        assertFalse(viewModel.state.isLoading)
        assertEquals(listOf("Pixel", "Living Room TV"), viewModel.state.devices.map { it.name })
        assertNull(viewModel.state.error)
    }

    @Test
    fun `load failure surfaces the error`() = runTest(mainDispatcher) {
        coEvery { adminRepository.getDevices() } returns Result.failure(RuntimeException("offline"))

        val viewModel = DevicesViewModel(adminRepository)
        advanceUntilIdle()

        assertEquals("offline", viewModel.state.error)
        assertFalse(viewModel.state.isLoading)
        assertTrue(viewModel.state.devices.isEmpty())
    }

    @Test
    fun `refresh updates the list`() = runTest(mainDispatcher) {
        val viewModel = loadedViewModel()
        coEvery { adminRepository.getDevices() } returns
            Result.success(listOf(phone, tv, DeviceInfo(id = "d-3", name = "Tablet")))

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.state.isRefreshing)
        assertEquals(listOf("Pixel", "Living Room TV", "Tablet"), viewModel.state.devices.map { it.name })
    }

    @Test
    fun `refresh failure keeps the stale list silently`() = runTest(mainDispatcher) {
        val viewModel = loadedViewModel()
        coEvery { adminRepository.getDevices() } returns Result.failure(RuntimeException("offline"))

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.state.isRefreshing)
        assertEquals(2, viewModel.state.devices.size)
        assertNull(viewModel.state.error)
    }

    // ── delete flow ──

    @Test
    fun `delete routes to the repository closes the dialog and reloads`() = runTest(mainDispatcher) {
        val viewModel = loadedViewModel()
        coEvery { adminRepository.deleteDevice("d-1") } returns Result.success(Unit)

        viewModel.showDeleteDialog(phone)
        assertEquals(phone, viewModel.state.selectedDevice)
        assertTrue(viewModel.state.showDeleteDialog)

        viewModel.deleteDevice()
        advanceUntilIdle()

        coVerify(exactly = 1) { adminRepository.deleteDevice("d-1") }
        assertFalse(viewModel.state.showDeleteDialog)
        assertNull(viewModel.state.selectedDevice)
        // deleteDevice ends with a reload.
        coVerify(atLeast = 2) { adminRepository.getDevices() }
    }

    @Test
    fun `delete without a selection is a no-op`() = runTest(mainDispatcher) {
        val viewModel = loadedViewModel()

        viewModel.deleteDevice()
        advanceUntilIdle()

        coVerify(exactly = 0) { adminRepository.deleteDevice(any()) }
    }

    @Test
    fun `dismissDeleteDialog clears the selection`() = runTest(mainDispatcher) {
        val viewModel = loadedViewModel()
        viewModel.showDeleteDialog(phone)

        viewModel.dismissDeleteDialog()

        assertFalse(viewModel.state.showDeleteDialog)
        assertNull(viewModel.state.selectedDevice)
    }

    // ── rename flow ──

    @Test
    fun `rename prefills the custom name and persists edits`() = runTest(mainDispatcher) {
        val viewModel = loadedViewModel()
        coEvery { adminRepository.renameDevice(any(), any()) } returns Result.success(Unit)

        viewModel.showEditNameDialog(phone)
        assertTrue(viewModel.state.showEditNameDialog)
        assertEquals("d-1", viewModel.state.editDeviceId)
        assertEquals("My Phone", viewModel.state.editCustomName)

        viewModel.updateEditCustomName("Renamed")
        viewModel.saveDeviceName()
        advanceUntilIdle()

        coVerify(exactly = 1) { adminRepository.renameDevice("d-1", "Renamed") }
        assertFalse(viewModel.state.showEditNameDialog)
        coVerify(atLeast = 2) { adminRepository.getDevices() }
    }

    @Test
    fun `blank custom name persists as null to clear the server-side name`() = runTest(mainDispatcher) {
        val viewModel = loadedViewModel()
        coEvery { adminRepository.renameDevice(any(), any()) } returns Result.success(Unit)

        // tv has no custom name → pre-filled blank.
        viewModel.showEditNameDialog(tv)
        assertEquals("", viewModel.state.editCustomName)

        viewModel.saveDeviceName()
        advanceUntilIdle()

        coVerify(exactly = 1) { adminRepository.renameDevice("d-2", null) }
    }

    @Test
    fun `dismissEditNameDialog closes without saving`() = runTest(mainDispatcher) {
        val viewModel = loadedViewModel()
        viewModel.showEditNameDialog(phone)

        viewModel.dismissEditNameDialog()

        assertFalse(viewModel.state.showEditNameDialog)
        coVerify(exactly = 0) { adminRepository.renameDevice(any(), any()) }
    }
}
