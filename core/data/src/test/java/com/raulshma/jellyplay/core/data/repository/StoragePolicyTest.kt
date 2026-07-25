package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.UserPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StoragePolicy.enforce] is the single owner of the download storage-cap
 * rule. Previously this logic was duplicated in `startDownload` and
 * `downloadSeries` and could drift; this test pins the one source of truth.
 */
class StoragePolicyTest {

    private val preferencesStore: UserPreferencesStore = mockk()

    private fun policy(returnedBytes: Long): StoragePolicy = StoragePolicy(
        preferencesStore = preferencesStore,
        currentBytesProvider = { returnedBytes },
    )

    private fun prefs(
        maxCacheSizeMb: Int = 0,
        maxDownloadStorageGb: Int = 0,
    ) {
        every { preferencesStore.preferences } returns MutableStateFlow(
            UserPreferences(
                maxCacheSizeMb = maxCacheSizeMb,
                maxDownloadStorageGb = maxDownloadStorageGb,
            ),
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
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                policy(returnedBytes = 500L * 1024 * 1024).enforce()
            }
        }
    }

    @Test
    fun `at GB cap exactly - throws`() = runTest {
        prefs(maxDownloadStorageGb = 5)
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                policy(returnedBytes = 5L * 1024 * 1024 * 1024).enforce()
            }
        }
    }

    @Test
    fun `over GB cap - throws`() = runTest {
        prefs(maxDownloadStorageGb = 5)
        assertThrows(IllegalStateException::class.java) {
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
            preferencesStore = preferencesStore,
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
            preferencesStore = preferencesStore,
            currentBytesProvider = { providerInvoked[0] = true; 10L * 1024 * 1024 },
        )
        val result = p.enforce()
        assertTrue("currentBytesProvider must run when no precomputed value is given", providerInvoked[0])
        assertEquals(10L * 1024 * 1024, result)
    }
}

