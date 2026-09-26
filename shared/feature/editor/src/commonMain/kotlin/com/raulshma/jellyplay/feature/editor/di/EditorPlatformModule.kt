package com.raulshma.jellyplay.feature.editor.di

import com.raulshma.jellyplay.feature.editor.EditorSubtitleStore
import org.koin.core.module.Module

/**
 * Platform registration fragment for the editor feature's [EditorSubtitleStore]
 * binding (PhotoExport's androidPhotoExportModule/desktopPhotoExportModule
 * shape, but composed into editorModule via [org.koin.core.module.Module.includes]
 * so the app composition roots keep registering the single `editorModule`):
 *  - jvmShared actual: wraps the process-wide `StreamingSubtitleStore` single
 *    (dataJvmModule's binding — Android filesDir / desktop appdata dir) so
 *    android + desktop behavior is unchanged.
 */
internal expect fun platformEditorModule(): Module
