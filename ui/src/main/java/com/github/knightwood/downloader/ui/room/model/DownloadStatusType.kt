package com.github.knightwood.downloader.ui.room.model

import com.github.knightwood.downloader.core.model.DownloadStatus

/**
 * 可持久化的下载状态
 *
 * core 模块中的 [DownloadStatus] 是密封类，其中 `DownloadStatus.ERROR` 携带 `Throwable`，
 * 无法直接写入数据库，因此抽取此枚举用于记录任务状态，
 * 异常信息单独记录在 `DownloadTaskEntity.errorMessage` 字段中。
 *
 * 枚举名与 [DownloadStatus] 的子类名保持一致，[of] 方法负责转换。
 */
enum class DownloadStatusType {
    IDLE,
    STARTING,
    DOWNLOADING,
    PAUSED,
    FINISHED,
    CANCELED,
    ERROR;

    companion object {

        /**
         * 将运行时的下载状态转换为可持久化的状态
         */
        fun of(status: DownloadStatus): DownloadStatusType = when (status) {
            DownloadStatus.IDLE -> IDLE
            DownloadStatus.STARTING -> STARTING
            is DownloadStatus.DOWNLOADING -> DOWNLOADING
            DownloadStatus.PAUSED -> PAUSED
            DownloadStatus.FINISHED -> FINISHED
            DownloadStatus.CANCELED -> CANCELED
            is DownloadStatus.ERROR -> ERROR
        }
    }
}

/**
 * 是否处于已提交给下载服务、正在执行的阶段
 */
val DownloadStatusType.isRunning: Boolean
    get() = this == DownloadStatusType.STARTING || this == DownloadStatusType.DOWNLOADING
