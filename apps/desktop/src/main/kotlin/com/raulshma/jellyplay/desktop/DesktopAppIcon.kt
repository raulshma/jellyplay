package com.raulshma.jellyplay.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

/**
 * Wave 12A: runtime-loadable app icon (window title-bar + system-tray).
 *
 * The bytes are the SAME deterministic PNG the packaging generator task draws
 * (`generatePackagingIcons` in build.gradle.kts → packaging/icons/JellyPlay-linux.png;
 * byte-identical copy committed at src/main/resources/branding/jellyplay-icon.png).
 * Packaging-only assets are NOT on the runtime classpath, so a runtime copy is
 * required. If the resource is ever missing or undecodable this degrades to null
 * and callers skip the window icon / tray rather than failing the boot.
 */
private const val ICON_RESOURCE = "/branding/jellyplay-icon.png"

/** Returns a painter for the app icon, or null when it cannot be loaded. */
fun desktopAppIconOrNull(): Painter? {
    val bytes = runCatching {
        object {}.javaClass.getResourceAsStream(ICON_RESOURCE)?.use { stream -> stream.readBytes() }
    }.getOrNull() ?: return null
    if (bytes.isEmpty()) return null
    // Decoded via skia (cmp's public decode path at 1.11.1; the byte[]-based
    // androidx helper exists in bytecode but is hidden from Kotlin resolution).
    val bitmap = runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
        ?: return null
    return ImageBitmapPainter(bitmap)
}

/**
 * Tray-availability probe. CMP 1.11.1's own `isTraySupported()` lives in the
 * ui-desktop jar but its Kotlin metadata keeps it internal, so the compiler
 * refuses it — probe AWT directly with equivalent semantics instead: tray icons
 * need a non-headless environment AND a SystemTray the OS actually exposes.
 */
fun systemTrayAvailable(): Boolean = runCatching {
    !java.awt.GraphicsEnvironment.isHeadless() && java.awt.SystemTray.isSupported()
}.getOrDefault(false)

/**
 * Minimal Painter over a decoded ImageBitmap: composes 1:1 at the bitmap's
 * pixel size — tray/AWT consumers pick their target sizes themselves.
 */
private class ImageBitmapPainter(private val bitmap: androidx.compose.ui.graphics.ImageBitmap) : Painter() {
    override val intrinsicSize: Size = Size(bitmap.width.toFloat(), bitmap.height.toFloat())

    override fun DrawScope.onDraw() {
        drawImage(bitmap)
    }
}
