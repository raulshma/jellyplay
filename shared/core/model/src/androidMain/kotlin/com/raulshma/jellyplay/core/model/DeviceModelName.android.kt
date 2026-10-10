package com.raulshma.jellyplay.core.model

import android.os.Build

actual val deviceModelName: String? =
    Build.MODEL?.takeIf { it.isNotBlank() }
