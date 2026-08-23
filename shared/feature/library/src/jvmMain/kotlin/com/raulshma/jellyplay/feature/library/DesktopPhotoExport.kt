package com.raulshma.jellyplay.feature.library

/**
 * Desktop [PhotoExport]: no system gallery or share sheet exists yet, so
 * export is unsupported and the viewer hides the affordances (voice-search
 * seam pattern). The methods never run — [isSupported] is false — but throw
 * defensively if invoked directly.
 */
internal class DesktopPhotoExport : PhotoExport {
    override val isSupported: Boolean = false

    override suspend fun saveToGallery(imageUrl: String, displayName: String) {
        error("Saving photos to a gallery is not supported on desktop yet")
    }

    override suspend fun sharePhoto(imageUrl: String, displayName: String) {
        error("Sharing photos is not supported on desktop yet")
    }
}
