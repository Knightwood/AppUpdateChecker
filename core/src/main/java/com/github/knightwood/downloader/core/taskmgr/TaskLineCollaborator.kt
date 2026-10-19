package com.github.knightwood.downloader.core.taskmgr

import com.github.knightwood.downloader.core.model.TaskStatus
import com.github.knightwood.downloader.core.task.DownloadTask
import kotlinx.coroutines.CoroutineScope

class TaskLineCollaborator(
    val scope: CoroutineScope,
    val repository: TaskRepository = TaskRepository(),
    val taskScheduler: TaskScheduler = TaskScheduler(repo = repository, scope = scope),
) {

    fun run() = taskScheduler.start()

    suspend fun submit(task: DownloadTask) {
        repository.add(task)
        taskScheduler.enqueue(task.id)
    }

    suspend fun cancelTask(id: Int) {
        taskScheduler.cancel(id)
    }

    fun findTask(id: Int): DownloadTask? {
        return repository.findTaskById(id)
    }

    fun close(){
        taskScheduler.cancel()
    }

    fun isEmpty(): Boolean {
        return repository.tasksIn(TaskStatus.RUNNING).isEmpty()
    }
}
