package com.raulshma.jellyplay.feature.library

/**
 * WHICH full-screen surface the Library renders, folded ONCE by
 * [computeLibrarySurface] — the pure successor of the inline branch that used
 * to open the screen's root `Box` (`error != null && itemCount == 0` vs the
 * whole header+content `Column`). The `HomeSurface`/`computeSearchSurface`
 * precedent: the screen's `when` is exhaustive over the result and owns
 * rendering only, so the branch policy is assertable JVM-side
 * ([LibrarySurfaceTest]) without any Compose or paging stack.
 *
 * Deliberately two arms: the full-screen initial-loading/empty rungs are NOT
 * folded here — they are the chassis ladder's decisions (`pagedCollectionRung`,
 * pinned by `PagedCollectionLadderTest`) taken INSIDE [Content]'s
 * pull-to-refresh body, below a header that renders in every non-[Error]
 * state. The fold is also deliberately section-blind: the shipped branch never
 * read the section mode (the header's Reset pill and folder row gate on it,
 * the error gate does not), so it takes no such input.
 *
 * Precedence is fixed: Error → Content. The load-bearing corner is the
 * `itemCount == 0` interaction — an error over already-loaded items keeps the
 * stale grid (the header's error strip carries the message), exactly as the
 * pre-fold inline predicate did.
 */
internal sealed interface LibrarySurface {
    /**
     * The screen-level error fired with nothing loaded — the full-screen error
     * with retry. Carries the message verbatim; the screen renders it through
     * the shared [com.raulshma.jellyplay.core.ui.components.ErrorScreen].
     */
    data class Error(val message: String) : LibrarySurface

    /**
     * Everything else — header plus pull-to-refresh body, whose
     * initial-loading/empty/content rungs come from the shared chassis ladder.
     * Also the deliberate fall-through for an error over loaded items.
     */
    data object Content : LibrarySurface
}

/**
 * THE fold — pure over exactly the two scalars the screen's inline branch
 * read (the collected `error` and the pager's `itemCount`), no Compose types.
 */
internal fun computeLibrarySurface(
    error: String?,
    itemCount: Int,
): LibrarySurface =
    if (error != null && itemCount == 0) {
        LibrarySurface.Error(error)
    } else {
        LibrarySurface.Content
    }
