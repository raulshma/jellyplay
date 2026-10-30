package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.book.BookTocProbe
import com.raulshma.jellyplay.core.data.book.BookTocProber
import com.raulshma.jellyplay.core.data.repository.BookTocCacheRepository
import com.raulshma.jellyplay.core.data.repository.NoopBookTocCacheRepository
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationColor
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationStyle
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationsRepository
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotation
import com.raulshma.jellyplay.core.data.repository.ReaderBookmark
import com.raulshma.jellyplay.core.data.session.JellyPlayFeatureGate
import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.data.session.isAvailableNowOrProbe
import com.raulshma.jellyplay.core.model.JellyPlayEpisodeMarker
import com.raulshma.jellyplay.core.model.JellyPlayEpisodeScore
import com.raulshma.jellyplay.core.model.JellyPlayPluginOmdbAwards
import com.raulshma.jellyplay.core.model.JellyPlayPluginRating
import com.raulshma.jellyplay.core.model.BookFormat
import com.raulshma.jellyplay.core.network.api.JellyPlayMarkersRoutes
import com.raulshma.jellyplay.core.network.api.JellyPlayRatingsRoutes
import com.raulshma.jellyplay.core.network.api.JellyPlayRecommendationsRoutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The ADR 0010 companion-plugin seams ([JellyPlayPluginStatusStore], the
 * three narrow route roles, the ONE [JellyPlayFeatureGate] gate) as one
 * bundle for [DetailViewModel] — the [DetailActionFactories] pure-DI
 * aggregation shape. Each read is a plain operation on this bundle and the
 * null-arm is decided ONCE, per operation, yielding the plugin contract's
 * silent absence (the section/rating/badge is absent, never an error).
 *
 * The members stay nullable-typed on purpose: two of the five (the status
 * store and the gate) are core:data final classes with heavyweight
 * constructor closures (capabilities client, session registry, DataStore),
 * so no faithful no-op implementation can stand in for them — the unwired
 * case stays `null` and every operation folds it in one place. The gate
 * stays ONE member ([featureGate]) per ADR 0010 §6: the capabilities
 * registry is the only gating mechanism, and the VM's single
 * `pluginFeature` path reads it through [isAvailableNowOrProbe]'s
 * nullable-seam ladder.
 *
 * Unwired default keeps the direct-construction test harnesses compiling —
 * the Settings screen's `jellyPlayStatusStore` precedent; the Koin factory
 * passes the real singles ([DetailPlatform.android] /
 * [DetailPlatform.jvm]).
 */
internal class DetailPluginArms(
    val statusStore: JellyPlayPluginStatusStore? = null,
    val featureGate: JellyPlayFeatureGate? = null,
    val ratingsApi: JellyPlayRatingsRoutes? = null,
    val recommendationsApi: JellyPlayRecommendationsRoutes? = null,
    val markersApi: JellyPlayMarkersRoutes? = null,
) {

    /**
     * The capabilities' server-similar-pipeline flag
     * ([com.raulshma.jellyplay.core.network.api.JellyPlayCapabilities.serverSimilarPipeline]).
     * Unwired store → false (the stock local similar-items row is not
     * suppressed).
     */
    fun serverSimilarPipeline(): Boolean =
        statusStore?.capabilities?.value?.serverSimilarPipeline == true

    /**
     * The mdblist ratings role. Unwired → null. Null, a failed fetch and a
     * null payload all yield the same silent absence.
     */
    suspend fun mdbListRatings(imdbId: String): Result<List<JellyPlayPluginRating>?>? =
        ratingsApi?.getMdbListRatings(imdbId)

    /** The OMDb awards role, payload-unwrapped. Unwired → no awards line. */
    suspend fun omdbAwards(imdbId: String): JellyPlayPluginOmdbAwards? =
        ratingsApi?.getOmdbAwards(imdbId)?.getOrNull()

    /** The per-season TMDB scores role, payload-unwrapped. Unwired → no scores. */
    suspend fun tmdbSeasonRatings(
        tmdbId: String,
        seasonNumber: Int,
    ): Map<Int, JellyPlayEpisodeScore>? =
        ratingsApi?.getTmdbSeasonRatings(tmdbId, seasonNumber)?.getOrNull()

    /**
     * The server-scored similar-ids role. Unwired → empty (the stock local
     * similar-items row renders).
     */
    suspend fun pluginSimilarIds(itemId: String, limit: Int): List<String> =
        recommendationsApi?.getJellyPlaySimilarItems(itemId, limit)?.getOrNull().orEmpty()

    /** The anime filler/recap markers role. Unwired → no badges. */
    suspend fun animeMarkers(
        seriesId: String,
        providerSeriesId: String,
    ): List<JellyPlayEpisodeMarker>? =
        markersApi?.getAnimeMarkers(seriesId, providerSeriesId)?.getOrNull()
}

/**
 * The book-only seams (BOOK media type) as one bundle for [DetailViewModel]:
 * the reader's write-through TOC cache (`book_toc_cache`), the marks
 * repository supplying the live bookmark/highlight counts, and the prober
 * that parses a never-opened book's LOCAL file when no cache row exists yet.
 *
 * The constructor takes the seams the way the platforms register them —
 * optional (a platform without a player-book prober binding passes null) —
 * and normalizes each absent seam onto its no-op adapter ONCE, so every
 * read in the VM is a plain call and the silent absence (no cached TOC, no
 * probe result, counts never written) is decided here, not per call site.
 * The all-default construction is the direct-construction test default.
 */
internal class DetailBookArms(
    tocCacheRepository: BookTocCacheRepository? = null,
    readerAnnotationsRepository: ReaderAnnotationsRepository? = null,
    tocProber: BookTocProber? = null,
) {
    val tocCacheRepository: BookTocCacheRepository = tocCacheRepository ?: NoopBookTocCacheRepository()
    val readerAnnotationsRepository: ReaderAnnotationsRepository =
        readerAnnotationsRepository ?: NoopReaderAnnotationsRepository
    val tocProber: BookTocProber = tocProber ?: NoopBookTocProber
}

/**
 * Null-object [BookTocProber]: `probe` answers null — the caller hides the
 * "Contents" section instead of surfacing an error for a purely speculative
 * read, keeping an unwired prober (`null`) on the same silent path the
 * bundle normalizes.
 */
internal object NoopBookTocProber : BookTocProber {
    override suspend fun probe(filePath: String?, format: BookFormat): BookTocProbe? = null
}

/**
 * Null-object [ReaderAnnotationsRepository]: the observe flows never emit,
 * so the detail screen's live marks-count collector parks without ever
 * writing — the counts stay untouched; no state write, not even a
 * zero-restoring one. The write and export operations no-op: nothing in the
 * detail screen reaches them, and an unwired surface must never pretend to
 * persist.
 */
internal object NoopReaderAnnotationsRepository : ReaderAnnotationsRepository {

    override fun observeBookmarks(itemId: String): Flow<List<ReaderBookmark>> = emptyFlow()

    override fun observeAnnotations(itemId: String): Flow<List<ReaderAnnotation>> = emptyFlow()

    override suspend fun addBookmark(itemId: String, positionTicks: Long, cfi: String?, chapterLabel: String) = Unit

    override suspend fun removeBookmark(id: Long) = Unit

    override suspend fun addAnnotation(
        itemId: String,
        cfi: String,
        style: ReaderAnnotationStyle,
        color: ReaderAnnotationColor,
        anchorText: String,
        note: String?,
        chapterLabel: String,
    ) = Unit

    override suspend fun updateAnnotation(
        id: Long,
        note: String?,
        color: ReaderAnnotationColor?,
        style: ReaderAnnotationStyle?,
    ) = Unit

    override suspend fun deleteAnnotation(id: Long) = Unit

    override suspend fun deleteAllForItem(itemId: String) = Unit

    override fun exportMarkdown(
        itemId: String?,
        title: String,
        bookmarks: List<ReaderBookmark>,
        annotations: List<ReaderAnnotation>,
    ): String = ""

    override fun exportJson(
        itemId: String?,
        title: String,
        bookmarks: List<ReaderBookmark>,
        annotations: List<ReaderAnnotation>,
    ): String = ""
}
