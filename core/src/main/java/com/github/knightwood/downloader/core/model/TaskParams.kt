package com.github.knightwood.downloader.core.model

/**
 * 下载任务配置
 */
data class TaskParams(
    val md5: String? = null,
    val url: String,
)
