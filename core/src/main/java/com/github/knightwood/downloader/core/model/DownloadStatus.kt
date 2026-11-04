package com.github.knightwood.downloader.core.model

import com.github.knightwood.downloader.core.executor.DownloadExecutor

/**
 * 反应当前[DownloadExecutor]下载状态、进度
 */
sealed class DownloadStatus {
    abstract val name: String

    data object IDLE : DownloadStatus() {
        override val name: String = "IDLE"
    }

    data object STARTING : DownloadStatus() {
        override val name: String = "STARTING"
    }

    data class DOWNLOADING(
        val progress: DownloadProgressRecord,
    ) : DownloadStatus() {
        override val name: String = "DOWNLOADING"
    }

    data object PAUSED : DownloadStatus() {
        override val name: String = "PAUSED"
    }

    data object FINISHED : DownloadStatus() {
        override val name: String = "FINISHED"
    }

    data object CANCELED : DownloadStatus() {
        override val name: String = "CANCELED"
    }

    data class ERROR(val e: Throwable) : DownloadStatus() {
        override val name: String = "ERROR"
    }

}

/** 是否为终态，终态之后不会再有状态变化 */
val DownloadStatus.isTerminalState: Boolean
    get() = this is DownloadStatus.FINISHED ||
            this is DownloadStatus.CANCELED ||
            this is DownloadStatus.ERROR

/**
 * @param index 分块id
 * @param start 分块在下载文件中的起始位置(包含)
 * @param downloaded 已下载到的绝对偏移(这是个位置, 大于等于start, 小于等于end, 不是下载总量)
 * @param end 分块在下载文件中的结束位置(包含)
 */
data class ChunkProgress(
    val index: Int,
    val start: Long,
    val end: Long,
    val downloaded: Long,
){
    val isFinished: Boolean
        get() {
            return downloaded == end
        }
}

data class DownloadProgressRecord(
    val length: Long,/*数据总长度*/
    val progress: Long,/*当前下载量*/
    val snapSpeed: Double = Double.NaN,/*瞬时速度 单位：bytes/second */
    val averageSpeed: Double = Double.NaN,/*平均速度 单位： bytes/second */
    val chunks: List<ChunkProgress> = emptyList(),  // 新增
) {
    /**
     * 当前下载进度百分比
     */
    val percentage: Double
        get() {
            val max = length
            val curr = (progress / max * 100.0)
            return curr
        }
}
