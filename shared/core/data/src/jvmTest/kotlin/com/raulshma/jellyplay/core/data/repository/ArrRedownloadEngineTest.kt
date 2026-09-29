package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.arr.ArrCommandName
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.model.arr.ArrRedownloadResult
import com.raulshma.jellyplay.core.model.arr.ArrRedownloadStep
import com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepResult
import com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepStatus
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the [ArrRedownloadEngine] step-ladder (extracted verbatim from
 * [ArrRepositoryImpl]) against a hand-written fake [ArrServiceClient] — the
 * LADDER rows only (step order, gates, generic message wording, Sonarr's
 * FAILED-verify hard gate). The ADAPTER-owned rows — lookup abort wording,
 * the verify re-query semantics incl. Sonarr's inconclusive WARNING branch —
 * keep exercising the real [RadarrServiceClient]/[SonarrServiceClient]
 * through [ArrRedownloadLadderTest] so no coverage is lost.
 */
class ArrRedownloadEngineTest {

    /** Scriptable ladder-only fake of [ArrServiceClient]; every unused op fails loudly. */
    private class FakeArrServiceClient(
        override val serviceName: String,
        var lookupResult: ArrRedownloadLookup,
        var deleteOk: Boolean = true,
        var verifyResult: ArrRedownloadStepResult = step(ArrRedownloadStep.VERIFY_DELETED, ArrRedownloadStepStatus.SUCCESS, null),
        var monitorOk: Boolean = true,
        var searchOk: Boolean = true,
    ) : ArrServiceClient {
        val calls = mutableListOf<String>()
        var deletedFileId = 0
        var monitoredId = 0
        var searchedId = 0

        private fun fail(op: String): Nothing = throw AssertionError("unexpected $op call")

        override suspend fun getQueue() = fail("getQueue")
        override suspend fun deleteQueueItem(id: Int, options: ArrQueueDeleteOptions) = fail("deleteQueueItem")
        override suspend fun deleteQueueItems(ids: List<Int>, options: ArrQueueDeleteOptions) = fail("deleteQueueItems")
        override suspend fun grabQueueItem(id: Int) = fail("grabQueueItem")
        override suspend fun importQueueItem(downloadId: String) = fail("importQueueItem")
        override suspend fun getCalendar(start: String, end: String) = fail("getCalendar")
        override suspend fun getBlocklist() = fail("getBlocklist")
        override suspend fun deleteBlocklistItem(id: Int) = fail("deleteBlocklistItem")
        override suspend fun deleteBlocklistItems(ids: List<Int>) = fail("deleteBlocklistItems")
        override suspend fun postCommand(
            commandName: ArrCommandName,
            movieIds: List<Int>?,
            episodeIds: List<Int>?,
            seriesId: Int?,
            seasonNumber: Int?,
        ) = fail("postCommand")
        override suspend fun testConnection() = fail("testConnection")

        override suspend fun lookup(ref: ArrRedownloadRef): ArrRedownloadLookup {
            calls += "lookup"
            return lookupResult
        }

        override suspend fun deleteFile(fileId: Int): Boolean {
            calls += "deleteFile:$fileId"
            deletedFileId = fileId
            return deleteOk
        }

        override suspend fun verifyDeleted(item: ArrRedownloadItem): ArrRedownloadStepResult {
            calls += "verifyDeleted"
            return verifyResult
        }

        override suspend fun monitor(id: Int): Boolean {
            calls += "monitor:$id"
            monitoredId = id
            return monitorOk
        }

        override suspend fun search(id: Int): Boolean {
            calls += "search:$id"
            searchedId = id
            return searchOk
        }
    }

    private fun trackedItem(
        fileId: Int = 42,
        monitored: Boolean = false,
    ) = ArrRedownloadItem(id = 7, fileId = fileId, monitored = monitored, tmdbId = 555)

    private fun engine(client: ArrServiceClient, kind: ArrServiceKind = ArrServiceKind.RADARR) =
        ArrRedownloadEngine(client, kind, ArrRedownloadRef(tmdbId = 555))

    @Test
    fun `lookup abort lands the failure at the DELETE_FILE gate and stops`() = runTest {
        val client = FakeArrServiceClient("Radarr", ArrRedownloadLookup.Aborted("Movie (tmdb 555) not tracked in Radarr."))

        val result = engine(client).run()

        assertEquals(listOf(step(ArrRedownloadStep.DELETE_FILE, ArrRedownloadStepStatus.FAILED, "Movie (tmdb 555) not tracked in Radarr.")), result.steps)
        assertEquals(false, result.isComplete)
        assertEquals(listOf("lookup"), client.calls, "an abort never touches the later steps")
    }

    @Test
    fun `no file skips the delete and continues through the ladder`() = runTest {
        val client = FakeArrServiceClient("Radarr", ArrRedownloadLookup.Found(trackedItem(fileId = 0, monitored = false)))

        val result = engine(client).run()

        assertEquals(
            listOf(
                step(ArrRedownloadStep.DELETE_FILE, ArrRedownloadStepStatus.SKIPPED, "No file to delete."),
                step(ArrRedownloadStep.VERIFY_DELETED, ArrRedownloadStepStatus.SUCCESS, null),
                step(ArrRedownloadStep.MONITOR, ArrRedownloadStepStatus.SUCCESS, null),
                step(ArrRedownloadStep.SEARCH, ArrRedownloadStepStatus.SUCCESS, "Radarr is searching for a new download."),
            ),
            result.steps,
        )
        assertEquals(true, result.isComplete)
        assertEquals(0, client.deletedFileId, "no file id is ever sent to the delete op")
        assertEquals(7, client.searchedId)
    }

    @Test
    fun `rejected delete is a hard stop with the service-named failure`() = runTest {
        val client = FakeArrServiceClient(
            "Radarr",
            ArrRedownloadLookup.Found(trackedItem()),
            deleteOk = false,
        )

        val result = engine(client).run()

        assertEquals(
            listOf(step(ArrRedownloadStep.DELETE_FILE, ArrRedownloadStepStatus.FAILED, "Radarr rejected the file delete.")),
            result.steps,
        )
        assertEquals(false, result.isComplete)
        assertEquals(listOf("lookup", "deleteFile:42"), client.calls)
    }

    @Test
    fun `failed verify is a hard gate on Sonarr only`() = runTest {
        val failedVerify = step(ArrRedownloadStep.VERIFY_DELETED, ArrRedownloadStepStatus.FAILED, "Sonarr still reports the file.")
        val sonarr = FakeArrServiceClient("Sonarr", ArrRedownloadLookup.Found(trackedItem()), verifyResult = failedVerify)

        val sonarrResult = engine(sonarr, ArrServiceKind.SONARR).run()

        assertEquals(
            listOf(
                step(ArrRedownloadStep.DELETE_FILE, ArrRedownloadStepStatus.SUCCESS, null),
                failedVerify,
            ),
            sonarrResult.steps,
        )
        assertEquals(false, sonarrResult.isComplete)
        assertEquals(listOf("lookup", "deleteFile:42", "verifyDeleted"), sonarr.calls, "no monitor/search after the Sonarr hard gate")

        val radarr = FakeArrServiceClient("Radarr", ArrRedownloadLookup.Found(trackedItem()), verifyResult = failedVerify)
        val radarrResult = engine(radarr, ArrServiceKind.RADARR).run()

        assertEquals(true, radarrResult.isComplete, "Radarr's verify failure is best-effort; the flow continues")
        assertEquals(ArrRedownloadStepStatus.FAILED, radarrResult.steps[1].status)
        assertEquals(7, radarr.searchedId)
    }

    @Test
    fun `already monitored skips the monitor step`() = runTest {
        val client = FakeArrServiceClient("Radarr", ArrRedownloadLookup.Found(trackedItem(monitored = true)))

        val result = engine(client).run()

        assertEquals(
            step(ArrRedownloadStep.MONITOR, ArrRedownloadStepStatus.SKIPPED, "Already monitored."),
            result.steps[2],
        )
        assertEquals(true, result.isComplete)
        assertEquals(listOf("lookup", "deleteFile:42", "verifyDeleted", "search:7"), client.calls)
    }

    @Test
    fun `failed monitor or search marks the step FAILED and still completes`() = runTest {
        val monitorFail = FakeArrServiceClient("Radarr", ArrRedownloadLookup.Found(trackedItem()), monitorOk = false)
        val monitorResult = engine(monitorFail).run()
        assertEquals(step(ArrRedownloadStep.MONITOR, ArrRedownloadStepStatus.FAILED, "Failed to re-monitor."), monitorResult.steps[2])
        assertEquals(true, monitorResult.isComplete, "a failed monitor does not stop the search")

        val searchFail = FakeArrServiceClient("Radarr", ArrRedownloadLookup.Found(trackedItem()), searchOk = false)
        val searchResult = engine(searchFail).run()
        assertEquals(step(ArrRedownloadStep.SEARCH, ArrRedownloadStepStatus.FAILED, "Search command failed."), searchResult.steps[3])
        assertEquals(true, searchResult.isComplete)
    }
}

private fun step(
    step: ArrRedownloadStep,
    status: ArrRedownloadStepStatus,
    message: String?,
) = ArrRedownloadStepResult(step, status, message)
