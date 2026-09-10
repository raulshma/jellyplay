package com.raulshma.jellyplay.startup

import com.raulshma.jellyplay.core.data.repository.LyricsRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Pins the cache-maintenance startup step: [CacheMaintenanceInitializer.cleanup]
 * always runs both maintenance passes, while [CacheMaintenanceInitializer.cleanupOnce]
 * is a once-per-process gate — safe to invoke from every auth-success emission
 * because the second and later calls are no-ops.
 */
class CacheMaintenanceInitializerTest {

    private val lyricsRepository: LyricsRepository = mockk(relaxed = true)
    private val offlineRepository: OfflineRepository = mockk(relaxed = true)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun setUp() {
        coEvery { lyricsRepository.cleanupLyricsCache() } returns Unit
        coEvery { offlineRepository.cleanupOrphans() } returns Unit
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun createInitializer() = CacheMaintenanceInitializer(
        lyricsRepository = lyricsRepository,
        offlineRepository = offlineRepository,
        applicationScope = scope,
    )

    @Test
    fun `cleanup runs both maintenance passes`() = runBlocking {
        createInitializer().cleanup()

        coVerify(exactly = 1) { lyricsRepository.cleanupLyricsCache() }
        coVerify(exactly = 1) { offlineRepository.cleanupOrphans() }
    }

    @Test
    fun `cleanupOnce runs the passes exactly once across repeated calls`() = runBlocking {
        val lyricsRan = CompletableDeferred<Unit>()
        val orphansRan = CompletableDeferred<Unit>()
        coEvery { lyricsRepository.cleanupLyricsCache() } coAnswers { lyricsRan.complete(Unit) }
        coEvery { offlineRepository.cleanupOrphans() } coAnswers { orphansRan.complete(Unit) }

        val initializer = createInitializer()
        initializer.cleanupOnce()
        initializer.cleanupOnce()
        initializer.cleanupOnce()

        // The passes run on the real application scope (Dispatchers.IO inside
        // cleanupOnce), so synchronize on the deferreds instead of assuming
        // virtual time.
        withTimeout(10_000) {
            lyricsRan.await()
            orphansRan.await()
        }

        coVerify(exactly = 1) { lyricsRepository.cleanupLyricsCache() }
        coVerify(exactly = 1) { offlineRepository.cleanupOrphans() }
    }
}
