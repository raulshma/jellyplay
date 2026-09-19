package com.raulshma.jellyplay.feature.player.video.engine

/**
 * Thin Media3 adapter over the shared [ContainerMimeTable] (commonMain): the
 * container→MIME table and its rationale live there, jvmTest-pinned; this
 * object keeps its name and signature for the existing platform call sites
 * (the mime strings the table returns are the media3 `MimeTypes` constants,
 * mirrored as plain strings — see the table's KDoc).
 */
internal object ContainerMimeMapper {
    fun mapToMime(container: String?): String? = ContainerMimeTable.mapToMime(container)
}
