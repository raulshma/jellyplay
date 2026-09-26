package com.raulshma.jellyplay.core.data.error

import com.raulshma.jellyplay.core.network.api.ApiException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the error-message fold's classification table: an [ApiException]'s
 * classified message wins over the caller fallback at EVERY flavor
 * (retryable, access-denied, plain http, network), a non-[ApiException]
 * keeps its own message, and only a null message falls to the fallback —
 * the exact ladder the ~50 swept `e.message ?: "<literal>"` sites
 * hand-rolled.
 */
class UserErrorMessagesTest {

    private val fallback = "Failed to do the thing"

    // ── ApiException flavors: the classified message always wins ──────────

    @Test
    fun `retryable server-error ApiException keeps its classified message`() {
        val e = ApiException.fromHttp(503, "Server error (503). Please try again later.")
        assertEquals("Server error (503). Please try again later.", UserErrorMessages.resolve(e, fallback))
    }

    @Test
    fun `rate-limit ApiException is retryable and keeps its message`() {
        val e = ApiException.fromHttp(429, "Too many requests.")
        assertEquals("Too many requests.", UserErrorMessages.resolve(e, fallback))
    }

    @Test
    fun `access-denied ApiException keeps the permission wording over the fallback`() {
        val denied = ApiException.fromHttp(403, "You don't have permission to access this item.")
        assertEquals(
            "You don't have permission to access this item.",
            UserErrorMessages.resolve(denied, fallback),
        )
        val unauthorized = ApiException.fromHttp(401, "Authentication required. Please sign in again.")
        assertEquals(
            "Authentication required. Please sign in again.",
            UserErrorMessages.resolve(unauthorized, fallback),
        )
    }

    @Test
    fun `plain http-status ApiExceptions keep their classified messages`() {
        assertEquals(
            "Item not found.",
            UserErrorMessages.resolve(ApiException.fromHttp(404, "Item not found."), fallback),
        )
        assertEquals(
            "Request failed (418).",
            UserErrorMessages.resolve(ApiException.fromHttp(418, "Request failed (418)."), fallback),
        )
    }

    @Test
    fun `network-classified ApiException keeps the friendly mapper text`() {
        val e = ApiException(
            isRetryable = true,
            message = "Unable to reach server. Check the URL and your network connection.",
            cause = java.net.UnknownHostException("nope.example"),
        )
        assertEquals(
            "Unable to reach server. Check the URL and your network connection.",
            UserErrorMessages.resolve(e, fallback),
        )
    }

    // ── non-ApiException: own message, else the caller fallback ───────────

    @Test
    fun `non-ApiException with a message keeps it verbatim`() {
        assertEquals("offline", UserErrorMessages.resolve(IllegalStateException("offline"), fallback))
    }

    @Test
    fun `non-ApiException without a message falls to the caller fallback`() {
        assertEquals(fallback, UserErrorMessages.resolve(RuntimeException(null as String?), fallback))
        assertEquals(fallback, UserErrorMessages.resolve(RuntimeException(), fallback))
    }

    @Test
    fun `null throwable falls to the caller fallback`() {
        assertEquals(fallback, UserErrorMessages.resolve(null as Throwable?, fallback))
    }

    // ── Result overloads ──────────────────────────────────────────────────

    @Test
    fun `Result failure resolves through the same ladder`() {
        assertEquals(
            "You don't have permission to access this item.",
            UserErrorMessages.resolve(
                Result.failure<Unit>(ApiException.fromHttp(403, "You don't have permission to access this item.")),
                fallback,
            ),
        )
        assertEquals(fallback, UserErrorMessages.resolve(Result.failure<Unit>(RuntimeException()), fallback))
    }

    @Test
    fun `Result success resolves to the caller fallback`() {
        assertEquals(fallback, UserErrorMessages.resolve(Result.success(Unit), fallback))
    }

    // ── rawOrNull: message-presence branching (auth's Raw-vs-Resource) ────

    @Test
    fun `rawOrNull mirrors resolve and returns null where the fallback would win`() {
        assertEquals(
            "Authentication required. Please sign in again.",
            UserErrorMessages.rawOrNull(ApiException.fromHttp(401, "Authentication required. Please sign in again.")),
        )
        assertNull(UserErrorMessages.rawOrNull(RuntimeException(null as String?)))
        assertNull(UserErrorMessages.rawOrNull(Result.success(Unit) as Result<*>))
    }
}
