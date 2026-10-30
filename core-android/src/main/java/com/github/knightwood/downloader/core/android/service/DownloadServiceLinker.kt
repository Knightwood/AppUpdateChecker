package com.github.knightwood.downloader.core.android.service

import android.os.Binder
import com.github.knightwood.downloader.core.android.model.AndroidDownloadParams
import com.github.knightwood.downloader.core.android.model.AndroidDownloadTaskSaveState
import com.github.knightwood.downloader.core.model.DownloadStatus
import com.github.knightwood.downloader.core.model.DownloadTaskSaveState
import com.github.knightwood.downloader.core.task.DownloadTask

abstract class DownloadServiceLinker : Binder() {
    abstract val service: DownloadService

    /**
     * 提交下载任务,
     * 如果是新任务,则生成DownloadTask实例,提交到下载任务管理器等待下载
     * 如果要提交的任务已存在,则使任务重新排队等待下载
     *
     * @param id 任务id
     * @param conf 下载任务配置
     * @param progress 下载进度, 默认为null. 用于指定任务的初始进度
     */
    abstract fun submitTask(
        id: Int,
        conf: AndroidDownloadParams,
        progress: DownloadStatus.DOWNLOADING? = null,
    ): DownloadTask

    /**
     * 将已存在的任务重新排队等待下载
     *
     * @param id 任务id
     * @return 若指定id的任务存在,则返回该任务实例,否则返回null
     */
    abstract fun enqueueTask(
        id: Int,
    ): DownloadTask?

    abstract fun findTask(id: Int): DownloadTask?

    abstract fun cancelTask(id: Int)

    abstract fun stopService()

    abstract suspend fun saveState(): AndroidDownloadTaskSaveState

    abstract suspend fun restoreState(state: AndroidDownloadTaskSaveState)
}
