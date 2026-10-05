package com.raulshma.jellyplay.feature.library.components

import androidx.compose.runtime.Composable
import com.raulshma.jellyplay.core.model.GroupBy
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.library.generated.resources.Res
import com.raulshma.jellyplay.feature.library.generated.resources.library_group_by_genre
import com.raulshma.jellyplay.feature.library.generated.resources.library_group_by_name
import com.raulshma.jellyplay.feature.library.generated.resources.library_group_by_none
import com.raulshma.jellyplay.feature.library.generated.resources.library_group_by_type
import com.raulshma.jellyplay.feature.library.generated.resources.library_group_by_year

/** Display label for a group-by mode. */
@Composable
internal fun groupByLabel(groupBy: GroupBy): String = when (groupBy) {
    GroupBy.NONE -> stringResource(Res.string.library_group_by_none)
    GroupBy.NAME -> stringResource(Res.string.library_group_by_name)
    GroupBy.TYPE -> stringResource(Res.string.library_group_by_type)
    GroupBy.GENRE -> stringResource(Res.string.library_group_by_genre)
    GroupBy.YEAR -> stringResource(Res.string.library_group_by_year)
}
