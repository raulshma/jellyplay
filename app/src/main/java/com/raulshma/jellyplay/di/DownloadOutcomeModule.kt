package com.raulshma.jellyplay.di

import com.raulshma.jellyplay.core.data.download.DownloadOutcomeMessenger
import com.raulshma.jellyplay.core.ui.feedback.UiText
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges core/data's [DownloadOutcomeMessenger] to core/ui's [UserMessageBus]:
 * the exact info/error toasts the per-ViewModel `when` cascades used to post
 * inline, now posted once by [MediaDownloadActions][com.raulshma.jellyplay.core.data.download.MediaDownloadActions.downloadAndReport].
 * Lives in the app module because only here may core/ui types be injected into
 * a core/data-bound seam.
 */
@Singleton
class UserMessageBusDownloadMessenger @Inject constructor(
    private val userMessageBus: UserMessageBus,
) : DownloadOutcomeMessenger {

    override fun downloadStarted() {
        userMessageBus.info(
            UiText.Resource(com.raulshma.jellyplay.core.data.R.string.data_download_started)
        )
    }

    override fun downloadStartFailed() {
        userMessageBus.error(
            UiText.Resource(com.raulshma.jellyplay.core.data.R.string.data_download_start_failed)
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadOutcomeModule {

    @Binds
    @Singleton
    abstract fun bindDownloadOutcomeMessenger(impl: UserMessageBusDownloadMessenger): DownloadOutcomeMessenger
}
