package com.raulshma.jellyplay.feature.photos.di

import com.raulshma.jellyplay.feature.photos.DesktopPhotoExport
import com.raulshma.jellyplay.feature.photos.PhotoExport
import org.koin.core.module.Module
import org.koin.dsl.module

/** Desktop wiring for the (inert) photo-export seam — see [DesktopPhotoExport]. */
fun desktopPhotoExportModule(): Module = module {
    single<PhotoExport> { DesktopPhotoExport() }
}
