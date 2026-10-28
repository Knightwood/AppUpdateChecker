package com.github.knightwood.downloader.core.model

/**
 * @param pending 当前正在排队下载的任务id
 * @param all 所有任务的状态
 */
class TaskSchedulerSaveState(
    val pending : IntArray,
    val all:List<DownloadTaskSaveState>
)
