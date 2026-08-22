package com.raulshma.jellyplay.core.data.util

import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

/**
 * Lenient JSON codec for the persisted filter blobs exchanged by the library
 * and search features. `ignoreUnknownKeys` keeps decode forward-compatible when
 * fields are added later; `encodeDefaults` guarantees a complete on-disk
 * snapshot. Shared here so the two feature modules cannot drift out of shape.
 *
 * Note: `ignoreUnknownKeys` does NOT suppress unknown enum constants — callers
 * that decode persisted blobs keep a try/catch as the resilience boundary for
 * those.
 */
val FilterCodec: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Delay before a single retry of a genre/tag filter lookup. A transient
 * network blip shouldn't leave the filter sheet permanently missing a section.
 */
const val FILTER_RETRY_DELAY_MS: Long = 800

/**
 * Runs [fetch] and hands a successful list to [onResult]. On failure, waits
 * [FILTER_RETRY_DELAY_MS] and retries once — a single, best-effort recovery
 * for the genre/tag lookups that back the filter sheet. Anything still failing
 * after the retry is reported to the caller via the untouched result.
 */
suspend fun <T> loadListWithRetry(
    fetch: suspend () -> Result<List<T>>,
    onResult: (List<T>) -> Unit,
) {
    var result = fetch()
    if (result.isFailure) {
        delay(FILTER_RETRY_DELAY_MS)
        result = fetch()
    }
    result.onSuccess(onResult)
}
