package com.raulshma.jellyplay.feature.settings

import android.os.Build

internal actual val supportsDynamicColor: Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
