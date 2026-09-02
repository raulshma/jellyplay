package com.raulshma.jellyplay.feature.editor.components

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.tv.TvFocusableGrid
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.raulshma.jellyplay.core.model.ImageInfo
import com.raulshma.jellyplay.core.model.RemoteImageInfo
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.feature.editor.EditorUiState
import coil3.size.Size as CoilSize
import com.raulshma.jellyplay.feature.editor.EditorFilePicker
import com.raulshma.jellyplay.feature.editor.EditorPickedFile
import com.raulshma.jellyplay.feature.editor.EditorViewModel
import com.raulshma.jellyplay.feature.editor.rememberImageFilePicker
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.editor.generated.resources.Res
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_action_cancel
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_image_type_art
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_image_type_backdrop
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_image_type_banner
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_image_type_box
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_image_type_box_rear
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_image_type_disc
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_image_type_logo
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_image_type_menu
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_image_type_primary
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_image_type_thumb
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_images_all_providers
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_images_all_types
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_images_browse_online
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_images_browse_title
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_images_change_file
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_images_close
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_images_delete_action
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_images_delete_message
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_images_delete_title
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_images_dimensions_format
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_images_download
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_images_download_from_url
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_images_image_type
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_images_image_url
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_images_next
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_images_no_images_found
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_images_none_available
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_images_pagination_format
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_images_preview
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_images_previous
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_images_select_image
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_images_select_type
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_images_source_file
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_images_source_url
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_images_upload
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_images_upload_title

private val IMAGE_TYPES = listOf("Primary", "Art", "Backdrop", "Banner", "Box", "BoxRear", "Disc", "Logo", "Menu", "Thumb")

private fun imageTypeLabelRes(type: String): StringResource = when (type) {
    "Primary" -> Res.string.editor_image_type_primary
    "Art" -> Res.string.editor_image_type_art
    "Backdrop" -> Res.string.editor_image_type_backdrop
    "Banner" -> Res.string.editor_image_type_banner
    "Box" -> Res.string.editor_image_type_box
    "BoxRear" -> Res.string.editor_image_type_box_rear
    "Disc" -> Res.string.editor_image_type_disc
    "Logo" -> Res.string.editor_image_type_logo
    "Menu" -> Res.string.editor_image_type_menu
    "Thumb" -> Res.string.editor_image_type_thumb
    else -> Res.string.editor_image_type_primary
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImagesTab(
    viewModel: EditorViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showUploadSheet by remember { mutableStateOf(false) }
    var showBrowseSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<ImageInfo?>(null) }
    var selectedImageInfo by remember { mutableStateOf<ImageInfo?>(null) }

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            JellyPlayLoadingIndicator()
        }
        return
    }

    val itemId = state.mediaDetail?.item?.id ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = { showUploadSheet = true }) {
                Icon(Tabler.Outline.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(Res.string.editor_images_upload))
            }
            FilledTonalButton(onClick = {
                viewModel.loadRemoteImages(null, null, null)
                showBrowseSheet = true
            }) {
                Icon(Tabler.Outline.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(Res.string.editor_images_browse_online))
            }
        }

        if (state.imageInfos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(Res.string.editor_images_none_available), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            TvFocusableGrid(
                items = state.imageInfos,
                key = { "${it.imageType}-${it.imageIndex}" },
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { _, imageInfo, itemModifier ->
                ImageCard(
                    imageInfo = imageInfo,
                    imageUrl = viewModel.getImageUrl(itemId, imageInfo),
                    onDelete = { showDeleteConfirm = imageInfo },
                    onClick = { selectedImageInfo = imageInfo },
                    modifier = itemModifier,
                )
            }
        }
    }

    showDeleteConfirm?.let { imageInfo ->
        ConfirmDialog(
            title = stringResource(Res.string.editor_images_delete_title),
            message = stringResource(
                Res.string.editor_images_delete_message,
                stringResource(imageTypeLabelRes(imageInfo.imageType)),
            ),
            confirmText = stringResource(Res.string.editor_images_delete_action),
            onConfirm = {
                viewModel.deleteImage(
                    imageInfo.imageType,
                    if (imageInfo.imageIndex > 0) imageInfo.imageIndex else null,
                )
            },
            onDismiss = { showDeleteConfirm = null },
            dismissText = stringResource(Res.string.editor_action_cancel),
            tone = ConfirmTone.DESTRUCTIVE,
            icon = Tabler.Outline.Trash,
        )
    }

    if (showUploadSheet) {
        ImageUploadSheet(
            onDismiss = { showUploadSheet = false },
            onUploadFile = { file, imageType ->
                viewModel.uploadImageFromFile(file, imageType)
                showUploadSheet = false
            },
            onUploadUrl = { url, imageType ->
                viewModel.uploadImageFromUrl(url, imageType)
                showUploadSheet = false
            },
        )
    }

    if (showBrowseSheet) {
        ImageBrowseSheet(
            state = state,
            onDismiss = { showBrowseSheet = false },
            onLoadImages = { type, provider, startIndex ->
                viewModel.loadRemoteImages(type, provider, startIndex)
            },
            onDownload = { imageUrl, imageType ->
                viewModel.uploadImageFromUrl(imageUrl, imageType)
            },
        )
    }

    selectedImageInfo?.let { imageInfo ->
        FullImageDialog(
            imageUrl = viewModel.getFullImageUrl(itemId, imageInfo),
            imageInfo = imageInfo,
            onDismiss = { selectedImageInfo = null },
        )
    }
}

@Composable
private fun ImageCard(
    imageInfo: ImageInfo,
    imageUrl: String,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        onClick = onClick,
    ) {
        Box {
            MediaImage(
                url = imageUrl,
                contentDescription = stringResource(imageTypeLabelRes(imageInfo.imageType)),
                size = CoilSize(512, 512),
                placeholderIcon = Tabler.Outline.Photo,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        when (imageInfo.imageType) {
                            "Backdrop", "Art", "Thumb", "Banner" -> 16f / 9f
                            "Logo" -> 2.39f
                            else -> 2f / 3f
                        }
                    )
                    .clip(ShapeCache.smooth16),
                contentScale = ContentScale.Crop,
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(8.dp),
            ) {
                Text(
                    text = stringResource(imageTypeLabelRes(imageInfo.imageType)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (imageInfo.width > 0 && imageInfo.height > 0) {
                    Text(
                        text = stringResource(Res.string.editor_images_dimensions_format, imageInfo.width, imageInfo.height),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
            ) {
                Icon(
                    Tabler.Outline.Trash,
                    contentDescription = stringResource(Res.string.editor_images_delete_action),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageUploadSheet(
    onDismiss: () -> Unit,
    onUploadFile: (EditorPickedFile, String) -> Unit,
    onUploadUrl: (String, String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableStateOf(0) }
    var imageUrl by remember { mutableStateOf("") }
    var selectedImageType by remember { mutableStateOf("Primary") }
    var selectedFile by remember { mutableStateOf<EditorPickedFile?>(null) }

    val filePicker: EditorFilePicker? = rememberImageFilePicker { file ->
        selectedFile = file
    }

    val isTv = com.raulshma.jellyplay.core.ui.tv.LocalTvMode.current
    if (isTv) {
        com.raulshma.jellyplay.core.ui.components.TvSafeSheet(
            onDismissRequest = onDismiss,
        ) {
            Column(
                // TV sheet hides system bars, so no inset clearance is needed here
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(Res.string.editor_images_upload_title), style = MaterialTheme.typography.headlineSmall)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = selectedTab == 0, onClick = { selectedTab = 0 }, label = { Text(stringResource(Res.string.editor_images_source_file)) })
                    FilterChip(selected = selectedTab == 1, onClick = { selectedTab = 1 }, label = { Text(stringResource(Res.string.editor_images_source_url)) })
                }

                ImageTypeSelector(
                    selected = selectedImageType,
                    onSelected = { selectedImageType = it },
                )

                if (selectedTab == 0) {
                    selectedFile?.previewUrl?.let { previewUrl ->
                        MediaImage(
                            url = previewUrl,
                            contentDescription = stringResource(Res.string.editor_images_preview),
                            size = CoilSize(512, 512),
                            placeholderIcon = Tabler.Outline.Photo,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(ShapeCache.smooth12),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    FilledTonalButton(
                        onClick = { filePicker?.launch() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (selectedFile != null) stringResource(Res.string.editor_images_change_file) else stringResource(Res.string.editor_images_select_image))
                    }
                    Button(
                        onClick = {
                            selectedFile?.let { file ->
                                onUploadFile(file, selectedImageType)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedFile != null,
                    ) { Text(stringResource(Res.string.editor_images_upload)) }
                } else {
                    OutlinedTextField(
                        value = imageUrl,
                        onValueChange = { imageUrl = it },
                        label = { Text(stringResource(Res.string.editor_images_image_url)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    if (imageUrl.isNotBlank()) {
                        MediaImage(
                            url = imageUrl,
                            contentDescription = stringResource(Res.string.editor_images_preview),
                            size = CoilSize(512, 512),
                            placeholderIcon = Tabler.Outline.Photo,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(ShapeCache.smooth12),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Button(
                        onClick = { onUploadUrl(imageUrl, selectedImageType) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = imageUrl.isNotBlank(),
                    ) { Text(stringResource(Res.string.editor_images_download_from_url)) }
                }
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(Res.string.editor_images_upload_title), style = MaterialTheme.typography.headlineSmall)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = selectedTab == 0, onClick = { selectedTab = 0 }, label = { Text(stringResource(Res.string.editor_images_source_file)) })
                    FilterChip(selected = selectedTab == 1, onClick = { selectedTab = 1 }, label = { Text(stringResource(Res.string.editor_images_source_url)) })
                }

                ImageTypeSelector(
                    selected = selectedImageType,
                    onSelected = { selectedImageType = it },
                )

                if (selectedTab == 0) {
                    selectedFile?.previewUrl?.let { previewUrl ->
                        MediaImage(
                            url = previewUrl,
                            contentDescription = stringResource(Res.string.editor_images_preview),
                            size = CoilSize(512, 512),
                            placeholderIcon = Tabler.Outline.Photo,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(ShapeCache.smooth12),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    FilledTonalButton(
                        onClick = { filePicker?.launch() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (selectedFile != null) stringResource(Res.string.editor_images_change_file) else stringResource(Res.string.editor_images_select_image))
                    }
                    Button(
                        onClick = {
                            selectedFile?.let { file ->
                                onUploadFile(file, selectedImageType)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedFile != null,
                    ) { Text(stringResource(Res.string.editor_images_upload)) }
                } else {
                    OutlinedTextField(
                        value = imageUrl,
                        onValueChange = { imageUrl = it },
                        label = { Text(stringResource(Res.string.editor_images_image_url)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    if (imageUrl.isNotBlank()) {
                        MediaImage(
                            url = imageUrl,
                            contentDescription = stringResource(Res.string.editor_images_preview),
                            size = CoilSize(512, 512),
                            placeholderIcon = Tabler.Outline.Photo,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(ShapeCache.smooth12),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Button(
                        onClick = { onUploadUrl(imageUrl, selectedImageType) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = imageUrl.isNotBlank(),
                    ) { Text(stringResource(Res.string.editor_images_download_from_url)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageBrowseSheet(
    state: EditorUiState,
    onDismiss: () -> Unit,
    onLoadImages: (String?, String?, Int?) -> Unit,
    onDownload: (String, String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedType by remember { mutableStateOf<String?>(null) }
    var selectedProvider by remember { mutableStateOf<String?>(null) }
    var currentPage by remember { mutableStateOf(0) }

    val allProvidersLabel = stringResource(Res.string.editor_images_all_providers)

    val isTv = com.raulshma.jellyplay.core.ui.tv.LocalTvMode.current
    if (isTv) {
        com.raulshma.jellyplay.core.ui.components.TvSafeSheet(
            onDismissRequest = onDismiss,
        ) {
            Column(
                // TV sheet hides system bars, so no inset clearance is needed here
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(Res.string.editor_images_browse_title), style = MaterialTheme.typography.headlineSmall)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageTypeSelector(
                        selected = selectedType ?: "",
                        onSelected = {
                            selectedType = it.ifBlank { null }
                            currentPage = 0
                            onLoadImages(it.ifBlank { null }, selectedProvider, 0)
                        },
                        allowBlank = true,
                    )
                }

                val providers = state.imageProviders
                if (providers.size > 1) {
                    var providerExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = providerExpanded,
                        onExpandedChange = { providerExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedProvider ?: allProvidersLabel,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                        )
                        ExposedDropdownMenu(
                            expanded = providerExpanded,
                            onDismissRequest = { providerExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.editor_images_all_providers)) },
                                onClick = {
                                    selectedProvider = null
                                    currentPage = 0
                                    onLoadImages(selectedType, null, 0)
                                    providerExpanded = false
                                },
                            )
                            providers.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.name) },
                                    onClick = {
                                        selectedProvider = provider.name
                                        currentPage = 0
                                        onLoadImages(selectedType, provider.name, 0)
                                        providerExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                val remoteImages = state.remoteImages?.images ?: emptyList()
                if (remoteImages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(Res.string.editor_images_no_images_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(140.dp),
                        modifier = Modifier.height(400.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(remoteImages, key = { it.url }) { remoteImage ->
                            RemoteImageCard(
                                remoteImage = remoteImage,
                                onDownload = {
                                    val type = selectedType ?: "Primary"
                                    onDownload(remoteImage.url, type)
                                },
                            )
                        }
                    }

                    val total = state.remoteImages?.totalRecordCount ?: 0
                    if (total > 50) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(Res.string.editor_images_pagination_format, currentPage * 50 + 1, minOf((currentPage + 1) * 50, total), total),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Row {
                                TextButton(
                                    onClick = {
                                        currentPage = (currentPage - 1).coerceAtLeast(0)
                                        onLoadImages(selectedType, selectedProvider, currentPage * 50)
                                    },
                                    enabled = currentPage > 0,
                                ) { Text(stringResource(Res.string.editor_images_previous)) }
                                TextButton(
                                    onClick = {
                                        currentPage++
                                        onLoadImages(selectedType, selectedProvider, currentPage * 50)
                                    },
                                    enabled = (currentPage + 1) * 50 < total,
                                ) { Text(stringResource(Res.string.editor_images_next)) }
                            }
                        }
                    }
                }
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(Res.string.editor_images_browse_title), style = MaterialTheme.typography.headlineSmall)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageTypeSelector(
                        selected = selectedType ?: "",
                        onSelected = {
                            selectedType = it.ifBlank { null }
                            currentPage = 0
                            onLoadImages(it.ifBlank { null }, selectedProvider, 0)
                        },
                        allowBlank = true,
                    )
                }

                val providers = state.imageProviders
                if (providers.size > 1) {
                    var providerExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = providerExpanded,
                        onExpandedChange = { providerExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedProvider ?: allProvidersLabel,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                        )
                        ExposedDropdownMenu(
                            expanded = providerExpanded,
                            onDismissRequest = { providerExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.editor_images_all_providers)) },
                                onClick = {
                                    selectedProvider = null
                                    currentPage = 0
                                    onLoadImages(selectedType, null, 0)
                                    providerExpanded = false
                                },
                            )
                            providers.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.name) },
                                    onClick = {
                                        selectedProvider = provider.name
                                        currentPage = 0
                                        onLoadImages(selectedType, provider.name, 0)
                                        providerExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                val remoteImages = state.remoteImages?.images ?: emptyList()
                if (remoteImages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(Res.string.editor_images_no_images_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(140.dp),
                        modifier = Modifier.height(400.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(remoteImages, key = { it.url }) { remoteImage ->
                            RemoteImageCard(
                                remoteImage = remoteImage,
                                onDownload = {
                                    val type = selectedType ?: "Primary"
                                    onDownload(remoteImage.url, type)
                                },
                            )
                        }
                    }

                    val total = state.remoteImages?.totalRecordCount ?: 0
                    if (total > 50) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(Res.string.editor_images_pagination_format, currentPage * 50 + 1, minOf((currentPage + 1) * 50, total), total),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Row {
                                TextButton(
                                    onClick = {
                                        currentPage = (currentPage - 1).coerceAtLeast(0)
                                        onLoadImages(selectedType, selectedProvider, currentPage * 50)
                                    },
                                    enabled = currentPage > 0,
                                ) { Text(stringResource(Res.string.editor_images_previous)) }
                                TextButton(
                                    onClick = {
                                        currentPage++
                                        onLoadImages(selectedType, selectedProvider, currentPage * 50)
                                    },
                                    enabled = (currentPage + 1) * 50 < total,
                                ) { Text(stringResource(Res.string.editor_images_next)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteImageCard(
    remoteImage: RemoteImageInfo,
    onDownload: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeCache.smooth12,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box {
            MediaImage(
                url = remoteImage.thumbnailUrl.ifBlank { remoteImage.url },
                contentDescription = null,
                size = CoilSize(512, 512),
                fallbackUrls = if (remoteImage.thumbnailUrl.isNotBlank()) listOf(remoteImage.url) else emptyList(),
                placeholderIcon = Tabler.Outline.Photo,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(ShapeCache.smooth12),
                contentScale = ContentScale.Crop,
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(6.dp),
            ) {
                Text(
                    remoteImage.providerName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (remoteImage.width > 0) {
                    Text(
                        stringResource(Res.string.editor_images_dimensions_format, remoteImage.width, remoteImage.height),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(
                onClick = onDownload,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp),
            ) {
                Icon(
                    Tabler.Outline.Download,
                    contentDescription = stringResource(Res.string.editor_images_download),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun FullImageDialog(
    imageUrl: String,
    imageInfo: ImageInfo,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            MediaImage(
                url = imageUrl,
                contentDescription = stringResource(imageTypeLabelRes(imageInfo.imageType)),
                size = CoilSize(1024, 1024),
                placeholderIcon = Tabler.Outline.Photo,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    ),
                contentScale = ContentScale.Fit,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(
                    Tabler.Outline.X,
                    contentDescription = stringResource(Res.string.editor_images_close),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageTypeSelector(
    selected: String,
    onSelected: (String) -> Unit,
    allowBlank: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.ifBlank { stringResource(Res.string.editor_images_select_type) },
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            label = { Text(stringResource(Res.string.editor_images_image_type)) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (allowBlank) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.editor_images_all_types)) },
                    onClick = { onSelected(""); expanded = false },
                )
            }
            IMAGE_TYPES.forEach { type ->
                DropdownMenuItem(
                    text = { Text(stringResource(imageTypeLabelRes(type))) },
                    onClick = { onSelected(type); expanded = false },
                )
            }
        }
    }
}
