package com.raulshma.jellyplay.feature.player.video.engine

import java.io.File

/**
 * The one write site for mpv's user `input.conf` (issue #26, the
 * `mpvInputConf` pref's engine application): syncs the user's raw
 * key-bindings text into an mpv config directory so the mpv engines can load
 * it. Both mpv hosts ride this one helper — Android's `MpvPlayerEngine`
 * writes it into the `config-dir` mpv already reads (`config=yes`,
 * `<filesDir>/mpv`), and the desktop factory writes it under the app data dir
 * and feeds the path to `MpvDesktopEngine`'s `input-conf=` option (its mpv
 * runs `config=no`) — so the file discipline cannot drift.
 *
 * mpv reads `input.conf` ONCE, during `mpv_initialize` — a mid-session pref
 * edit does NOT hot-reload (no live keybinding reload is attempted); it lands
 * on the next engine rebuild / playback session (the EngineConfigBuilder fold
 * carries the text so every later engine sees it).
 *
 * Public (not internal) because the desktop adapter (apps/desktop) shares it
 * — the [MpvConfigMapping] precedent. Consumers stay the two mpv engine
 * surfaces and their tests; not a stable API surface.
 */
public object MpvInputConfFile {

    /** The file name inside the mpv config directory. */
    public const val FILE_NAME: String = "input.conf"

    /**
     * The outcome of [sync]: [Synced] carries the file's absolute path (the
     * value an `input-conf=` option wants), [Cleared] means the pref is blank
     * and any stale file was removed (nothing to load), [Failed] means the
     * write could not land (log + skip — playback continues without the
     * custom bindings).
     */
    public sealed interface SyncResult {
        public data class Synced(val path: String) : SyncResult
        public data object Cleared : SyncResult
        public data object Failed : SyncResult
    }

    /**
     * Makes [configDir] reflect [text]: non-blank text is written
     * atomically (temp file + rename, with a delete-then-rename fallback for
     * the Windows rename-over-existing case) as `input.conf`; blank/null text
     * removes a stale `input.conf` so an old keymap cannot outlive the pref
     * being cleared. Directory creation is best-effort and folded into the
     * same failure containment.
     */
    public fun sync(configDir: File, text: String?): SyncResult {
        val target = File(configDir, FILE_NAME)
        val content = text?.takeIf { it.isNotBlank() }
        if (content == null) {
            return if (!target.exists() || target.delete()) SyncResult.Cleared else SyncResult.Failed
        }
        return try {
            configDir.mkdirs()
            val tmp = File(configDir, "$FILE_NAME.tmp")
            tmp.writeText(content)
            if (tmp.renameTo(target) || (target.delete() && tmp.renameTo(target))) {
                SyncResult.Synced(target.absolutePath)
            } else {
                tmp.delete()
                SyncResult.Failed
            }
        } catch (_: Exception) {
            SyncResult.Failed
        }
    }
}
