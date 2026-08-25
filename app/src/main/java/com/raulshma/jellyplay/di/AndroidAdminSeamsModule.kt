package com.raulshma.jellyplay.di

import android.content.Context
import com.raulshma.jellyplay.core.data.R
import com.raulshma.jellyplay.core.data.repository.AdminStatisticsLabelProvider
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * App-authored Koin definitions for the Android actuals of the admin
 * statistics seams (Wave wB admin flip — the AndroidDownloadSeamsModule
 * precedent: legacy-side resources the shared module cannot see get their
 * Koin defs in the composition root that sees both).
 *
 * [AdminStatisticsLabelProvider] is the locale seam of the Koin-owned
 * [com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepositoryImpl]
 * (moved to :shared:core:data jvmShared): every method is the verbatim
 * former `context.getString(R.string.data_*)` call, so Android behaviour —
 * including all 9 locale translations — is byte-identical to the pre-move
 * repository. The desktop actual (base-locale English literals) lives in
 * desktopDataModule.
 */
private class AndroidAdminStatisticsLabels(
    private val context: Context,
) : AdminStatisticsLabelProvider {
    override fun movies(): String = context.getString(R.string.data_label_movies)
    override fun episodes(): String = context.getString(R.string.data_label_episodes)
    override fun songs(): String = context.getString(R.string.data_label_songs)
    override fun addedToday(): String = context.getString(R.string.data_added_today)
    override fun addedOneDayAgo(): String = context.getString(R.string.data_added_one_day_ago)
    override fun addedDaysAgo(days: Int): String = context.getString(R.string.data_added_days_ago, days)
    override fun addedMonthsAgo(months: Int): String = context.getString(R.string.data_added_months_ago, months)
    override fun addedYearsAgo(years: Int): String = context.getString(R.string.data_added_years_ago, years)
    override fun daysSincePlay(days: Int): String = context.getString(R.string.data_days_since_play, days)
    override fun neverPlayed(): String = context.getString(R.string.data_never_played)
    override fun playsCount(count: Int): String = context.getString(R.string.data_plays_count, count)
    override fun addedDate(date: String): String = context.getString(R.string.data_added_date, date)
    override fun addedUnknown(): String = context.getString(R.string.data_added_unknown)
    override fun playedDate(date: String): String = context.getString(R.string.data_played_date, date)
}

fun androidAdminSeamsModule(context: Context): Module = module {
    single<AdminStatisticsLabelProvider> { AndroidAdminStatisticsLabels(context) }
}
