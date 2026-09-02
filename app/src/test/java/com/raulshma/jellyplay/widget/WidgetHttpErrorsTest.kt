package com.raulshma.jellyplay.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the permanent-failure classification for widget background workers:
 * auth/permission/not-found conditions must read as permanent (retrying
 * burns WorkManager quota and never succeeds) while transient conditions
 * (null message, server errors) stay retryable.
 */
class WidgetHttpErrorsTest {

    @Test
    fun `status code messages classify as permanent`() {
        assertTrue(isPermanentWidgetFailure(IllegalStateException("HTTP 401")))
        assertTrue(isPermanentWidgetFailure(IllegalStateException("HTTP 403")))
        assertTrue(isPermanentWidgetFailure(IllegalStateException("HTTP 404")))
    }

    @Test
    fun `reason-phrase messages classify as permanent`() {
        assertTrue(isPermanentWidgetFailure(IllegalStateException("Unauthorized")))
        assertTrue(isPermanentWidgetFailure(IllegalStateException("Forbidden")))
        assertTrue(isPermanentWidgetFailure(IllegalStateException("Not Found")))
    }

    @Test
    fun `null message is retryable`() {
        assertFalse(isPermanentWidgetFailure(IllegalStateException()))
    }

    @Test
    fun `server errors and unknown messages are retryable`() {
        assertFalse(isPermanentWidgetFailure(IllegalStateException("HTTP 500")))
        assertFalse(isPermanentWidgetFailure(IllegalStateException("connection reset")))
    }

    @Test
    fun `permanent marker inside a longer message still classifies`() {
        // The check is a contains() over the whole message, so wrapped
        // exceptions ("...: 401 Unauthorized") must not fall through.
        assertTrue(
            isPermanentWidgetFailure(
                IllegalStateException("Request failed: 401 Unauthorized for /Users/Me"),
            ),
        )
    }
}
