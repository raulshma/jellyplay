package com.raulshma.jellyplay.core.ui.tv

import android.content.Context
import android.content.pm.PackageManager

/**
 * Android TV feature detection. The composition locals ([LocalTvMode],
 * [LocalTvTypography], [LocalTvDrawerOpener]) and [TvScaffold] live in
 * `shared/core/ui`.
 */
fun Context.isTv(): Boolean =
    packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY)
