package com.github.knightwood.downloader.core.model

import okio.Path
import com.github.knightwood.downloader.core.executor.DownloadExecutor

/**
 * 反应当前[DownloadExecutor]下载状态、进度
 */
sealed class DownloadStatus {
    abstract val name: String
    data object IDLE : DownloadStatus(){
        override val name: String = "IDLE"
    }

    data object STARTING : DownloadStatus(){
        override val name: String = "STARTING"
    }

    data class DOWNLOADING(
        val length: Long,/*数据总长度*/
        val progress: Long,/*当前下载量*/
        val snapSpeed: Double = Double.NaN,/*瞬时速度 单位：bytes/second */
        val averageSpeed: Double = Double.NaN,/*平均速度 单位： bytes/second */
        val chunks: List<ChunkProgress> = emptyList(),  // 新增
    ) : DownloadStatus() {
        override val name: String = "DOWNLOADING"
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

    data object PAUSED : DownloadStatus(){
        override val name: String = "PAUSED"
    }

    data object FINISHED : DownloadStatus(){
        override val name: String = "FINISHED"
    }

    data object CANCELED : DownloadStatus(){
        override val name: String = "CANCELED"
    }

    data class ERROR(val e: Throwable) : DownloadStatus(){
        override val name: String = "ERROR"
    }

}

/** 是否为终态，终态之后不会再有状态变化 */
val DownloadStatus.isTerminalState: Boolean
    get() = this is DownloadStatus.FINISHED ||
            this is DownloadStatus.CANCELED ||
            this is DownloadStatus.ERROR

data class ChunkProgress(
    val index: Int, //分块id
    val start: Long, //分块在下载文件中的起始位置
    val end: Long,// 分块在下载文件中的结束位置
    val downloaded: Long,// 已下载到的绝对偏移
)

typealias DownloadProgressRecord = DownloadStatus.DOWNLOADING
