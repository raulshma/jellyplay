package com.raulshma.jellyplay.di

import com.raulshma.jellyplay.core.data.R
import com.raulshma.jellyplay.core.data.download.DownloadOutcomeMessenger
import com.raulshma.jellyplay.core.ui.feedback.UiText
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bridges core/data's [DownloadOutcomeMessenger] to core/ui's [UserMessageBus]:
 * the exact info/error toasts the per-ViewModel `when` cascades used to post
 * inline, now posted once by [com.raulshma.jellyplay.core.data.download.MediaDownloadActions.downloadAndReport].
 * Lives in the app module because only here may core/ui types be injected into
 * a core/data-bound seam.
 *
 * Koin-authored (dev's v0.10.7 Hilt @Binds DownloadOutcomeModule ported to the
 * kmp seam convention — androidAppInteropAdaptersModule pattern: UserMessageBus
 * is Koin-owned by the Android core:ui graph, and the messenger interface is
 * owned by :shared:core:data jvmShared, which cannot see core/ui).
 */
val downloadOutcomeModule: Module = module {
    single<DownloadOutcomeMessenger> { UserMessageBusDownloadMessenger(userMessageBus = get()) }
}

class UserMessageBusDownloadMessenger(
    private val userMessageBus: UserMessageBus,
) : DownloadOutcomeMessenger {

    override fun downloadStarted() {
        userMessageBus.info(
            UiText.Resource(R.string.data_download_started)
        )
    }

    override fun downloadStartFailed() {
        userMessageBus.error(
            UiText.Resource(R.string.data_download_start_failed)
        )
    }
}
