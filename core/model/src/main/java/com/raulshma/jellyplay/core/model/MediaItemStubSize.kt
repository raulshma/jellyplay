package com.raulshma.jellyplay.core.model

private val SIZE_TEXT_REGEX = Regex("(\\d+\\.?\\d*)\\s*(B|KB|MB|GB|TB)")

val MediaItemStub.sortSizeBytes: Long
    get() {
        val match = SIZE_TEXT_REGEX.find(sizeText) ?: return 0L
        val num = match.groupValues[1].toDoubleOrNull() ?: return 0L
        return when (match.groupValues[2]) {
            "TB" -> (num * 1024 * 1024 * 1024 * 1024).toLong()
            "GB" -> (num * 1024 * 1024 * 1024).toLong()
            "MB" -> (num * 1024 * 1024).toLong()
            "KB" -> (num * 1024).toLong()
            else -> num.toLong()
        }
    }
