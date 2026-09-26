package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.repository.AdminStatisticsLabelProvider
import com.raulshma.jellyplay.core.data.repository.DesktopAdminStatisticsLabels
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Admin family of the desktopDataModule split: the admin-statistics label
 * seam's desktop actual — the platform counterpart of jvmShared's
 * dataAdminModule cluster (whose KDoc documents the Android/desktop label
 * split). Binding body moved verbatim from the pre-split single-module
 * layout — see [desktopDataModule] for the aggregate and the family map.
 */
internal val desktopAdminModule: Module = module {
    //  admin flip: desktop actual of the admin-statistics label
    // seam — base-locale English literals (see the object's kdoc for the
    // accepted locale delta). The Android actual lives in the app
    // composition root (androidAdminSeamsModule) over legacy core:data
    // R.string.
    single<AdminStatisticsLabelProvider> { DesktopAdminStatisticsLabels }
}
