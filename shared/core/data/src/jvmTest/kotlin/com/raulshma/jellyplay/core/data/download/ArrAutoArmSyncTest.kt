package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.SeriesDownloadPolicyStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalFeatureGate
import com.raulshma.jellyplay.core.model.AutoDownloadMode
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.core.model.arr.ArrServiceSummary
import com.raulshma.jellyplay.core.network.api.AuthApiClient
import com.raulshma.jellyplay.core.network.api.MediaInfoApiClient
import com.raulshma.jellyplay.core.network.arr.SonarrApiClient
import com.raulshma.jellyplay.core.network.arr.SonarrSeriesInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Pins the [ArrAutoArmSync] stage: the gate ladder (pref → experimental flag
 * → Sonarr servers), the add-only arming join (monitored Sonarr series ×
 * Jellyfin library presence × no existing policy row), the title precedence,
 * and the fail-closed contract (every failure — server resolution, a Sonarr
 * series list, a Jellyfin enumeration page — skips the stage; the calling
 * pass continues). The match index builds straight from the paged
 * enumeration's ProviderIds rows — one paged read, no per-series reads — so
 * the former per-series-degrade arm has no seam to fail anymore.
 * The check-level choreography (stage ordering, same-pass universe join,
 * pass-continues-on-failure) is pinned by AutoDownloadCheckTest.
 */
class ArrAutoArmSyncTest {

    private val featureGate: ExperimentalFeatureGate = mockk()
    private val arrRepository: ArrRepository = mockk()
    private val sonarrApiClient: SonarrApiClient = mockk()
    private val policyStore: SeriesDownloadPolicyStore = mockk()
    private val authApiClient: AuthApiClient = mockk()
    private val mediaInfoApiClient: MediaInfoApiClient = mockk()

    private val sonarr = ArrServerConfig(
        id = "sonarr-1",
        baseUrl = "http://sonarr.local",
        apiKey = "key",
        name = "Sonarr",
        kind = ArrServiceKind.SONARR,
    )

    @BeforeTest
    fun setup() {
        every { featureGate.directArrEnabled } returns MutableStateFlow(true)
        coEvery { arrRepository.resolveServers() } returns
            Result.success(ArrServiceSummary(sonarrServers = listOf(sonarr)))
        coEvery { policyStore.seriesPolicies(any()) } returns emptyMap()
        coEvery { policyStore.armSeries(any(), any()) } returns Unit
        every { authApiClient.currentUser } returns flowOf(user())
        coEvery { seriesEnumeration(0) } returns
            Result.success(1 to listOf(seriesItem("jf-1", "Battlestar", tvdbId = 121361)))
    }

    private fun user() = UserInfo(
        id = "user-1",
        name = "user",
        serverAddress = "http://jellyfin.local",
        accessToken = "token",
    )

    private fun seriesItem(id: String, name: String, tvdbId: Int?) = MediaItem(
        id = id,
        name = name,
        mediaType = MediaType.SERIES,
        providerIds = tvdbId?.let { mapOf("tvdb" to it.toString()) },
    )

    private fun sonarrSeries(
        id: Int,
        title: String,
        tvdbId: Int,
        monitored: Boolean = true,
    ) = SonarrSeriesInfo(id = id, title = title, tvdbId = tvdbId, monitored = monitored)

    private fun sync(prefs: DownloadsSlice = DownloadsSlice(autoArmFromArr = true)) = runTest {
        ArrAutoArmSync(
            featureGate = featureGate,
            arrRepository = arrRepository,
            sonarrApiClient = sonarrApiClient,
            policyStore = policyStore,
            authApiClient = authApiClient,
            mediaInfoApiClient = mediaInfoApiClient,
        ).sync(prefs)
    }

    /**
     * The enumeration-read call spec, spelled once: every stub and verify
     * block in this file pins the same single request shape — the stage's
     * one paged Jellyfin read (series rows, SortName, 200-per-page) — only
     * the page offset and the returned rows differ.
     */
    private suspend fun seriesEnumeration(startIndex: Int) = mediaInfoApiClient.getItemsWithUserData(
        userId = "user-1",
        includeItemTypes = listOf("Series"),
        sortBy = "SortName",
        sortOrder = "Ascending",
        startIndex = startIndex,
        limit = 200,
    )

    // ── Gate ladder ───────────────────────────────────────────────────

    @Test
    fun `pref off no-ops before any resolution`() = runTest {
        sync(prefs = DownloadsSlice(autoArmFromArr = false))

        coVerify(exactly = 0) { arrRepository.resolveServers() }
        coVerify(exactly = 0) { sonarrApiClient.getSeriesList(any()) }
        coVerify(exactly = 0) { policyStore.armSeries(any(), any()) }
    }

    @Test
    fun `directArr disabled no-ops`() = runTest {
        every { featureGate.directArrEnabled } returns MutableStateFlow(false)

        sync()

        coVerify(exactly = 0) { arrRepository.resolveServers() }
        coVerify(exactly = 0) { policyStore.armSeries(any(), any()) }
    }

    @Test
    fun `no Sonarr configured no-ops`() = runTest {
        coEvery { arrRepository.resolveServers() } returns Result.success(ArrServiceSummary())

        sync()

        coVerify(exactly = 0) { sonarrApiClient.getSeriesList(any()) }
        coVerify(exactly = 0) { policyStore.armSeries(any(), any()) }
    }

    @Test
    fun `server resolution failure skips the stage`() = runTest {
        coEvery { arrRepository.resolveServers() } returns
            Result.failure(RuntimeException("discovery down"))

        sync()

        coVerify(exactly = 0) { sonarrApiClient.getSeriesList(any()) }
        coVerify(exactly = 0) { policyStore.armSeries(any(), any()) }
    }

    // ── Arming: the join + name precedence ─────────────────────────────

    @Test
    fun `arms monitored matched series with the Jellyfin title`() = runTest {
        coEvery { sonarrApiClient.getSeriesList(sonarr) } returns Result.success(
            listOf(sonarrSeries(id = 7, title = "BSG (Sonarr)", tvdbId = 121361)),
        )

        sync()

        coVerify(exactly = 1) { policyStore.armSeries("jf-1", "Battlestar") }
        // ONE paged read builds the whole match index — no second page, no
        // per-series reads (the stage has no detail seam to fan out over).
        coVerify(exactly = 1) { seriesEnumeration(0) }
    }

    @Test
    fun `falls back to the Sonarr title when the Jellyfin name is blank`() = runTest {
        coEvery { sonarrApiClient.getSeriesList(sonarr) } returns Result.success(
            listOf(sonarrSeries(id = 7, title = "BSG (Sonarr)", tvdbId = 121361)),
        )
        coEvery { seriesEnumeration(0) } returns
            Result.success(1 to listOf(seriesItem("jf-1", "", tvdbId = 121361)))

        sync()

        coVerify(exactly = 1) { policyStore.armSeries("jf-1", "BSG (Sonarr)") }
    }

    @Test
    fun `a Jellyfin match with no tvdb provider id cannot be armed`() = runTest {
        coEvery { sonarrApiClient.getSeriesList(sonarr) } returns Result.success(
            listOf(sonarrSeries(id = 7, title = "BSG", tvdbId = 121361)),
        )
        coEvery { seriesEnumeration(0) } returns
            Result.success(1 to listOf(seriesItem("jf-1", "Battlestar", tvdbId = null)))

        sync()

        coVerify(exactly = 0) { policyStore.armSeries(any(), any()) }
    }

    @Test
    fun `unmonitored and tvdbId-sentinel Sonarr rows are skipped`() = runTest {
        coEvery { sonarrApiClient.getSeriesList(sonarr) } returns Result.success(
            listOf(
                sonarrSeries(id = 1, title = "Unmonitored", tvdbId = 111, monitored = false),
                sonarrSeries(id = 2, title = "Sentinel", tvdbId = 0),
                sonarrSeries(id = 3, title = "Kept", tvdbId = 121361),
            ),
        )

        sync()

        coVerify(exactly = 1) { policyStore.armSeries("jf-1", "Battlestar") }
    }

    @Test
    fun `first row wins when two enumeration rows share a tvdb id`() = runTest {
        coEvery { sonarrApiClient.getSeriesList(sonarr) } returns Result.success(
            listOf(sonarrSeries(id = 7, title = "BSG", tvdbId = 121361)),
        )
        // Two Jellyfin series claiming the same tvdb id (a duplicate-import
        // library): the enumeration's first row owns the match.
        coEvery { seriesEnumeration(0) } returns Result.success(
            2 to listOf(
                seriesItem("jf-1", "Battlestar", tvdbId = 121361),
                seriesItem("jf-2", "Battlestar (Duplicate)", tvdbId = 121361),
            ),
        )

        sync()

        coVerify(exactly = 1) { policyStore.armSeries("jf-1", "Battlestar") }
        coVerify(exactly = 0) { policyStore.armSeries("jf-2", any()) }
    }

    // ── Add-only: existing rows are never clobbered ────────────────────

    @Test
    fun `an existing NEVER tombstone is not re-armed`() = runTest {
        coEvery { sonarrApiClient.getSeriesList(sonarr) } returns Result.success(
            listOf(sonarrSeries(id = 7, title = "BSG", tvdbId = 121361)),
        )
        // any() matcher: the candidate list's iteration order is the join
        // map's, not a fixture-order guarantee.
        coEvery { policyStore.seriesPolicies(any()) } returns
            mapOf("jf-1" to AutoDownloadMode.NEVER)

        sync()

        coVerify(exactly = 0) { policyStore.armSeries(any(), any()) }
    }

    @Test
    fun `an explicit override and an earlier arm are left untouched — only new rows are written`() = runTest {
        coEvery { sonarrApiClient.getSeriesList(sonarr) } returns Result.success(
            listOf(
                sonarrSeries(id = 7, title = "BSG", tvdbId = 121361),
                sonarrSeries(id = 8, title = "Fresh", tvdbId = 999),
            ),
        )
        coEvery { seriesEnumeration(0) } returns Result.success(
            2 to listOf(
                seriesItem("jf-1", "Battlestar", tvdbId = 121361),
                seriesItem("jf-2", "Fresh Show", tvdbId = 999),
            ),
        )
        coEvery { policyStore.seriesPolicies(any()) } returns
            mapOf("jf-1" to AutoDownloadMode.ASK)

        sync()

        coVerify(exactly = 1) { policyStore.armSeries("jf-2", "Fresh Show") }
        coVerify(exactly = 0) { policyStore.armSeries("jf-1", any()) }
    }

    // ── Fail-closed: every stage failure skips the stage ───────────────

    @Test
    fun `a Sonarr series-list failure skips the whole stage`() = runTest {
        coEvery { sonarrApiClient.getSeriesList(sonarr) } returns
            Result.failure(RuntimeException("sonarr unreachable"))

        sync()

        coVerify(exactly = 0) { policyStore.seriesPolicies(any()) }
        coVerify(exactly = 0) { policyStore.armSeries(any(), any()) }
    }

    @Test
    fun `a Jellyfin enumeration failure propagates to the caller without arming`() = runTest {
        // The stage's fail-closed contract: whole-stage failures SURFACE (the
        // calling check's runCatching folds them into "skip the stage, the
        // rest of the pass continues" — pinned there) — the stage itself
        // never arms on a failed enumeration.
        coEvery { sonarrApiClient.getSeriesList(sonarr) } returns Result.success(
            listOf(sonarrSeries(id = 7, title = "BSG", tvdbId = 121361)),
        )
        coEvery { seriesEnumeration(0) } returns
            Result.failure(RuntimeException("jellyfin unreachable"))

        assertFailsWith<RuntimeException> { sync() }

        coVerify(exactly = 0) { policyStore.armSeries(any(), any()) }
    }

    @Test
    fun `paging walks every page of the series universe`() = runTest {
        val pageOne = (1..200).map { seriesItem("jf-$it", "Series $it", tvdbId = null) }
        val pageTwo = listOf(seriesItem("jf-tail", "Tail", tvdbId = 777))
        coEvery { seriesEnumeration(0) } returns Result.success(201 to pageOne)
        coEvery { seriesEnumeration(200) } returns Result.success(201 to pageTwo)
        coEvery { sonarrApiClient.getSeriesList(sonarr) } returns Result.success(
            listOf(sonarrSeries(id = 7, title = "Tail", tvdbId = 777)),
        )

        sync()

        coVerify(exactly = 1) { policyStore.armSeries("jf-tail", "Tail") }
    }
}
