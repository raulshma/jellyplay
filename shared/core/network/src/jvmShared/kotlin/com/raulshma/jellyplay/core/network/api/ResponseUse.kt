package com.raulshma.jellyplay.core.network.api

import okhttp3.Response

/**
 * [Response]-scoped block — the `use` replacement for EVERY okhttp call
 * site in the app (JellyfinRawRequester, HttpExecutor, the book/video/data
 * clients all route through this).
 *
 * WHY NOT Kotlin's `use`: it inlines `kotlin.io.closeFinally(AutoCloseable)`
 * at each call site, and R8's close-support rewriting then *sometimes*
 * specializes that synthetic to the concrete receiver type — on v0.11.11
 * (Nov 2026) it emitted `closeFinally(Response)` on some build machines and
 * the generic `closeFinally(AutoCloseable)` on others, producing two
 * byte-stable dex variants selected by the machine's shape. The F-Droid
 * reproducible-build byte-compare between the reference binary and the
 * buildserver rebuild then fails nondeterministically — run-to-run on the
 * SAME machine class (pipelines #41/#43/#44/#45, one ~2 KB dex delta in an
 * R8 outline class). A plain try/finally `close()` leaves nothing for R8
 * to specialize, so the dex output is identical on every machine.
 *
 * Semantics match `use` for the cases that can occur here: the block
 * result is returned, [Response.close] runs on both paths, and — unlike
 * streams — `Response.close()` is idempotent and does not throw, so there
 * is no suppressed-exception subtlety to reproduce.
 */
inline fun <T> Response.useResponse(block: (Response) -> T): T {
    try {
        return block(this)
    } finally {
        close()
    }
}
