package com.raulshma.jellyplay.core.designsystem.theme

import android.os.Build
import android.util.LruCache
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
internal actual fun dynamicPlatformColorScheme(darkTheme: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    // darkTheme must key the remember: the pre-KMP code picked
    // dynamicDark/LightColorScheme inline on every recomposition, so a dark
    // mode toggle swapped schemes immediately. Keying only on context would
    // freeze the first-composed scheme until Activity recreation.
    return remember(context, darkTheme) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
}

private val colorCache = LruCache<String, ArtworkColors>(100)

@Composable
actual fun rememberArtworkColors(imageUrl: String?): ArtworkColors? {
    val context = LocalContext.current
    // Extraction results land here; the RETURNED palette below never drops to
    // null while a new image extracts. The palette drives ArtworkThemeWrapper's
    // whole MaterialTheme (and the home backdrop/background pipelines), so a
    // null window re-renders the entire app in the DEFAULT color scheme for
    // the extraction's duration — a full-screen theme flash on every hero
    // rotation or data refresh that lands on an uncached image.
    var extractedColors by remember { mutableStateOf<ArtworkColors?>(null) }
    // The returned palette: the new URL's cached swatches the moment they're
    // known (cache hits swap in the same frame, no effect round-trip), the
    // in-flight extraction's result once it lands, and the PREVIOUS artwork's
    // palette as the hold while that extraction runs or when it fails. A
    // null/blank URL is an explicit reset (theming off / no artwork), not a
    // hold.
    val cachedNow = imageUrl?.let { colorCache.get(it) }
    val artworkColors = when {
        imageUrl.isNullOrBlank() -> null
        cachedNow != null -> cachedNow
        else -> extractedColors
    }
    val loader = coil3.SingletonImageLoader.get(context)

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrBlank()) return@LaunchedEffect
        if (colorCache.get(imageUrl) != null) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .size(Size(64, 64))
                    .allowHardware(false)
                    // Isolate this Palette decode in its own cache slot so it
                    // can't evict the larger display bitmap a live AsyncImage is
                    // drawing (eviction lets the BitmapPool recycle it,
                    // crashing onDraw with "Canvas: trying to use a recycled
                    // bitmap"). See WidgetImageLoader for the same fix class.
                    .memoryCacheKey("$imageUrl#palette-artwork")
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.image as? coil3.BitmapImage)?.bitmap
                        ?: return@withContext
                    // At 64px, ArtworkColorExtractor.scaleForPalette returns the
                    // same instance (<=128px), so it won't recycle the shared
                    // cache bitmap. The cache-key isolation above is what keeps
                    // this request from evicting a display painter's bitmap.
                    val colors = AndroidArtworkPalette.extractColors(bitmap)
                    colorCache.put(imageUrl, colors)
                    // A rotation that raced this extraction cancelled this
                    // effect; adopting the finished palette would pin the
                    // outgone artwork's colors over the new one until the next
                    // rotation. An isActive read, not ensureActive — the
                    // surrounding catch swallows exceptions and must not
                    // swallow a thrown CancellationException.
                    if (isActive) extractedColors = colors
                }
            } catch (_: Exception) {
                // Extraction failure keeps the held palette — dropping to null
                // (and with it the themed scheme) is the flash this retain
                // exists to prevent. A later visit to this URL retries.
            }
        }
    }

    return artworkColors
}
