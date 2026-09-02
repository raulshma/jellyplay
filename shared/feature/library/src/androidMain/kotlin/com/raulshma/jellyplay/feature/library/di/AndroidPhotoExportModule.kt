package com.raulshma.jellyplay.feature.library.di

import android.app.Application
import com.raulshma.jellyplay.feature.library.AndroidPhotoExport
import com.raulshma.jellyplay.feature.library.PhotoExport
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android wiring for the photo-export seam (androidDataModule pattern): the
 * MediaStore/FileProvider actual needs the application context, so this is a
 * platform module function the app shell registers beside [libraryModule].
 */
fun androidPhotoExportModule(application: Application): Module = module {
    single<PhotoExport> { AndroidPhotoExport(application) }
}
