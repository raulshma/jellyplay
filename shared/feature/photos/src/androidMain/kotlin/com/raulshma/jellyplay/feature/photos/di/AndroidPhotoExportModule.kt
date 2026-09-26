package com.raulshma.jellyplay.feature.photos.di

import android.app.Application
import com.raulshma.jellyplay.feature.photos.AndroidPhotoExport
import com.raulshma.jellyplay.feature.photos.PhotoExport
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android wiring for the photo-export seam (androidDataModule pattern): the
 * MediaStore/FileProvider actual needs the application context, so this is a
 * platform module function the app shell registers beside the shared feature modules.
 */
fun androidPhotoExportModule(application: Application): Module = module {
    single<PhotoExport> { AndroidPhotoExport(application) }
}
