package com.raulshma.jellyplay.feature.admin.di

import android.content.Context
import com.raulshma.jellyplay.feature.admin.plugins.PluginConfigViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android platform pick for the admin feature's Android-only ViewModels: the
 * WebView plugin-config ViewModel keeps its Context ctor param (it reads the
 * `pluginBridge.js` asset), so it is constructed here with the application
 * context handed in by the app composition root — the same parameterised
 * pattern as androidSettingsPlatformModule. Desktop has no WebView host and
 * therefore no equivalent definition (PluginConfigHost renders a static
 * fallback there).
 */
fun androidAdminModule(context: Context): Module = module {
    viewModel {
        PluginConfigViewModel(
            context = context,
            adminRepository = get(),
        )
    }
}
