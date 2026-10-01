package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.download.SeriesEpisodeDownloads
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.ui.message.UserMessageBus
import kotlinx.coroutines.CoroutineScope

/**
 * The two series sheets' construction seam — [SeriesDeleteStateHolder] plus
 * [SeriesDownloadStateHolder] — mirroring [HomeRefresherFactory] /
 * [HomeSyncStatusFactory]: the holders' pure-DI collaborators are owned HERE
 * so a new series-sheet dependency widens this factory and the Koin single,
 * never [HomeViewModel]'s constructor (the test harness constructs the VM,
 * not Koin — every widened VM parameter used to drag both suites with it).
 *
 * Deliberate double-ownership, same as the refresher factory's
 * `mediaRepository`: [episodeCatalogue], [downloadIntake], [userMessageBus]
 * and [offlineRepository] are also consumed by the VM's own body
 * (resolveSeriesPlay / downloadItem / discover-reroll messages / the offline
 * gate), so they stay on the VM constructor too — only
 * [seriesDownloads][SeriesEpisodeDownloads] is holder-exclusive and left this
 * constructor. What the factory buys is the single landing place for
 * sheet-shaped collaborators, not a narrower VM interface for shared beans.
 *
 * This adds no behavioural seam: [SeriesDownloadStateHolderTest] and
 * [SeriesDeleteStateHolderTest] keep constructing the holders directly, and
 * [create] passes the same values the VM passed before.
 */
internal class HomeSheetsFactory(
    private val episodeCatalogue: EpisodeCatalogue,
    private val seriesDownloads: SeriesEpisodeDownloads,
    private val downloadIntake: DownloadIntake,
    private val userMessageBus: UserMessageBus,
    private val offlineRepository: OfflineRepository,
) {
    /**
     * Builds both holders on the VM's [scope] — sheet loads and deletes must
     * die with the VM (each holder's own contract).
     */
    fun create(scope: CoroutineScope): HomeSheets = HomeSheets(
        seriesDelete = SeriesDeleteStateHolder(scope, offlineRepository),
        seriesDownload = SeriesDownloadStateHolder(
            scope = scope,
            episodeCatalogue = episodeCatalogue,
            seriesDownloads = seriesDownloads,
            downloadIntake = downloadIntake,
            userMessageBus = userMessageBus,
        ),
    )
}

/**
 * The pair of series-sheet holders as one value: the VM folds each holder's
 * `state` into its [HomeUiState] slice and routes the sheet events to the
 * holder methods exactly as before — only the construction moved.
 */
internal class HomeSheets(
    val seriesDelete: SeriesDeleteStateHolder,
    val seriesDownload: SeriesDownloadStateHolder,
)
