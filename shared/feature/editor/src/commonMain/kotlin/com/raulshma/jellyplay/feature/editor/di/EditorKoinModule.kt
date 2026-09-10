package com.raulshma.jellyplay.feature.editor.di

import com.raulshma.jellyplay.feature.editor.EditorViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the editor feature (docs/kmp-migration-plan.md
 * , ninth conveyor item). The HiltViewModel/@Inject/@ApplicationContext
 * annotations were stripped at the move — Koin is the single constructor owner
 * (one framework per type). Ctor deps split two ways:
 *  - MetadataEditorRepository / AuthRepository / SubtitleProviderRepository
 *    are Koin-native (dataJvmModule in :shared:core:data);
 *  - StreamingSubtitleStore: the interface lives in :shared:core:data's
 *    jvmShared source set, and its file-backed impl was promoted there —
 *    Android binds it over `context.filesDir` in
 *    androidCoreDataModule, desktop over the appdata dir in desktopDataModule
 *    (both platforms also feed the player's SubtitleManager from the same
 *    binding).
 *
 * Live-resolvable on BOTH platforms: desktop renders editorSection
 * (DesktopAppRoot registers Route.MetadataEditor; the platform file-picker
 * actuals open a native AWT dialog on desktop, so upload-from-file works
 * there too — see DesktopEditorFilePicker).
 */
val editorModule: Module = module {
    viewModel {
        EditorViewModel(
            editorRepository = get(),
            authRepository = get(),
            subtitleProviderRepository = get(),
            streamingSubtitleStore = get(),
        )
    }
}
