package com.github.knightwood.downloader.core.task

import com.github.knightwood.downloader.core.model.DownloadStatus
import com.github.knightwood.downloader.core.model.TaskStatus
import com.github.knightwood.downloader.core.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

class TaskScheduler(
    private val scope: CoroutineScope,
    private val repo: TaskRepository = TaskRepository(),
    private val maxParallel: Int = 3,
) {
    private val mutex = Mutex()
    private val TAG = "TaskScheduler"

    // 用 ArrayDeque 而不是 Channel, 支持随机删除
    private val pending = ArrayDeque<Int>()
    private val waitSignal = Channel<Unit>(Channel.CONFLATED)

    private var workers: List<Job> = emptyList()

    fun start() {
        if (workers.isNotEmpty()) return
        workers = (0 until maxParallel).map {
            scope.launch(Dispatchers.IO) {
                while (isActive) {
                    waitSignal.receive()                     // 等到有任务
                    val id = nextTask() ?: continue          // 取一个
                    runTask(id)
                }
            }
        }
    }

    private suspend fun nextTask(): Int? = mutex.withLock {
        // 跳过已被取消/移除的
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            if (repo.statusOf(id) == TaskStatus.QUEUED) return id
        }
        null
    }

    suspend fun enqueue(id: Int) {
        val added = mutex.withLock {
            if (id in pending) false else {
                pending.addLast(id); true
            }
        }
        if (!added) return
        repo.transition(id, TaskStatus.IDLE, TaskStatus.QUEUED)
        waitSignal.trySend(Unit)
    }

    /** 立即删除：排队中就真删；执行中走 task.cancel() */
    suspend fun cancel(id: Int) {
        val removed = mutex.withLock { pending.remove(id) }
        if (removed) {
            repo.transition(id, TaskStatus.QUEUED, TaskStatus.CANCELED)
            return
        }
        // 正在执行
        repo.tasksIn(TaskStatus.RUNNING).find { it.id == id }?.cancel()
    }

    private suspend fun runTask(id: Int) {
        val task = repo.tasksIn(TaskStatus.QUEUED).find { it.id == id } ?: return
        repo.transition(id, TaskStatus.QUEUED, TaskStatus.RUNNING)
        try {
            coroutineScope {
                launch(Dispatchers.IO) {
                    task.stateFlow.collect { state ->
                        when (state) {
                            is DownloadStatus.FINISHED -> repo.transition(id, TaskStatus.RUNNING, TaskStatus.FINISHED)
                            is DownloadStatus.ERROR -> repo.transition(id, TaskStatus.RUNNING, TaskStatus.FAILED)
                            is DownloadStatus.CANCELED -> repo.transition(id, TaskStatus.RUNNING, TaskStatus.CANCELED)
                            else -> Unit
                        }
                    }
                }
                task.execute()   // 内部把最终状态回写 repo
            }
        } catch (e: CancellationException) {
            repo.transition(id, TaskStatus.RUNNING, TaskStatus.CANCELED)
        } catch (e: Throwable) {
            repo.transition(id, TaskStatus.RUNNING, TaskStatus.FAILED)
            LogUtil.e(TAG, e)
        }
    }

    fun stop() {
        workers.forEach { it.cancel() }
        workers = emptyList()
        waitSignal.close()
    }
}
