package com.github.knightwood.downloader.core.model

enum class TaskStatus {
    IDLE,
    QUEUED,
    RUNNING,
    PAUSED,
    FINISHED,
    FAILED,
    CANCELED
}
