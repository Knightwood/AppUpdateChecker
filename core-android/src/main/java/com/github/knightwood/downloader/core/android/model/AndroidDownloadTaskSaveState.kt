package com.github.knightwood.downloader.core.android.model

import com.github.knightwood.downloader.core.model.TaskSchedulerSaveState

class AndroidDownloadTaskSaveState(
    val infoMap: Map<Int, AndroidDownloadParams> = mapOf(),
    val taskSchedulerSaveState: TaskSchedulerSaveState
)
