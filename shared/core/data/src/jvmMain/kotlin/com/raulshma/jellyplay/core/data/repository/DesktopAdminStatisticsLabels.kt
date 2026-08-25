package com.raulshma.jellyplay.core.data.repository

/**
 * Desktop actual of [AdminStatisticsLabelProvider]: base-locale English
 * literals, byte-matching the legacy core:data `values/strings.xml` entries
 * the Android def still reads (all format args via Kotlin string templates —
 * no String.format, so the output is locale-stable). English-only on desktop
 * is the accepted locale delta, same as the downloads conveyor's
 * `data_no_media_source_download` copy in DesktopDownloadIntake.
 */
internal object DesktopAdminStatisticsLabels : AdminStatisticsLabelProvider {
    override fun movies(): String = "Movies"
    override fun episodes(): String = "Episodes"
    override fun songs(): String = "Songs"
    override fun addedToday(): String = "Added today"
    override fun addedOneDayAgo(): String = "Added 1 day ago"
    override fun addedDaysAgo(days: Int): String = "Added ${days}d ago"
    override fun addedMonthsAgo(months: Int): String = "Added ${months}mo ago"
    override fun addedYearsAgo(years: Int): String = "Added ${years}y ago"
    override fun daysSincePlay(days: Int): String = "${days}d since play"
    override fun neverPlayed(): String = "Never played"
    override fun playsCount(count: Int): String = "$count plays"
    override fun addedDate(date: String): String = "Added $date"
    override fun addedUnknown(): String = "Added unknown"
    override fun playedDate(date: String): String = "Played $date"
}
