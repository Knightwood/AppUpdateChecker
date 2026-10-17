package com.github.knightwood.downloader.core.executor

import com.github.knightwood.downloader.core.model.DownloadStatus
import com.github.knightwood.downloader.core.model.TaskStatus
import kotlinx.coroutines.flow.Flow

interface IDownloadExecutor {
    /**
     * 在执行[download]方法下载之前，调用此方法设置上次下载中断的进度
     * ```
     * executor.resumeFrom(lastSaveState)
     *         .download(fileStorage,url)
     * ```
     */
    fun restoreState(saveState: DownloadStatus.DOWNLOADING): IDownloadExecutor = this
    /**
     * download apk from apkUrl
     *
     * @param fileStorage 将下载数据输出到文件存储器
     * @param url 下载地址
     * 返回下载状态
     */
    fun download(
        fileStorage: FileStorage,
        url: String,
    ): Flow<DownloadStatus>

    fun pause() {
        throw NotImplementedError()
    }

    fun resume() {
        throw NotImplementedError()
    }

    /**
     * cancel download apk
     */
    fun cancel()

}
