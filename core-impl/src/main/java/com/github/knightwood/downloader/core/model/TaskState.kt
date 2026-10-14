package com.github.knightwood.downloader.core.model

import okio.Path

sealed class TaskState {

    data object IDLE : TaskState()

    data object STARTING : TaskState()

    data class DOWNLOADING(
        val length: Int,/*数据长度*/
        val progress: Long,/*当前下载量*/
        val snapSpeed: Double = Double.NaN,/*瞬时速度*/
        val averageSpeed: Double = Double.NaN,/*平均速度*/
    ) : TaskState()

    data object PAUSED : TaskState()

    data class FINISHED(val path: Path) : TaskState()

    data object DROPPED : TaskState()

    data class ERROR(val e: Throwable) : TaskState()

}
