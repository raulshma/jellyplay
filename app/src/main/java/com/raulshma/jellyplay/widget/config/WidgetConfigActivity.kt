package com.raulshma.jellyplay.widget.config

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
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
import androidx.compose.material3.Surface
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
import com.raulshma.jellyplay.widget.LibraryRecommendationsWidget
import com.raulshma.jellyplay.widget.SeerrRecommendationsWidget
import com.raulshma.jellyplay.widget.WidgetWorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Configuration activity for the Library Recommendations widget. The
 * AOSP AppWidget framework launches `android:configure` activities
 * with a fixed `APPWIDGET_CONFIGURE` action, so we keep one activity
 * per widget kind rather than a single alias-based dispatcher.
 */
@AndroidEntryPoint
class LibraryWidgetConfigActivity : ComponentActivity() {

    @Inject lateinit var widgetWorkScheduler: WidgetWorkScheduler

    private val viewModel: WidgetConfigViewModel by viewModels()
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

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
                        titleRes = R.string.widget_library_recommendations_title,
                        viewModel = viewModel,
                        isLibraryKind = true,
                        onSave = { saveAndFinish() },
                    )
                }
            }
        }
    }

    private fun saveAndFinish() {
        lifecycleScope.launch {
            val manager = AppWidgetManager.getInstance(this@LibraryWidgetConfigActivity)
            LibraryRecommendationsWidget.updateAppWidget(this@LibraryWidgetConfigActivity, manager, widgetId)
            manager.notifyAppWidgetViewDataChanged(widgetId, R.id.lr_widget_grid)
            widgetWorkScheduler.refreshLibraryNow()
            finish()
        }
    }
}

/**
 * Configuration activity for the Seerr Recommendations widget.
 */
@AndroidEntryPoint
class SeerrWidgetConfigActivity : ComponentActivity() {

    @Inject lateinit var widgetWorkScheduler: WidgetWorkScheduler

    private val viewModel: WidgetConfigViewModel by viewModels()
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

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
                        titleRes = R.string.widget_seerr_recommendations_title,
                        viewModel = viewModel,
                        isLibraryKind = false,
                        onSave = { saveAndFinish() },
                    )
                }
            }
        }
    }

    private fun saveAndFinish() {
        lifecycleScope.launch {
            val manager = AppWidgetManager.getInstance(this@SeerrWidgetConfigActivity)
            SeerrRecommendationsWidget.updateAppWidget(this@SeerrWidgetConfigActivity, manager, widgetId)
            manager.notifyAppWidgetViewDataChanged(widgetId, R.id.sr_widget_grid)
            widgetWorkScheduler.refreshSeerrNow()
            finish()
        }
    }
}

@Composable
private fun ConfigScreen(
    titleRes: Int,
    viewModel: WidgetConfigViewModel,
    isLibraryKind: Boolean,
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
        ) {
            if (isLibraryKind) {
                items(LibraryOptions) { option ->
                    OptionRow(
                        title = stringResource(option.titleRes),
                        description = stringResource(option.descriptionRes),
                        selected = state.librarySource == option.source,
                    ) { viewModel.selectLibrarySource(option.source) }
                }
            } else {
                items(SeerrOptions) { option ->
                    OptionRow(
                        title = stringResource(option.titleRes),
                        description = stringResource(option.descriptionRes),
                        selected = state.seerrSource == option.source,
                    ) { viewModel.selectSeerrSource(option.source) }
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
