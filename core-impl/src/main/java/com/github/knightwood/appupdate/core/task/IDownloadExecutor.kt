package com.github.knightwood.appupdate.core.task

import com.github.knightwood.appupdate.core.base.bean.DownloadStatus
import kotlinx.coroutines.flow.Flow

interface IDownloadExecutor {
    /**
     * download apk from apkUrl
     *
     * @param fileStorage 将下载数据输出到文件存储器
     * @param apkUrl 下载地址
     * 返回下载状态
     */
    fun download(fileStorage: FileStorage, apkUrl: String): Flow<DownloadStatus>

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

    /**
     * release memory
     */
    fun release()
}
