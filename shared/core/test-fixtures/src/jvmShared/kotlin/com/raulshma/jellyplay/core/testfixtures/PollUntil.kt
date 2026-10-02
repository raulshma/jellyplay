package com.raulshma.jellyplay.core.testfixtures

/**
 * Polls [condition] every 50 ms until true or [timeoutMs] elapses — the
 * wall-clock wait helper for suites whose engines/collaborators run on their
 * own threads (the desktop mpv suites; the recorder-sampling tests). Moved
 * beside [FakeMediaEngine] from the desktop tests' deleted fake file, so the
 * real-engine suites keep it after the fake-twin merge.
 *
 * Named `pollUntil` (not `waitUntil`) to stay clear of MpvDesktopEngineTest's
 * file-private helper of that name.
 */
fun pollUntil(message: String = "condition", timeoutMs: Long = 5_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!condition()) {
        if (System.currentTimeMillis() > deadline) {
            throw AssertionError("$message not met within ${timeoutMs}ms")
        }
        Thread.sleep(50)
    }
}
