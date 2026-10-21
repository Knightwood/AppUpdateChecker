package com.github.knightwood.downloader.core.model

class TaskSchedulerSaveState(
    val pending : IntArray,
    val all:List<DownloadTaskSaveState>
)
