package com.github.knightwood.downloader.core.model

/**
 * 下载任务配置
 */
data class TaskParams(
    val md5: String? = null,
    val url: String,
    /**
     * true: 执行下载之前删除残留文件
     */
    var deleteLastFile: Boolean = false,
)
