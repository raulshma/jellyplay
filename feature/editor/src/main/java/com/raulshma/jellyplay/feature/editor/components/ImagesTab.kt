package com.raulshma.jellyplay.feature.editor.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.tv.TvFocusableGrid
import androidx.compose.material3.AlertDialog
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
import com.raulshma.jellyplay.feature.editor.EditorViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

private val IMAGE_TYPES = listOf("Primary", "Art", "Backdrop", "Banner", "Box", "BoxRear", "Disc", "Logo", "Menu", "Thumb")

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
                Text("Upload")
            }
            FilledTonalButton(onClick = {
                viewModel.loadRemoteImages(null, null, null)
                showBrowseSheet = true
            }) {
                Icon(Tabler.Outline.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Browse Online")
            }
        }

        if (state.imageInfos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("No images available", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Image") },
            text = { Text("Are you sure you want to delete this ${imageInfo.imageType} image?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteImage(
                            imageInfo.imageType,
                            if (imageInfo.imageIndex > 0) imageInfo.imageIndex else null,
                        )
                        showDeleteConfirm = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
            },
        )
    }

    if (showUploadSheet) {
        ImageUploadSheet(
            onDismiss = { showUploadSheet = false },
            onUploadFile = { bytes, imageType ->
                viewModel.uploadImage(bytes, imageType)
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
                contentDescription = imageInfo.imageType,
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
                    text = imageInfo.imageType,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (imageInfo.width > 0 && imageInfo.height > 0) {
                    Text(
                        text = "${imageInfo.width}x${imageInfo.height}",
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
                    contentDescription = "Delete",
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
    onUploadFile: (ByteArray, String) -> Unit,
    onUploadUrl: (String, String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableStateOf(0) }
    var imageUrl by remember { mutableStateOf("") }
    var selectedImageType by remember { mutableStateOf("Primary") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedUri = uri
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Upload Image", style = MaterialTheme.typography.headlineSmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selectedTab == 0, onClick = { selectedTab = 0 }, label = { Text("File") })
                FilterChip(selected = selectedTab == 1, onClick = { selectedTab = 1 }, label = { Text("URL") })
            }

            ImageTypeSelector(
                selected = selectedImageType,
                onSelected = { selectedImageType = it },
            )

            if (selectedTab == 0) {
                selectedUri?.let { uri ->
                    MediaImage(
                        url = uri.toString(),
                        contentDescription = "Preview",
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
                    onClick = { fileLauncher.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (selectedUri != null) "Change File" else "Select Image")
                }
                Button(
                    onClick = {
                        selectedUri?.let { uri ->
                            TODO("Read bytes from URI in activity context - handled by parent")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedUri != null,
                ) { Text("Upload") }
            } else {
                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = { imageUrl = it },
                    label = { Text("Image URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (imageUrl.isNotBlank()) {
                    MediaImage(
                        url = imageUrl,
                        contentDescription = "Preview",
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
                ) { Text("Download from URL") }
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Browse Online Images", style = MaterialTheme.typography.headlineSmall)

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
                        value = selectedProvider ?: "All Providers",
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
                            text = { Text("All Providers") },
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
                    Text("No images found", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            "${currentPage * 50 + 1}-${minOf((currentPage + 1) * 50, total)} of $total",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Row {
                            TextButton(
                                onClick = {
                                    currentPage = (currentPage - 1).coerceAtLeast(0)
                                    onLoadImages(selectedType, selectedProvider, currentPage * 50)
                                },
                                enabled = currentPage > 0,
                            ) { Text("Previous") }
                            TextButton(
                                onClick = {
                                    currentPage++
                                    onLoadImages(selectedType, selectedProvider, currentPage * 50)
                                },
                                enabled = (currentPage + 1) * 50 < total,
                            ) { Text("Next") }
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
                        "${remoteImage.width}x${remoteImage.height}",
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
                    contentDescription = "Download",
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
                contentDescription = imageInfo.imageType,
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
                    contentDescription = "Close",
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
            value = selected.ifBlank { "Select Type" },
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            label = { Text("Image Type") },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (allowBlank) {
                DropdownMenuItem(
                    text = { Text("All Types") },
                    onClick = { onSelected(""); expanded = false },
                )
            }
            IMAGE_TYPES.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type) },
                    onClick = { onSelected(type); expanded = false },
                )
            }
        }
    }
}
