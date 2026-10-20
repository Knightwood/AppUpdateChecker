package com.github.knightwood.downloader.core.util

object HttpConstant {
    /**
     * Http timeout(ms)
     */
    const val HTTP_TIME_OUT = 30_000
    /** 分块下载相关 */
    const val DEFAULT_CHUNK_COUNT = 4
    const val MIN_CHUNK_SIZE = 1L * 1024 * 1024   // <1MB 不分块
    const val MAX_REDIRECTS = 5
    const val COPY_BUFFER_SIZE = 8192

}
