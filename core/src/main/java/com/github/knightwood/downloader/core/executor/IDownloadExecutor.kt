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

    fun create(conf: DownloadTaskConf): IDownloadExecutor {
        // 未开启分块下载时 chunkCount 通常为0, 这里退化为单连接下载
        val chunkCount = if (conf.isSupportChunkDownload) conf.chunkCount.coerceAtLeast(1) else 1
        return create(chunkCount, conf.minChunkSize, null)
    }
}
