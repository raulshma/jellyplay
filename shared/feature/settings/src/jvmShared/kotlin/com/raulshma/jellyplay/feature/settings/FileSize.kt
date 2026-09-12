package com.raulshma.jellyplay.feature.settings

import java.io.File

/**
 * Sums file lengths under [dir] using an explicit stack (no recursion, so a
 * deep tree cannot overflow the call stack). Symlinks inside the tree are
 * skipped to avoid following circular links, and traversal is capped at
 * [MAX_DEPTH] levels as a guard against pathological trees. The root [dir]
 * itself is never skipped, even if it is a symlink, so a cache/downloads
 * location that the OS symlinks onto external storage still reports its size.
 *
 * Shared by [SettingsViewModel] (cache size on the settings root) and
 * [StorageSettingsViewModel] (full storage breakdown) — previously the body was
 * duplicated in both, so a fix to one could silently diverge from the other.
 */
internal fun directorySizeBytes(dir: File): Long {
    var size = 0L
    // (file, depth, isRoot) — isRoot lets us skip the symlink guard for the
    // seed entry so a legitimately-symlinked root is still measured.
    val stack = ArrayDeque<Triple<File, Int, Boolean>>()
    stack.addLast(Triple(dir, 0, true))
    while (stack.isNotEmpty()) {
        val (current, depth, isRoot) = stack.removeLast()
        if (!isRoot && java.nio.file.Files.isSymbolicLink(current.toPath())) continue
        if (current.isDirectory) {
            if (depth >= MAX_DEPTH) continue
            current.listFiles()?.forEach { file ->
                stack.addLast(Triple(file, depth + 1, false))
            }
        } else if (current.isFile) {
            size += current.length()
        }
    }
    return size
}

private const val MAX_DEPTH = 10
