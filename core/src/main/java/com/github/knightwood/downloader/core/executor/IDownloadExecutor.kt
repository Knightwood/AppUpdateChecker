package com.github.knightwood.downloader.core.executor

import com.github.knightwood.downloader.core.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

interface IDownloadExecutor {
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
