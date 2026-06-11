package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.NewsletterData

interface NewsletterRepository {

    suspend fun getNewsletterData(sinceDate: String, limit: Int = 20): Result<NewsletterData>
}
