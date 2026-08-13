package com.raulshma.jellyplay.feature.player.video.engine

/**
 * Engine-neutral aspect-ratio choice, used as the [MediaEngine.setAspectRatio]
 * contract so no media3 `RESIZE_MODE_*` integer crosses the engine seam.
 *
 * `AUTO` means "follow the detected stream ratio"; the screen resolves it to a
 * concrete ratio (or `FIT`) before it reaches an engine. The optional [ratio]
 * carries the numeric value for the fixed-ratio entries (`16:9`, `4:3`, `21:9`).
 */
enum class AspectRatio(val displayName: String, val ratio: Float?) {
    AUTO("Auto", null),
    FIT("Fit", null),
    FILL("Fill", null),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_21_9("21:9", 21f / 9f),
    CROP("Crop", null),
}
