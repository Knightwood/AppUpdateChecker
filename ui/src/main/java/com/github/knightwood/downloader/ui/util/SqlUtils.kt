package com.github.knightwood.downloader.ui.util

object SqlUtils {
    /**
     * 转义SQL中的通配符
     */
    fun escapeLike(input: String): String {
        return input
            .replace("\\", "\\\\") // 如果转义符本身也要被转义，先处理它
            .replace("%", "\\%")
            .replace("_", "\\_")
    }
}
