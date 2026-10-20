package com.github.knightwood.downloader.core.model


/**
 * 反应当前下载任务的的队列状态
 */
enum class TaskQueueType {
    QUEUED, /* 排队等待 */
    RUNNING,/* 正在下载 */
    PAUSED,/* 暂停 */
    FINISHED,
    FAILED,
    CANCELED
}
