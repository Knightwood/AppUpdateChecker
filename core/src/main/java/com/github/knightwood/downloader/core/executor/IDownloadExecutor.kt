package com.github.knightwood.downloader.core.executor

import com.github.knightwood.downloader.core.model.DownloadStatus
import com.github.knightwood.downloader.core.filestorage.FileStorage
import com.github.knightwood.downloader.core.util.HttpConstant
import com.github.knightwood.downloader.core.model.DownloadTaskConf
import kotlinx.coroutines.flow.Flow

interface IDownloadExecutor {
    val isSupportRestoreState: Boolean

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

object DownloadExecutorFactory {
    /**
     * create download executor
     * @param chunkCount 分块数量
     * @param minChunkSize 最小分块大小,单位byte; 文件总大小小于此值则不进行分块.
     * @param lastDownloadProgress 上次的下载进度,若下载执行器可以恢复进度则使用此变量恢复进度
     * @return IDownloadExecutor
     */
    fun create(
        chunkCount: Int = 1,
        minChunkSize: Long = HttpConstant.MIN_CHUNK_SIZE,
        lastDownloadProgress: DownloadStatus.DOWNLOADING? = null,
    ): IDownloadExecutor {
        check(chunkCount > 0) { "chunkCount must be > 0" }
        return if (chunkCount > 1) {
            MultiChunkDownloadExecutor(chunkCount, minChunkSize, lastDownloadProgress)
        } else {
            DownloadExecutor(lastDownloadProgress)
        }
    }

    /**
     * create download executor
     */
    fun create(conf: DownloadTaskConf): IDownloadExecutor {
        // 未开启分块下载时 chunkCount 通常为0, 这里退化为单连接下载
        val chunkCount = if (conf.isSupportChunkDownload) conf.chunkCount.coerceAtLeast(1) else 1
        return create(chunkCount, conf.minChunkSize, conf.lastDownloadProgress)
    }
}
