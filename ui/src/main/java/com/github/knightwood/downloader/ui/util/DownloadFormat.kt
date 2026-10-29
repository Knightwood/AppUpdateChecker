package com.github.knightwood.downloader.ui.util

import java.util.Locale

/**
 * 下载界面使用的数值格式化工具
 */

private val SIZE_UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

/**
 * 字节数格式化，例如 1.23 MB
 */
fun formatByteSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024.0 && index < SIZE_UNITS.lastIndex) {
        value /= 1024.0
        index++
    }
    return if (index == 0) {
        "$bytes ${SIZE_UNITS[0]}"
    } else {
        String.format(Locale.US, "%.2f %s", value, SIZE_UNITS[index])
    }
}

/**
 * 下载速度格式化，速度未知（NaN）时返回 "--"
 */
fun formatSpeed(bytesPerSecond: Double): String {
    if (bytesPerSecond.isNaN() || bytesPerSecond <= 0.0) return "--"
    return "${formatByteSize(bytesPerSecond.toLong())}/s"
}

/**
 * 下载进度百分比，总长度未知时返回0
 *
 * @return 0..1 之间的值
 */
fun calculateProgress(downloaded: Long, total: Long): Float {
    if (total <= 0L) return 0f
    return (downloaded.toDouble() / total).coerceIn(0.0, 1.0).toFloat()
}

/**
 * 移除文件名中不合法或会影响路径解析的字符
 */
fun sanitizeFileName(name: String): String {
    val cleaned = name
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
        .trim()
    return cleaned.ifEmpty { "download" }
}
