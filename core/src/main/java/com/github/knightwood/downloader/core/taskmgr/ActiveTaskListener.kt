package com.github.knightwood.downloader.core.taskmgr

import com.github.knightwood.downloader.core.task.DownloadTask

/**
 * 用于监听即将开始运行的任务
 */
fun interface ActiveTaskListener {
    fun onTaskStart(task: DownloadTask)
}
