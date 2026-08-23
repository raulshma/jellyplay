package com.raulshma.jellyplay.feature.library

/**
 * Platform seam for exporting the currently viewed photo (docs/kmp-migration-plan.md
 * §Phase V3 library conveyor). Android saves into MediaStore ("gallery") and
 * shares through FileProvider + ACTION_SEND — those bodies moved verbatim from
 * the legacy PhotoViewerViewModel into the androidMain actual; desktop has no
 * gallery or share sheet yet, so [isSupported] is false there and the viewer
 * hides the save/share affordances (same pattern as search's voice seam).
 *
 * Implementations throw with a user-presentable message on failure; the
 * ViewModel maps the throw into its save-result / share-error UI state, exactly
 * where the inline try/catch used to sit.
 */
interface PhotoExport {

    /** Whether this platform can export; gates the viewer's save/share buttons. */
    val isSupported: Boolean

    /**
     * Downloads [imageUrl] and inserts it into the system gallery as
     * `<displayName>_<timestamp>.jpg` (JPEG, quality 95).
     */
    suspend fun saveToGallery(imageUrl: String, displayName: String)

    /** Downloads [imageUrl] and opens the platform share sheet for it. */
    suspend fun sharePhoto(imageUrl: String, displayName: String)
}
