package com.raulshma.jellyplay.core.network.di

import org.koin.core.qualifier.named
import org.koin.core.qualifier.Qualifier

/**
 * Koin qualifiers mirroring the legacy Hilt `@Named` strings for the derived
 * OkHttp clients (Phase C4). The names MUST stay identical to the dagger
 * qualifiers so the Hilt bridges in the legacy shim resolve the same
 * definitions Koin constructs.
 */
object NetworkQualifiers {
    val streamingHttpClient: Qualifier = named("streaming")
    val downloadHttpClient: Qualifier = named("download")
}
