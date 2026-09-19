package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.NewsletterData
import com.raulshma.jellyplay.core.network.api.MediaInfoApiClient

/**
 *  MediaRepository facade split: the three NewsletterRepository members
 * moved verbatim from [MediaRepositoryImpl] (one-line forwards there,
 * one-line forwards here — the family owns no cache state, so it takes no
 * [MediaRepositoryInternals]).
 *
 * Ctor narrowed to the ONE API family client that owns the routes
 * ([MediaInfoApiClient] — the PlaybackRepositoryImpl family-seam
 * precedent, not the JellyfinApiClient union): the family single composes
 * the same impl the union delegates to, so the calls hit the same wire and
 * the same per-instance client state (e.g. the newsletter renderer's cached
 * server name).
 */
class NewsletterRepositoryImpl(
    private val apiClient: MediaInfoApiClient,
) : NewsletterRepository {

    override suspend fun getNewsletterData(sinceDate: String, limit: Int): Result<NewsletterData> =
        apiClient.getNewsletterData(sinceDate, limit)

    // NOTE: backend route `POST /newsletter/send` not yet implemented; 404s until added.
    override suspend fun sendNewsletter(): Result<Unit> =
        apiClient.sendNewsletter()

    // NOTE: backend route `POST /newsletter/test` not yet implemented; 404s until added.
    override suspend fun sendTestNewsletter(): Result<Unit> =
        apiClient.sendTestNewsletter()
}
