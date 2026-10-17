package com.github.knightwood.downloader.core.task

import com.github.knightwood.downloader.core.model.DownloadStatus
import com.github.knightwood.downloader.core.model.TaskStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TaskRepository {
    private val mutex = Mutex()

    // 所有任务, id -> DownloadTask
    private val all = LinkedHashMap<Int, DownloadTask>()

    // 分状态索引 (只存 id, 避免持有引用)
    private val byStatus = mutableMapOf<TaskStatus, MutableSet<Int>>()
        .apply { TaskStatus.entries.forEach { put(it, mutableSetOf()) } }

    private val _events = MutableSharedFlow<TaskEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<TaskEvent> = _events

    suspend fun add(task: DownloadTask) = mutex.withLock {
        all[task.id] = task
        byStatus.getValue(TaskStatus.IDLE).add(task.id)
        _events.tryEmit(TaskEvent.Added(task.id))
    }

    suspend fun transition(id: Int, from: TaskStatus, to: TaskStatus) = mutex.withLock {
        /*
        并发下如果两个地方都调用 transition(id, RUNNING, FINISHED)，第二次会从 RUNNING 集合里移除一个不存在的 id（无害），
        但往 FINISHED 里加了一次重复的 id（Set 去重，仍无害）。
        真正的问题是状态非法转换被静默接受。
        加断言或返回 Boolean：
        */
        if (id !in byStatus.getValue(from)) return@withLock false
        byStatus.getValue(from).remove(id)
        byStatus.getValue(to).add(id)
        _events.tryEmit(TaskEvent.StatusChanged(id, from, to))
    }

    suspend fun statusOf(id: Int): TaskStatus? = mutex.withLock {
        byStatus.entries.firstOrNull { id in it.value }?.key
    }

    suspend fun tasksIn(status: TaskStatus): List<DownloadTask> = mutex.withLock {
        byStatus.getValue(status).mapNotNull { all[it] }
    }

    suspend fun remove(id: Int) = mutex.withLock {
        all.remove(id)
        byStatus.values.forEach { it.remove(id) }
        _events.tryEmit(TaskEvent.Removed(id))
    }

    suspend fun snapshot(): Map<TaskStatus, List<DownloadTask>> = mutex.withLock {
        byStatus.mapValues { (_, ids) -> ids.mapNotNull { all[it] } }
    }

    /**
     * 导出全部任务的下载状态/进度
     */
    suspend fun saveState(): List<TaskSaveState> {
        return all.map { (key, value) ->
            TaskSaveState(key, statusOf(key), value.saveState())
        }
    }

    fun findById(id: Int): DownloadTask? {
        return all.values.find { it.id == id }
    }
}

sealed class TaskEvent {
    class Removed(val taskId: Int) : TaskEvent()
    class Added(val taskId: Int) : TaskEvent()
    class StatusChanged(val taskId: Int, oldStatus: TaskStatus, newStatus: TaskStatus) : TaskEvent()
}

class TaskSaveState(
    val id: Int,
    val taskStatus: TaskStatus?,
    val taskDownloadStatus: DownloadStatus.DOWNLOADING?,
)
