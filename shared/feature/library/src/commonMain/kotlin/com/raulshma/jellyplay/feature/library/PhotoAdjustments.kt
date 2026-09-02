package com.raulshma.jellyplay.feature.library

import androidx.compose.ui.graphics.ColorFilter

/**
 * Photo-viewer color-adjustment matrix seam. Android composes the saturation ×
 * contrast/brightness matrices through android.graphics.ColorMatrix (verbatim
 * from the legacy PhotoViewerScreen); desktop builds the same 20-value matrix
 * with Compose's own ColorMatrix. timesAssign computes A·B while postConcat
 * computes P·A — the orders differ, but these particular matrices commute
 * (saturation has unit row sums and no translation; contrast is uniform
 * scale+offset), so the resulting filters match within float rounding.
 */
internal expect fun photoAdjustmentColorFilter(
    brightness: Float,
    contrast: Float,
    saturation: Float,
): ColorFilter
