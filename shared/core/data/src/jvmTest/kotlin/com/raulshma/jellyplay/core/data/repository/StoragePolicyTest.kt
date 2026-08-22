package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * [StoragePolicy.enforce] is the single owner of the download storage-cap
 * rule. Previously this logic was duplicated in `startDownload` and
 * `downloadSeries` and could drift; this test pins the one source of truth.
 */
class StoragePolicyTest {

    private val networkOfflineStore: NetworkOfflineStore = mockk()
    private val downloadsStore: DownloadsStore = mockk()

    private fun policy(returnedBytes: Long): StoragePolicy = StoragePolicy(
        networkOfflineStore = networkOfflineStore,
        downloadsStore = downloadsStore,
        currentBytesProvider = { returnedBytes },
    )

    private fun prefs(
        maxCacheSizeMb: Int = 0,
        maxDownloadStorageGb: Int = 0,
    ) {
        every { networkOfflineStore.networkOffline } returns MutableStateFlow(
            NetworkOfflineSlice(maxCacheSizeMb = maxCacheSizeMb),
        )
        every { downloadsStore.downloads } returns MutableStateFlow(
            DownloadsSlice(maxDownloadStorageGb = maxDownloadStorageGb),
        )
    }

    @Test
    fun `no caps configured - returns without throwing`() = runTest {
        prefs(maxCacheSizeMb = 0, maxDownloadStorageGb = 0)
        // currentBytesProvider should not even be consulted.
        val result = policy(returnedBytes = 999L).enforce()
        assertEquals(-1L, result)
    }

    @Test
    fun `under MB cap - passes and returns current bytes`() = runTest {
        prefs(maxCacheSizeMb = 500) // 500 MB
        val result = policy(returnedBytes = 100L * 1024 * 1024).enforce() // 100 MB used
        assertEquals(100L * 1024 * 1024, result)
    }

    @Test
    fun `at MB cap - throws`() = runTest {
        prefs(maxCacheSizeMb = 500)
        assertFailsWith<IllegalStateException> {
            kotlinx.coroutines.runBlocking {
                policy(returnedBytes = 500L * 1024 * 1024).enforce()
            }
        }
    }

    @Test
    fun `at GB cap exactly - throws`() = runTest {
        prefs(maxDownloadStorageGb = 5)
        assertFailsWith<IllegalStateException> {
            kotlinx.coroutines.runBlocking {
                policy(returnedBytes = 5L * 1024 * 1024 * 1024).enforce()
            }
        }
    }

    @Test
    fun `over GB cap - throws`() = runTest {
        prefs(maxDownloadStorageGb = 5)
        assertFailsWith<IllegalStateException> {
            kotlinx.coroutines.runBlocking {
                policy(returnedBytes = 6L * 1024 * 1024 * 1024).enforce()
            }
        }
    }

    @Test
    fun `precomputed bytes bypass provider`() = runTest {
        prefs(maxCacheSizeMb = 500)
        val providerInvoked = booleanArrayOf(false)
        val p = StoragePolicy(
            networkOfflineStore = networkOfflineStore,
            downloadsStore = downloadsStore,
            currentBytesProvider = { providerInvoked[0] = true; 0L },
        )
        p.enforce(precomputedCurrentBytes = 10L * 1024 * 1024)
        assertEquals(false, providerInvoked[0])
    }

    @Test
    fun `without precomputed bytes - provider is consulted`() = runTest {
        prefs(maxCacheSizeMb = 500)
        val providerInvoked = booleanArrayOf(false)
        val p = StoragePolicy(
            networkOfflineStore = networkOfflineStore,
            downloadsStore = downloadsStore,
            currentBytesProvider = { providerInvoked[0] = true; 10L * 1024 * 1024 },
        )
        val result = p.enforce()
        assertTrue(providerInvoked[0], "currentBytesProvider must run when no precomputed value is given")
        assertEquals(10L * 1024 * 1024, result)
    }
}
