package com.raulshma.jellyplay.core.data.repository

/**
 * Locale seam for the user-visible labels [AdminStatisticsRepositoryImpl]
 * bakes into persisted scan rows (stale/watched-media cleanup) and the
 * per-user content breakdown. The repo left `context.getString(R.string.…)`
 * behind when it moved to jvmShared (Phase X admin flip), and shared
 * :core:data has no resource infrastructure — the downloads conveyor's
 * precedent is platform-edge formatting, so every former getString call site
 * maps to exactly one method here (same name, same format args).
 *
 * One definition per platform:
 *  - Android: the app composition root's androidAdminSeamsModule, delegating
 *    to `context.getString(legacy core:data R.string.data_*)` — behaviour is
 *    byte-identical to the pre-move code, all 9 locales included;
 *  - desktop: [DesktopAdminStatisticsLabels] in desktopDataModule, English
 *    literals byte-matching the base-locale strings (English-only on desktop,
 *    the documented downloads-string delta).
 */
interface AdminStatisticsLabelProvider {
    /** data_label_movies — "Movies". */
    fun movies(): String

    /** data_label_episodes — "Episodes". */
    fun episodes(): String

    /** data_label_songs — "Songs". */
    fun songs(): String

    /** data_added_today — "Added today". */
    fun addedToday(): String

    /** data_added_one_day_ago — "Added 1 day ago". */
    fun addedOneDayAgo(): String

    /** data_added_days_ago — "Added %1$dd ago". */
    fun addedDaysAgo(days: Int): String

    /** data_added_months_ago — "Added %1$dmo ago". */
    fun addedMonthsAgo(months: Int): String

    /** data_added_years_ago — "Added %1$dy ago". */
    fun addedYearsAgo(years: Int): String

    /** data_days_since_play — "%1$dd since play". */
    fun daysSincePlay(days: Int): String

    /** data_never_played — "Never played". */
    fun neverPlayed(): String

    /** data_plays_count — "%1$d plays". */
    fun playsCount(count: Int): String

    /** data_added_date — "Added %1$s". */
    fun addedDate(date: String): String

    /** data_added_unknown — "Added unknown". */
    fun addedUnknown(): String

    /** data_played_date — "Played %1$s". */
    fun playedDate(date: String): String
}
