package com.github.knightwood.appupdate.core.task

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable

class TaskCollaborator(
    val scope: CoroutineScope,
) : Runnable, Closeable {
    private var job: Job? = null
    private val tasks: Channel<DownloadTask> = Channel()

    /**
     * 任务被提交到channel后无法直接移除, 如需取消任务,
     * 需将任务id放于此处, 待执行时跳过任务
     */
    private val waitCancelTaskIds: MutableSet<String> = HashSet()

    /**
     * 当前正在执行的任务
     */
    val currentTask: MutableStateFlow<DownloadTask?> = MutableStateFlow(null)

    override fun run() {
        if (job == null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    for (task in tasks) {
                        if (!waitCancelTaskIds.contains(task.id)) {
                            waitCancelTaskIds.remove(task.id)
                            task.cancel()
                            continue
                        }
                        currentTask.value = task
                        task.execute()
                    }
                }
            }
        }
    }

    suspend fun submitTask(task: DownloadTask) {
        tasks.send(task)
    }

    fun cancelTask(id: String) {
        if (job == null) {
            return
        } else {
            currentTask.value?.let {
                if (it.id == id) {
                    it.cancel()
                }
            } ?: waitCancelTaskIds.add(id)
        }
    }

    override fun close() {
        job?.cancel()
        job = null
        tasks.cancel()
        tasks.close()
        currentTask.value = null
    }

}
