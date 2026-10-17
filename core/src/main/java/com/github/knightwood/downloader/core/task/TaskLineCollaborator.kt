package com.github.knightwood.downloader.core.task

import com.github.knightwood.downloader.core.model.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable

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
        return repository.findById(id)
    }

    fun close(){
        taskScheduler.cancel()
    }

    fun isEmpty(): Boolean {
        return repository.tasksIn(TaskStatus.RUNNING).isEmpty()
    }
}
