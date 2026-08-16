package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.SystemInfo
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminRepositoryImplTest {

    private val apiClient: JellyfinApiClient = mockk(relaxed = true)
    private val repository = AdminRepositoryImpl(apiClient)

    @Test
    fun `getSystemInfo passes success through`() = runTest {
        val info = SystemInfo(serverName = "Jelly", version = "10.9.11")
        coEvery { apiClient.getSystemInfo() } returns Result.success(info)

        val result = repository.getSystemInfo()

        assertTrue(result.isSuccess)
        assertEquals(info, result.getOrNull())
        coVerify(exactly = 1) { apiClient.getSystemInfo() }
    }

    @Test
    fun `getSystemInfo passes failure through`() = runTest {
        val error = Exception("server unreachable")
        coEvery { apiClient.getSystemInfo() } returns Result.failure(error)

        val result = repository.getSystemInfo()

        assertTrue(result.isFailure)
        assertSame(error, result.exceptionOrNull())
    }
}
