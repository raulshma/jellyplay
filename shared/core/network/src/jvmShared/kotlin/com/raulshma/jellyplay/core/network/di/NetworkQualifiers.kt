package com.raulshma.jellyplay.core.network.di

import org.koin.core.qualifier.named
import org.koin.core.qualifier.Qualifier

/**
 * Koin qualifiers mirroring the legacy Hilt `@Named` strings for the derived
 * OkHttp clients (Phase C4). The names mirror the historic dagger
 * qualifiers the Hilt-era bridges keyed on; Koin is the only consumer now.
 */
object NetworkQualifiers {
    val streamingHttpClient: Qualifier = named("streaming")
    val downloadHttpClient: Qualifier = named("download")
}
