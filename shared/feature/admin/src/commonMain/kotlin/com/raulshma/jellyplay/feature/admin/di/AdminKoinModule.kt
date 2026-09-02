package com.raulshma.jellyplay.feature.admin.di

import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.feature.admin.dashboard.AdminDashboardViewModel
import com.raulshma.jellyplay.feature.admin.devices.DevicesViewModel
import com.raulshma.jellyplay.feature.admin.logs.LogsViewModel
import com.raulshma.jellyplay.feature.admin.plugins.PluginDetailViewModel
import com.raulshma.jellyplay.feature.admin.plugins.PluginsViewModel
import com.raulshma.jellyplay.feature.admin.stalemedia.StaleMediaViewModel
import com.raulshma.jellyplay.feature.admin.statistics.UserStatisticsViewModel
import com.raulshma.jellyplay.feature.admin.statistics.detail.UserStatisticsDetailViewModel
import com.raulshma.jellyplay.feature.admin.tasks.ScheduledTasksViewModel
import com.raulshma.jellyplay.feature.admin.users.UsersViewModel
import com.raulshma.jellyplay.feature.admin.users.detail.UserDetailViewModel
import com.raulshma.jellyplay.feature.admin.watchedremoval.WatchedMediaCleanupViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the admin feature (docs/kmp-migration-plan.md
 * §Phase V3, admin conveyor — eighth feature). The HiltViewModel/@Inject
 * annotations were stripped at the move — Koin is the single constructor
 * owner (one framework per type). Ctor deps all resolve from the shared
 * :core:data Koin graph (dataJvmModule) on BOTH platforms since the admin
 * repo flip:
 *  - AdminRepository + AdminStatisticsRepository are dataJvmModule singles
 *    (impls moved out of legacy :core:data; the app's DataModule only keeps
 *    koin().get() @Provides bridges for its remaining Hilt injectors);
 *  - AuthRepository likewise resolves from dataJvmModule.
 *
 * The Android-only PluginConfigViewModel (WebView quartet, Context ctor dep)
 * lives in androidAdminModule in this module's androidMain.
 */
val adminModule: Module = module {
    viewModel {
        AdminDashboardViewModel(
            adminRepository = get(),
        )
    }
    viewModel {
        ScheduledTasksViewModel(
            adminRepository = get(),
        )
    }
    viewModel {
        DevicesViewModel(
            adminRepository = get(),
        )
    }
    viewModel {
        LogsViewModel(
            adminRepository = get(),
        )
    }
    viewModel {
        PluginsViewModel(
            adminRepository = get(),
        )
    }
    viewModel {
        PluginDetailViewModel(
            adminRepository = get(),
        )
    }
    viewModel {
        UserStatisticsViewModel(
            repository = get<AdminStatisticsRepository>(),
        )
    }
    viewModel {
        UserStatisticsDetailViewModel(
            repository = get<AdminStatisticsRepository>(),
        )
    }
    viewModel {
        StaleMediaViewModel(
            repository = get<AdminStatisticsRepository>(),
            authRepository = get<AuthRepository>(),
        )
    }
    viewModel {
        WatchedMediaCleanupViewModel(
            repository = get<AdminStatisticsRepository>(),
            authRepository = get<AuthRepository>(),
        )
    }
    viewModel {
        UsersViewModel(
            adminRepository = get<AdminRepository>(),
        )
    }
    viewModel {
        UserDetailViewModel(
            adminRepository = get<AdminRepository>(),
        )
    }
}
