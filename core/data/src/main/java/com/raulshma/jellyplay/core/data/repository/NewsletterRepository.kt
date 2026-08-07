package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.NewsletterData

interface NewsletterRepository {

    suspend fun getNewsletterData(sinceDate: String, limit: Int = 20): Result<NewsletterData>

    // NOTE: backend route `POST /newsletter/send` not yet implemented; 404s until added.
    suspend fun sendNewsletter(): Result<Unit>

    // NOTE: backend route `POST /newsletter/test` not yet implemented; 404s until added.
    suspend fun sendTestNewsletter(): Result<Unit>
}
