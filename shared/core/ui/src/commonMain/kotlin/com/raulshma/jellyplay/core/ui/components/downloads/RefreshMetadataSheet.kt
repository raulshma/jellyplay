package com.raulshma.jellyplay.core.ui.components.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MetadataRefreshOption
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.core_cancel
import com.raulshma.jellyplay.core.ui.generated.resources.core_confirm
import com.raulshma.jellyplay.core.ui.generated.resources.core_refresh_metadata_message
import com.raulshma.jellyplay.core.ui.generated.resources.core_refresh_metadata_mode_default
import com.raulshma.jellyplay.core.ui.generated.resources.core_refresh_metadata_mode_default_desc
import com.raulshma.jellyplay.core.ui.generated.resources.core_refresh_metadata_mode_full_validation
import com.raulshma.jellyplay.core.ui.generated.resources.core_refresh_metadata_mode_full_validation_desc
import com.raulshma.jellyplay.core.ui.generated.resources.core_refresh_metadata_mode_replace_all
import com.raulshma.jellyplay.core.ui.generated.resources.core_refresh_metadata_mode_replace_all_desc
import com.raulshma.jellyplay.core.ui.generated.resources.core_refresh_metadata_mode_replace_images
import com.raulshma.jellyplay.core.ui.generated.resources.core_refresh_metadata_mode_replace_images_desc
import com.raulshma.jellyplay.core.ui.generated.resources.core_refresh_metadata_title
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet

/**
 * The shared "Refresh metadata" mode picker (the jellyfin-web Identify-adjacent
 * refresh dialog): one radio row per [MetadataRefreshOption] plus a
 * confirm/cancel pair. Hosted by TvSafeSheet so the same body renders as a
 * bottom sheet on touch/desktop and a focused dialog on TV.
 *
 * Callers own the actual refresh call — the sheet only reports the chosen
 * option through [onConfirm].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshMetadataSheet(
    itemName: String,
    onConfirm: (MetadataRefreshOption) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by rememberSaveable {
        mutableStateOf(MetadataRefreshOption.DEFAULT)
    }

    TvSafeSheet(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.core_refresh_metadata_title),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = stringResource(Res.string.core_refresh_metadata_message, itemName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            @Composable
            fun optionRow(
                option: MetadataRefreshOption,
                label: String,
                description: String,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ShapeCache.smooth12)
                        .clickable { selected = option }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == option, onClick = { selected = option })
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected == option) FontWeight.SemiBold else null,
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            optionRow(
                option = MetadataRefreshOption.DEFAULT,
                label = stringResource(Res.string.core_refresh_metadata_mode_default),
                description = stringResource(Res.string.core_refresh_metadata_mode_default_desc),
            )
            optionRow(
                option = MetadataRefreshOption.FULL_VALIDATION,
                label = stringResource(Res.string.core_refresh_metadata_mode_full_validation),
                description = stringResource(Res.string.core_refresh_metadata_mode_full_validation_desc),
            )
            optionRow(
                option = MetadataRefreshOption.REPLACE_ALL_METADATA,
                label = stringResource(Res.string.core_refresh_metadata_mode_replace_all),
                description = stringResource(Res.string.core_refresh_metadata_mode_replace_all_desc),
            )
            optionRow(
                option = MetadataRefreshOption.REPLACE_IMAGES,
                label = stringResource(Res.string.core_refresh_metadata_mode_replace_images),
                description = stringResource(Res.string.core_refresh_metadata_mode_replace_images_desc),
            )

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.core_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onConfirm(selected) }) {
                    Text(stringResource(Res.string.core_confirm))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
