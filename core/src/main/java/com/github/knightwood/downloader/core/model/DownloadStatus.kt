package com.github.knightwood.downloader.core.model

import okio.Path
import com.github.knightwood.downloader.core.executor.DownloadExecutor

/**
 * 反应当前[DownloadExecutor]下载状态、进度
 */
sealed class DownloadStatus {

    data object IDLE : DownloadStatus()

    data object STARTING : DownloadStatus()

    data class DOWNLOADING(
        val length: Long,/*数据总长度*/
        val progress: Long,/*当前下载量*/
        val snapSpeed: Double = Double.NaN,/*瞬时速度 单位：bytes/second */
        val averageSpeed: Double = Double.NaN,/*平均速度 单位： bytes/second */
        val chunks: List<ChunkProgress> = emptyList(),  // 新增
    ) : DownloadStatus()

    data object PAUSED : DownloadStatus()

    data object FINISHED : DownloadStatus()

    data object CANCELED : DownloadStatus()

    data class ERROR(val e: Throwable) : DownloadStatus()

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
