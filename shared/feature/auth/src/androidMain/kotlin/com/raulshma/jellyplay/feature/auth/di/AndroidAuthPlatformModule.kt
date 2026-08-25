package com.raulshma.jellyplay.feature.auth.di

import android.content.Context
import com.raulshma.jellyplay.core.ui.util.LocalNetworkAccess
import com.raulshma.jellyplay.feature.auth.LocalNetworkStatus
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android platform pick for the auth feature's Android-only seam: the
 * LocalNetworkStatus gate bridges the legacy :core:ui LocalNetworkAccess
 * object — the single source of truth for the Android 17 local-network
 * permission (enforcement, grant check, LAN-address heuristic) — with the
 * application context handed in by the app composition root
 * (androidAdminModule / androidSettingsPlatformModule pattern). Desktop
 * registers its non-blaming counterpart from the module's jvmMain.
 */
fun androidAuthModule(context: Context): Module = module {
    single<LocalNetworkStatus> {
        LocalNetworkStatus { address ->
            LocalNetworkAccess.enforced &&
                !LocalNetworkAccess.isGranted(context) &&
                LocalNetworkAccess.isLocalAddress(address)
        }
    }
}
