package com.raulshma.jellyplay.core.data.error

import com.raulshma.jellyplay.core.network.api.ApiException

/**
 * THE error-message fold: the one resolver that turns a failed repository
 * answer (a raw [Throwable] or the [Result] that carried it) plus a
 * caller-supplied fallback into the user-facing message string a ViewModel
 * puts on screen. The ~50 per-feature hand copies of
 * `e.message ?: "<fallback>"` / `result.exceptionOrNull()?.message ?: "…"`
 * route through here, so the policy exists once instead of once per screen.
 *
 * The load-bearing contract — why the fallback exists and when it loses:
 *
 * 1. An [ApiException] always wins with its own message. The network layer
 *    classifies failures BEFORE the friendly text is attached
 *    (`JellyfinErrorMapper` for the Jellyfin SDK path, the HTTP-status
 *    factories for the raw paths), so an [ApiException]'s message IS the
 *    classified, user-facing wording: the retryable/access-denied/http
 *    distinction already drove the message choice at throw time
 *    (`isAccessDenied` 401/403 render as permission text, 404 as
 *    not-found, 5xx as server-error-retry-later, unknown-host/timeout as
 *    reachability text). The fold therefore NEVER re-words or overrides a
 *    classified message with the caller fallback — the fallback would
 *    demote a precise message to a generic one.
 * 2. A non-[ApiException] with a message keeps that message. Repositories
 *    legitimately raise non-network errors whose message is the best
 *    available text (an `IllegalStateException("offline")` from a store
 *    guard, for example), and every swept site displayed it verbatim.
 * 3. Only a null message falls to [fallback] — the caller's context-aware
 *    literal ("Failed to create user", "Couldn't delete the file.", …).
 *    Every site passes its PRE-EXISTING literal unchanged, so no UX string
 *    moves in this fold.
 *
 * Deliberately NOT here: sites whose behavior DISTINGUISHES the
 * classification keep their own ladders — the details load reducer buckets
 * access-denied/unavailable-offline into dedicated UI states, and
 * `ArrRepositoryImpl`'s discovery path maps 401/403 to
 * `ArrDiscoveryError.NoAdminPermission`. When a future site needs a
 * retryable/access-denied message ladder of its own, grow it here first so
 * the policy stays single-homed.
 *
 * [rawOrNull] serves the minority of sites that branch on message
 * PRESENCE rather than on a fallback string (the auth screens' Raw-vs-
 * Resource message seal): it returns the same resolution the fold would
 * render, or null when the fold would have used the fallback.
 */
object UserErrorMessages {

    /** [resolve] for a [Result] — resolves the failure, passes successes to [fallback]. */
    fun resolve(result: Result<*>, fallback: String): String =
        resolve(result.exceptionOrNull() ?: return fallback, fallback)

    /** [rawOrNull] for a [Result]. */
    fun rawOrNull(result: Result<*>): String? = rawOrNull(result.exceptionOrNull() ?: return null)

    /** [resolve] for a nullable throwable — null resolves straight to [fallback]. */
    fun resolve(throwable: Throwable?, fallback: String): String =
        throwable?.message ?: fallback

    /** [rawOrNull] for a nullable throwable. */
    fun rawOrNull(throwable: Throwable?): String? = throwable?.message
}
