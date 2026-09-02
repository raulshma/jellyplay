package com.raulshma.jellyplay.feature.subtitle.tester

/**
 * Canonical id literals for the built-in sample presets. Lives in commonMain
 * so [SubtitleTesterUiState]'s default `samplePresetId` and the (androidMain)
 * [SampleSubtitlePresets] registry share a single source of truth — the
 * registry itself can never be commonMain because its entries carry `@RawRes`
 * ids from this module's androidMain R class.
 */
object SampleSubtitlePresetIds {
    const val DIALOGUE = "dialogue"
    const val LYRICS = "lyrics"
    const val SIGNS = "signs"
    const val ACTION = "action"
}
