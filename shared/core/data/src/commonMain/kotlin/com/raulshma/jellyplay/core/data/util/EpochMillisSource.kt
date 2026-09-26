package com.raulshma.jellyplay.core.data.util

/**
 * D3: the epoch-millis clock seam moved down to :shared:core:model
 * (`com.raulshma.jellyplay.core.model.EpochMillisSource`) — core:network
 * sits BELOW core:data in the module graph and could never adopt a seam
 * living here. This alias keeps the not-yet-migrated imports in this
 * module's commonMain repository impls (and any external feature-module
 * imports) compiling; migrate them per-touch.
 */
@Deprecated(
    message = "Moved to :shared:core:model — use the core.model EpochMillisSource",
    replaceWith = ReplaceWith("EpochMillisSource", "com.raulshma.jellyplay.core.model.EpochMillisSource"),
)
typealias EpochMillisSource = com.raulshma.jellyplay.core.model.EpochMillisSource
