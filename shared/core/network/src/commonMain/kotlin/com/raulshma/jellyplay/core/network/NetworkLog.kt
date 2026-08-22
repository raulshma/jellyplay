package com.raulshma.jellyplay.core.network

/**
 * Minimal logging facade for the shared network module (docs/kmp-migration-plan.md
 * §Phase C3). The jvmShared impls previously called android.util.Log directly;
 * each target maps onto its native logger:
 *  - androidMain → android.util.Log (identical tags/levels as pre-migration)
 *  - jvmMain     → slf4j (the same backend the Jellyfin SDK logs through)
 *  - wasmJsMain  → console
 *
 * Expect functions cannot have default arguments, so the warn-with-throwable
 * form is a separate overload and call sites pass the error explicitly.
 */
expect object NetworkLog {
    fun d(tag: String, message: String)

    /** Warn without a throwable. */
    fun w(tag: String, message: String)

    /** Warn with an optional attached throwable. */
    fun w(tag: String, message: String, error: Throwable?)

    fun e(tag: String, message: String, error: Throwable?)

    /**
     * Gate for expensive debug-message construction (mirrors
     * `Log.isLoggable(tag, Log.DEBUG)` on Android / slf4j's `isDebugEnabled`).
     */
    fun isDebugEnabled(tag: String): Boolean
}
