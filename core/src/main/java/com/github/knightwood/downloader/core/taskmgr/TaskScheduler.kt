package com.github.knightwood.downloader.core.taskmgr

import com.github.knightwood.downloader.core.filestorage.FileStorage
import com.github.knightwood.downloader.core.model.TaskQueueType
import com.github.knightwood.downloader.core.task.DownloadTask
import com.github.knightwood.downloader.core.task.mapping2QueueType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.lang.Runnable
import kotlin.coroutines.cancellation.CancellationException
import com.github.knightwood.downloader.core.model.*
import com.github.knightwood.downloader.core.task.currentStatus
import com.github.knightwood.downloader.core.util.HttpConstant
import java.io.Closeable

class TaskScheduler(
    private val scope: CoroutineScope,
    private val maxParallel: Int = HttpConstant.MAX_PARALLEL_DOWNLOAD_COUNT,
) : Runnable, Closeable {
    private val logger = LoggerFactory.getLogger(TaskScheduler::class.java)
    private val mutex = Mutex()

    // 所有任务, id -> DownloadTask
    private val all = LinkedHashMap<Int, DownloadTask>()

    // 等待队列, 用 ArrayDeque 而不是 Channel, 支持随机删除
    private val pending = ArrayDeque<Int>()
    private val waitSignal = Channel<Unit>(Channel.CONFLATED)

    // 正在执行的下载任务 Map<JobId,Job信息>
    private var workers: Map<Int, Worker> = emptyMap()
    private var mActiveTaskListener: ActiveTaskListener? = null

    /**
     * 查找某队列状态下的所有任务
     *
     * 除了等待下载队列, 其余队列均可根据任务的下载状态确定,
     * 我们使用[pending]记录处于等待下载状态的任务列表，
     * 其余队列类型[TaskQueueType]所含任务, 需根据任务的下载状态[DownloadStatus]实时筛选
     *
     */
    fun tasksIn(status: TaskQueueType): List<DownloadTask> {
        val arr = if (status == TaskQueueType.QUEUED) {
            pending.mapNotNull { id -> all[id] }
        } else {
            //筛选处于目标队列类型的任务集合
            all.values.toList().filter { task ->
                // 根据任务当前状态映射成任务所处队列类型, 筛选出处于要查找队列类型的任务
                // IDLE/FAILED/CANCELED状态任务都有可能在等待队列中排队等待下载, 它们算作TaskQueueType.QUEUED,不算作要查找的目标队列类型，因此需剔除已在等待队列中的任务
                task.currentStatus.mapping2QueueType() == status && (task.id !in pending)
            }
        }
        return arr
    }

    /**
     * 查找某些队列状态下的全部任务
     */
    fun tasksIn(vararg status: TaskQueueType): Map<TaskQueueType, List<DownloadTask>> {
        return status.associateWith { id -> tasksIn(id) }
    }

    /**
     * 产生[maxParallel]个work协程，不间断的听从waitSignal信号，从[pending]队列中取出任务并开始执行
     * 注意：
     * 暂停下载中的任务，在协程看来任务的execute方法没有结束，此状态下works中的协程无法选择一个等待队列中的任务执行
     */
    override fun run() {
        if (workers.isNotEmpty()) return
        workers = (0 until maxParallel).associateWith { jobId ->
            val job = scope.launch(Dispatchers.IO) {
                while (true) {
                    workers[jobId]?.isWorking = false
                    waitSignal.receive()                     // 等到有任务
                    val id = nextTask() ?: continue      // 取一个
                    workers[jobId]?.isWorking = true
                    runTask(id)
                }
            }
            TaskScheduler.Worker(jobId, job, false)
        }
    }

    private suspend fun nextTask(): Int? = mutex.withLock {
        // 跳过已被取消/移除的
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            return id
        }
        null
    }

    private suspend fun runTask(id: Int) {
        try {
            val task = all[id]!!
            coroutineScope {
                mActiveTaskListener?.onTaskStart(task)
                task.execute()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error("task failed", e)
        }
    }

    //<editor-fold desc="任务交互">

    /**
     * 提交新的下载任务
     */
    suspend fun submit(task: DownloadTask) {
        all[task.id] = task
        enqueue(task.id)
    }

    fun findTaskById(id: Int): DownloadTask? {
        return all[id]
    }

    /**
     * 将已存在任务添加到等待队列, 等待下载
     */
    suspend fun enqueue(id: Int) {
        val added = mutex.withLock {
            if (id in pending) false else {
                pending.addLast(id); true
            }
        }
        if (!added) return
        waitSignal.trySend(Unit)
    }

    /**
     * 取消任务下载:
     * 1. 立即从等待队列中删除任务, 避免开始下载
     * 2. 若任务已在执行, 调用 task.cancel() 取消下载
     */
    suspend fun cancel(id: Int) {
        val removed = mutex.withLock { pending.remove(id) }
        if (removed) { //还在排队, 从等待队列移除后直接返回即可
            return
        }
        // 正在执行的,需要取消下载
        all[id]?.cancel()
    }

    /**
     * 删除某任务
     */
    suspend fun delete(id: Int) {
        cancel(id)
        all.remove(id)
    }

    //</editor-fold>

    override fun close() {
        workers.forEach { it.value.job.cancel() }
        workers = emptyMap()
        waitSignal.close()
    }

    /**
     * 导出全部任务的下载状态/进度
     */
    suspend fun saveState(): TaskSchedulerSaveState {
        val allTaskSaveState = all.map { (key, value) ->
            value.saveState()
        }
        return TaskSchedulerSaveState(pending.toIntArray(), allTaskSaveState)
    }

    suspend fun restoreState(
        saveStates: TaskSchedulerSaveState,
        taskProvider: (DownloadTaskSaveState) -> DownloadTask = {
            DownloadTask(
                id = it.id, conf = it.conf,
            ).apply { restoreProgress(it.progress) }
        },
    ) {
        pending.clear()
        pending.addAll(saveStates.pending.toList())
        all.clear()
        saveStates.all.forEach { saveState ->
            all[saveState.id] = taskProvider(saveState)
        }
    }

    //<editor-fold desc="其他">
    fun setActiveTaskProcessor(listener: ActiveTaskListener) {
        mActiveTaskListener = listener
    }

    /**
     * 判断是否已没有等待或正在下载的任务
     *
     * @return 若没有排队等待下载的任务,也没有正在下载的任务, 返回true
     */
    fun isEmpty(): Boolean {
        return pending.isEmpty() && workers.all { !it.value.isWorking }
    }

    /**
     * 记录一个工作协程
     */
    data class Worker(
        val jobId: Int,
        val job: Job,
        /**
         * 当协程运行某任务时,此标志位为true,否则为false
         */
        var isWorking: Boolean,
    )
    //</editor-fold>
}
