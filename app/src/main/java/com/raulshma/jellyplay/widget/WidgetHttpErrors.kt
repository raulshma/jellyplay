package com.raulshma.jellyplay.widget

/**
 * Shared classification of throwable messages for widget background workers.
 *
 * A failure whose message indicates an authorization/permissions/not-found
 * condition is treated as permanent — the worker should not retry it, since
 * retrying a 401/403/404 will never succeed and only burns WorkManager quota.
 *
 * Extracted verbatim from the previously duplicated private helpers in
 * `LibraryRecommendationsWidgetWorker` and `SeerrRecommendationsWidgetWorker`.
 */
fun isPermanentWidgetFailure(throwable: Throwable): Boolean {
    val message = throwable.message ?: return false
    return message.contains("401") || message.contains("403") ||
        message.contains("404") || message.contains("Unauthorized") ||
        message.contains("Forbidden") || message.contains("Not Found")
}
