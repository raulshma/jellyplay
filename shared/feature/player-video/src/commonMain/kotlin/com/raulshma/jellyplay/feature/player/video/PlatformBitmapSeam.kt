package com.raulshma.jellyplay.feature.player.video

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Platform bitmap seam (wave 9A): trickplay thumbnails cross from the
 * platform controllers (`Any?` on the common
 * [VideoPlayerViewModel.loadTrickplayThumbnail]) into the overlay/controls
 * renderers as this type. The androidMain actual is a typealias to
 * `android.graphics.Bitmap`; the jvmMain actual is `java.awt.image
 * .BufferedImage`. Desktop's trickplay controller is a no-op today (no
 * thumbnails), so the JVM render path is exercised only when a desktop
 * thumbnail source lands.
 */
expect class PlatformBitmap

/** Convert a platform bitmap for Compose rendering (Bitmap.asImageBitmap twin). */
expect fun PlatformBitmap.asPlatformImageBitmap(): ImageBitmap
