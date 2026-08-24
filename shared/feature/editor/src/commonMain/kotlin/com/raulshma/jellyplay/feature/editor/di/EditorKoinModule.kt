package com.raulshma.jellyplay.feature.editor.di

import com.raulshma.jellyplay.feature.editor.EditorViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the editor feature (docs/kmp-migration-plan.md
 * §Phase V3, ninth conveyor item). The HiltViewModel/@Inject/@ApplicationContext
 * annotations were stripped at the move — Koin is the single constructor owner
 * (one framework per type). Ctor deps split three ways:
 *  - MetadataEditorRepository / AuthRepository / SubtitleProviderRepository
 *    are Koin-native (dataJvmModule in :shared:core:data);
 *  - StreamingSubtitleStore's interface lives in shared :core:data commonMain
 *    but its impl is still Hilt-owned (legacy SubtitleModule @Binds), so on
 *    Android it reaches Koin through the app composition root's Hilt interop
 *    module (dies at Phase X);
 *  - the @ApplicationContext Context param died with the SAF-read seam
 *    (EditorFilePicker: the contentResolver byte read moved into the Android
 *    actual).
 *
 * Desktop: no StreamingSubtitleStore definition exists yet, so resolution of
 * this ViewModel would throw NoDefinitionFound — registered inert-latent like
 * the other pre-Phase-X conveyor modules (the desktop shell has no editor nav
 * entry; grep confirms no route/screen reference).
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
