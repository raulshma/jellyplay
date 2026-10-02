package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.network.api.NewsletterApiClient

/**
 * The newsletter data seam — RETIRED pass-through mirror: the three members
 * this interface used to re-declare were one-line forwards over the client
 * family, so the data-facing interface now extends [NewsletterApiClient]
 * verbatim with zero added members (the LiveTvRepository shape). There is no
 * impl class: the data DI module re-binds the [com.raulshma.jellyplay.core.network.api.MediaInfoApiClient]
 * single under this name — it IS-A newsletter client through the family
 * supertype, so the forward bodies (and their file) are gone.
 */
interface NewsletterRepository : NewsletterApiClient
