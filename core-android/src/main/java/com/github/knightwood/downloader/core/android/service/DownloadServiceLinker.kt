package com.github.knightwood.downloader.core.android.service

import android.os.Binder
import com.github.knightwood.downloader.core.android.model.AndroidDownloadParams
import com.github.knightwood.downloader.core.android.model.AndroidDownloadTaskSaveState
import com.github.knightwood.downloader.core.task.DownloadTask

abstract class DownloadServiceLinker : Binder() {
    abstract val service: DownloadService

    abstract fun submitTask(
        id: Int,
        conf: AndroidDownloadParams,
    ):DownloadTask

    abstract fun findTask(id: Int): DownloadTask?

    abstract fun cancelTask(id: Int)

    abstract fun stopService()

    abstract suspend fun saveState(): AndroidDownloadTaskSaveState

    abstract suspend fun restoreState(state: AndroidDownloadTaskSaveState)
}
