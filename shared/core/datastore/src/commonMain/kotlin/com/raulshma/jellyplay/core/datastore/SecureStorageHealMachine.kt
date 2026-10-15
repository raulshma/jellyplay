package com.raulshma.jellyplay.core.datastore

/**
 * Pure self-heal state machine behind the encrypted [SecureKeyValueStorage]
 * backends. Owns the constructor-open guard, the delete-checked reset and the
 * read/write guard routing to an in-memory fallback — the choreography that
 * absorbed the undecryptable-state crash loop (370354c49) and its
 * deletion-checked, in-memory fallback refinement (f5b667b6c). It sees only
 * injected operation results: `open` produces the encrypted backing or throws
 * on undecryptable state, `delete` reports whether the poisoned file is
 * actually gone, `fallback` is the caller-hosted process-local store. Platform
 * classes supply the real operations and the storage; the behavior is pinned
 * by jvmTest against fakes.
 *
 * Contract (exact — the two crash fixes are the spec):
 *  - A failed constructor open takes the reset path. On the open paths, ANY
 *    `Throwable` counts as undecryptable (the old `runCatching` semantics);
 *    the read/write guards differ deliberately and catch `Exception` only.
 *  - [reset][reset] reopens ONLY when the delete actually succeeded —
 *    reopening a file that couldn't be removed would hand back a store whose
 *    keyset loads but whose values still throw on every read outside the
 *    guards. Delete failed, or the fresh open failed → the in-memory
 *    [fallback] serves. A delete that throws counts as failed.
 *  - A guarded read/write that throws heals (reset) and retries ONCE on the
 *    fresh backing; a second throw propagates.
 */
class SecureStorageHealMachine<Backing : Any>(
    private val fallback: Backing,
    private val open: () -> Backing,
    private val delete: () -> Boolean,
) {

    // Constructor-open guard: see the class contract for the Throwable scope.
    private var backing: Backing = try {
        open()
    } catch (_: Throwable) {
        reset()
    }

    private fun reset(): Backing {
        val deleted = try {
            delete()
        } catch (_: Throwable) {
            false
        }
        backing = if (deleted) {
            try {
                open()
            } catch (_: Throwable) {
                fallback
            }
        } else {
            fallback
        }
        return backing
    }

    /**
     * Runs [block] on the current backing; an `Exception` heals via reset and
     * retries once on the fresh backing.
     */
    fun <T> guarded(block: (Backing) -> T): T =
        try {
            block(backing)
        } catch (_: Exception) {
            block(reset())
        }
}
