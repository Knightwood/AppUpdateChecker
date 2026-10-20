package com.github.knightwood.downloader.core.model

/**
 * 用于导出当前下载任务的快照
 *
 * @property id 任务id
 * @property conf 下载配置
 * @property progress 下载进度
 * @property finalState 最新状态
 */
data class DownloadTaskSaveState(
    val id: Int,
    val conf: DownloadTaskConf,
    val progress: DownloadStatus.DOWNLOADING? = null,
    val finalState: DownloadStatus,
)
