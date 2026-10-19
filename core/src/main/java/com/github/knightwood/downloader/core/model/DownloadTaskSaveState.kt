package com.github.knightwood.downloader.core.model

/**
 * 用于导出当前下载任务的快照
 */
data class DownloadTaskSaveState(
    val id: Int,
    val conf: DownloadTaskConf,
    val downloadProgress: DownloadStatus.DOWNLOADING? = null,
)
