package com.github.knightwood.downloader.core.model

sealed class TaskEvent {
    class Removed(val taskId: Int) : TaskEvent()
    class Added(val taskId: Int) : TaskEvent()
    class StatusChanged(val taskId: Int, oldStatus: TaskQueueType, newStatus: TaskQueueType) : TaskEvent()
}
