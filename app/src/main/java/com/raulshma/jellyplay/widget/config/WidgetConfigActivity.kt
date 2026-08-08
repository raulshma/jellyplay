package com.raulshma.jellyplay.widget.config

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.designsystem.theme.JellyPlayTheme
import com.raulshma.jellyplay.core.model.LibraryRecommendationsSource
import com.raulshma.jellyplay.core.model.SeerrWidgetSource
import com.raulshma.jellyplay.core.model.WidgetConfig
import com.raulshma.jellyplay.widget.ContinueWatchingWidget
import com.raulshma.jellyplay.widget.LibraryRecommendationsWidget
import com.raulshma.jellyplay.widget.NowPlayingWidget
import com.raulshma.jellyplay.widget.SeerrRecommendationsWidget
import com.raulshma.jellyplay.widget.WidgetWorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shared scaffold for the per-kind widget configuration activities. The AOSP
 * AppWidget framework launches each `android:configure` activity with a fixed
 * `APPWIDGET_CONFIGURE` action and an `EXTRA_APPWIDGET_ID` extra; every kind
 * handled that identically (extract id → bail if invalid → init VM → set OK
 * result → mount the themed [ConfigScreen]), so the base owns it once.
 * Subclasses implement [saveAndFinish] for their kind-specific refresh side
 * effects (push RemoteViews, notify grid, kick a scheduler, etc.).
 */
abstract class BaseWidgetConfigActivity : ComponentActivity() {

    protected val viewModel: WidgetConfigViewModel by viewModels()
    protected var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
        private set

    /** String resource shown as the config screen subtitle. */
    protected abstract val titleRes: Int

    /** Which options section [ConfigScreen] renders. */
    protected abstract val kind: WidgetKind

    /** Called when the user taps Save — push RemoteViews, notify grids, refresh. */
    protected abstract suspend fun saveAndFinish()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        viewModel.initWidgetId(widgetId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        setContent {
            JellyPlayTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ConfigScreen(
                        titleRes = titleRes,
                        viewModel = viewModel,
                        kind = kind,
                        onSave = { lifecycleScope.launch { saveAndFinish() } },
                    )
                }
            }
        }
    }
}

/**
 * Configuration activity for the Library Recommendations widget.
 */
@AndroidEntryPoint
class LibraryWidgetConfigActivity : BaseWidgetConfigActivity() {

    @Inject lateinit var widgetWorkScheduler: WidgetWorkScheduler

    override val titleRes: Int = R.string.widget_library_recommendations_title
    override val kind: WidgetKind = WidgetKind.LIBRARY

    override suspend fun saveAndFinish() {
        val manager = AppWidgetManager.getInstance(this)
        LibraryRecommendationsWidget.updateAppWidget(this, manager, widgetId)
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.lr_widget_grid)
        widgetWorkScheduler.refreshLibraryNow()
        finish()
    }
}

/**
 * Configuration activity for the Seerr Recommendations widget.
 */
@AndroidEntryPoint
class SeerrWidgetConfigActivity : BaseWidgetConfigActivity() {

    @Inject lateinit var widgetWorkScheduler: WidgetWorkScheduler

    override val titleRes: Int = R.string.widget_seerr_recommendations_title
    override val kind: WidgetKind = WidgetKind.SEERR

    override suspend fun saveAndFinish() {
        val manager = AppWidgetManager.getInstance(this)
        SeerrRecommendationsWidget.updateAppWidget(this, manager, widgetId)
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.sr_widget_grid)
        widgetWorkScheduler.refreshSeerrNow()
        finish()
    }
}

/**
 * Configuration activity for the Continue Watching widget. Configures the
 * max item count; the shelf itself is fed by the playback shelf sync, so
 * no [WidgetWorkScheduler] refresh is triggered.
 */
@AndroidEntryPoint
class ContinueWatchingWidgetConfigActivity : BaseWidgetConfigActivity() {

    override val titleRes: Int = R.string.widget_continue_watching_label
    override val kind: WidgetKind = WidgetKind.CONTINUE_WATCHING

    override suspend fun saveAndFinish() {
        val manager = AppWidgetManager.getInstance(this)
        ContinueWatchingWidget.updateWidget(this, manager, widgetId)
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.cw_widget_list)
        finish()
    }
}

/**
 * Configuration activity for the Now Playing widget. Configures artwork /
 * progress visibility; [NowPlayingWidget.updateAppWidget] renders directly
 * via RemoteViews, so no grid notify or scheduler refresh is needed.
 */
@AndroidEntryPoint
class NowPlayingWidgetConfigActivity : BaseWidgetConfigActivity() {

    override val titleRes: Int = R.string.widget_now_playing_label
    override val kind: WidgetKind = WidgetKind.NOW_PLAYING

    override suspend fun saveAndFinish() {
        val manager = AppWidgetManager.getInstance(this)
        NowPlayingWidget.updateAppWidget(this, manager, widgetId)
        finish()
    }
}

@Composable
private fun ConfigScreen(
    titleRes: Int,
    viewModel: WidgetConfigViewModel,
    kind: WidgetKind,
    onSave: () -> Unit,
) {
    val state by viewModel.getWidgetConfig().collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            text = stringResource(R.string.widget_config_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (kind) {
                WidgetKind.LIBRARY -> items(LibraryOptions, key = { option -> option.source.name }) { option ->
                    OptionRow(
                        title = stringResource(option.titleRes),
                        description = stringResource(option.descriptionRes),
                        selected = state.librarySource == option.source,
                    ) { viewModel.selectLibrarySource(option.source) }
                }

                WidgetKind.SEERR -> items(SeerrOptions, key = { option -> option.source.name }) { option ->
                    OptionRow(
                        title = stringResource(option.titleRes),
                        description = stringResource(option.descriptionRes),
                        selected = state.seerrSource == option.source,
                    ) { viewModel.selectSeerrSource(option.source) }
                }

                WidgetKind.CONTINUE_WATCHING -> item {
                    ContinueWatchingCountRow(
                        count = state.continueWatchingItemCount,
                        onCountChange = { viewModel.selectContinueWatchingCount(it) },
                    )
                }

                WidgetKind.NOW_PLAYING -> {
                    item {
                        SwitchRow(
                            title = stringResource(R.string.widget_np_show_artwork),
                            checked = state.nowPlayingShowArtwork,
                        ) { viewModel.setNowPlayingShowArtwork(it) }
                    }
                    item {
                        SwitchRow(
                            title = stringResource(R.string.widget_np_show_progress),
                            checked = state.nowPlayingShowProgress,
                        ) { viewModel.setNowPlayingShowProgress(it) }
                    }
                }
            }
        }
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.widget_config_save))
        }
    }
}

@Composable
private fun ContinueWatchingCountRow(
    count: Int,
    onCountChange: (Int) -> Unit,
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.widget_cw_item_count),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Slider(
            value = count.toFloat(),
            onValueChange = { onCountChange(it.toInt()) },
            valueRange = WidgetConfig.MIN_CONTINUE_WATCHING_ITEM_COUNT.toFloat()..
                WidgetConfig.MAX_CONTINUE_WATCHING_ITEM_COUNT.toFloat(),
            steps = WidgetConfig.MAX_CONTINUE_WATCHING_ITEM_COUNT -
                WidgetConfig.MIN_CONTINUE_WATCHING_ITEM_COUNT - 1,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(ShapeCache.smooth12)
            .background(
                if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else Color.Transparent,
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun OptionRow(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val highlight = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        Color.Transparent
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(ShapeCache.smooth12)
            .background(highlight),
        color = Color.Transparent,
        onClick = onSelect,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class LibraryOption(
    val source: LibraryRecommendationsSource,
    val titleRes: Int,
    val descriptionRes: Int,
)

private data class SeerrOption(
    val source: SeerrWidgetSource,
    val titleRes: Int,
    val descriptionRes: Int,
)

private val LibraryOptions = listOf(
    LibraryOption(
        source = LibraryRecommendationsSource.SIMILAR_TO_RECENT,
        titleRes = R.string.widget_config_source_similar,
        descriptionRes = R.string.widget_config_source_similar_description,
    ),
    LibraryOption(
        source = LibraryRecommendationsSource.LATEST,
        titleRes = R.string.widget_config_source_latest,
        descriptionRes = R.string.widget_config_source_latest_description,
    ),
    LibraryOption(
        source = LibraryRecommendationsSource.FAVORITES,
        titleRes = R.string.widget_config_source_favorites,
        descriptionRes = R.string.widget_config_source_favorites_description,
    ),
    LibraryOption(
        source = LibraryRecommendationsSource.SURPRISE_ME,
        titleRes = R.string.widget_config_source_surprise_me,
        descriptionRes = R.string.widget_config_source_surprise_me_description,
    ),
)

private val SeerrOptions = listOf(
    SeerrOption(
        source = SeerrWidgetSource.TRENDING,
        titleRes = R.string.widget_config_source_trending,
        descriptionRes = R.string.widget_config_source_trending_description,
    ),
    SeerrOption(
        source = SeerrWidgetSource.POPULAR_MOVIES,
        titleRes = R.string.widget_config_source_popular_movies,
        descriptionRes = R.string.widget_config_source_popular_movies_description,
    ),
    SeerrOption(
        source = SeerrWidgetSource.POPULAR_TV,
        titleRes = R.string.widget_config_source_popular_tv,
        descriptionRes = R.string.widget_config_source_popular_tv_description,
    ),
    SeerrOption(
        source = SeerrWidgetSource.UPCOMING_MOVIES,
        titleRes = R.string.widget_config_source_upcoming_movies,
        descriptionRes = R.string.widget_config_source_upcoming_movies_description,
    ),
    SeerrOption(
        source = SeerrWidgetSource.UPCOMING_TV,
        titleRes = R.string.widget_config_source_upcoming_tv,
        descriptionRes = R.string.widget_config_source_upcoming_tv_description,
    ),
)
