package com.raulshma.jellyplay.feature.settings

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Clock
import com.composables.icons.tabler.outline.Folder
import com.composables.icons.tabler.outline.LayersLinked
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.PlayerSkipForward
import com.composables.icons.tabler.outline.Wand
import com.raulshma.jellyplay.core.model.NewsletterSectionType
import org.jetbrains.compose.resources.StringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_activity_log
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_activity_log_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_continue_watching
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_continue_watching_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_curated_picks
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_curated_picks_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_library_stats
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_library_stats_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_next_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_next_up_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_recently_added
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_recently_added_desc

/**
 * [NewsletterSectionType]'s presentation vocabulary, following the
 * [HomeSectionType] precedent (descriptor-carried name/description +
 * `homeSectionIcon()` in core/ui): one exhaustive mapping per facet — label,
 * description, icon — instead of three parallel inline `when` chains at the
 * render site.
 *
 * The shape differs from HomeSectionType by necessity: those strings are
 * hardcoded English on the core-model descriptor, while the newsletter strings
 * are locale-resolved Compose resources owned by this module (and only this
 * module renders them), so the label/description facets stay `StringResource`s
 * resolved at render time. The icon facet mirrors `homeSectionIcon()`'s
 * non-composable exhaustive function. Adding a [NewsletterSectionType]
 * constant without extending all three is a compile error here.
 */
internal val NewsletterSectionType.labelRes: StringResource
    get() = when (this) {
        NewsletterSectionType.RECENTLY_ADDED -> Res.string.settings_newsletter_recently_added
        NewsletterSectionType.ACTIVITY_DIGEST -> Res.string.settings_newsletter_activity_log
        NewsletterSectionType.LIBRARY_STATS -> Res.string.settings_newsletter_library_stats
        NewsletterSectionType.CONTINUE_WATCHING -> Res.string.settings_newsletter_continue_watching
        NewsletterSectionType.NEXT_UP -> Res.string.settings_newsletter_next_up
        NewsletterSectionType.CURATED_PICKS -> Res.string.settings_newsletter_curated_picks
    }

/** One-line explanation shown by the newsletter reorder rows. */
internal val NewsletterSectionType.descriptionRes: StringResource
    get() = when (this) {
        NewsletterSectionType.RECENTLY_ADDED -> Res.string.settings_newsletter_recently_added_desc
        NewsletterSectionType.ACTIVITY_DIGEST -> Res.string.settings_newsletter_activity_log_desc
        NewsletterSectionType.LIBRARY_STATS -> Res.string.settings_newsletter_library_stats_desc
        NewsletterSectionType.CONTINUE_WATCHING -> Res.string.settings_newsletter_continue_watching_desc
        NewsletterSectionType.NEXT_UP -> Res.string.settings_newsletter_next_up_desc
        NewsletterSectionType.CURATED_PICKS -> Res.string.settings_newsletter_curated_picks_desc
    }

/** A stable [ImageVector] for each section type (newsletter reorder rows). */
internal fun newsletterSectionIcon(type: NewsletterSectionType): ImageVector = when (type) {
    NewsletterSectionType.CONTINUE_WATCHING -> Tabler.Outline.PlayerPlay
    NewsletterSectionType.NEXT_UP -> Tabler.Outline.PlayerSkipForward
    NewsletterSectionType.RECENTLY_ADDED -> Tabler.Outline.Clock
    NewsletterSectionType.LIBRARY_STATS -> Tabler.Outline.LayersLinked
    NewsletterSectionType.CURATED_PICKS -> Tabler.Outline.Wand
    NewsletterSectionType.ACTIVITY_DIGEST -> Tabler.Outline.Folder
}
