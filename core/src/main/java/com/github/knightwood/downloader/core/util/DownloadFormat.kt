package com.github.knightwood.downloader.core.util

import java.util.Locale

/**
 * 下载界面使用的数值格式化工具
 */

private val SIZE_UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

object DownloadFormat {
    /**
     * 字节数格式化，例如 1.23 MB
     */
    fun formatByteSize(bytes: Long?): String {
        if (bytes == null) return "0 B"
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
    fun formatSpeed(bytesPerSecond: Double?): String {
        if (bytesPerSecond == null || bytesPerSecond.isNaN() || bytesPerSecond <= 0.0) return "--"
        return "${DownloadFormat.formatByteSize(bytesPerSecond.toLong())}/s"
    }

    /**
     * 下载进度百分比，总长度未知时返回0
     *
     * @return 0..1 之间的值
     */
    fun calculateProgress(downloaded: Long, total: Long): Double {
        if (total <= 0L) return 0.0
        return (downloaded.toDouble() / total).coerceIn(0.0, 1.0)
    }

    /**
     * 移除文件名中不合法或会影响路径解析的字符
     */
    fun sanitizeFileName(name: String): String = FileUtil.sanitizeFileName(name)
}
