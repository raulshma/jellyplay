package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.IntegrationsBackupSliceSource
import com.raulshma.jellyplay.core.data.IntegrationsStoreFan
import com.raulshma.jellyplay.core.data.ItemPrefsBackupSliceSource
import com.raulshma.jellyplay.core.data.PlaylistsBackupSliceSource
import com.raulshma.jellyplay.core.data.WidgetBackupSliceSource
import com.raulshma.jellyplay.core.datastore.settings.ExternalBackupSlice
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin construction owner for the Wave-2 settings-backup slice sources (the
 * [ExternalBackupSlice] seam impls) — the Room-backed config and the
 * allowlisted config surfaces that live OUTSIDE the 19 domain-store fan-out
 * (see SettingsBackupSliceSources.kt). This is the core:data ↔ core:datastore
 * module-boundary crossing for the backup: the DAOs / WidgetDataStore /
 * integration stores resolve from the database + datastore modules here, and
 * `UserPreferencesStore` (datastoreCommonModule) folds them in via
 * `getAll<ExternalBackupSlice>()` — so a graph that loads core:data gets the
 * four slices in the backup envelope, and a datastore-only graph (tests,
 * standalone resolution) simply gets none.
 */
val dataSettingsBackupSlicesModule: Module = module {

    single {
        IntegrationsBackupSliceSource(
            fan = IntegrationsStoreFan(
                seerrPreferencesStore = get(),
                arrPreferencesStore = get(),
                subtitleProviderPreferencesStore = get(),
            ),
        )
    } bind ExternalBackupSlice::class

    single {
        ItemPrefsBackupSliceSource(
            database = get(),
            dao = get(),
        )
    } bind ExternalBackupSlice::class

    single {
        PlaylistsBackupSliceSource(
            database = get(),
            smartPlaylistDao = get(),
            moodPlaylistDao = get(),
        )
    } bind ExternalBackupSlice::class

    single {
        WidgetBackupSliceSource(
            widgetDataStore = get(),
        )
    } bind ExternalBackupSlice::class
}
