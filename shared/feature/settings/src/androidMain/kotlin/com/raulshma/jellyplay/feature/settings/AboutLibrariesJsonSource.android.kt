package com.raulshma.jellyplay.feature.settings

import android.content.Context

/**
 * Android actual of the [AboutLibrariesJsonSource] seam — the pre-migration
 * LicensesViewModel asset read, verbatim. Takes the application Context the
 * same way the Wave 1a AndroidSettingsPlatformModule Context-shaped actuals
 * (AndroidSettingsBackupIo / AndroidAppLocaleSetter) do; the composition root
 * constructs it during startKoin.
 */
internal class AndroidAboutLibrariesJsonSource(
    private val context: Context,
) : AboutLibrariesJsonSource {
    override fun read(): String? =
        context.assets.open("aboutlibraries.json").bufferedReader().use { it.readText() }
}
