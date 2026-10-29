package com.github.knightwood.downloader.ui.repository

import com.github.knightwood.downloader.core.android.model.AndroidDownloadParams
import com.github.knightwood.downloader.core.android.service.DownloadServiceLinker
import com.github.knightwood.downloader.core.model.DownloadStatus
import com.github.knightwood.downloader.core.task.DownloadTask
import com.github.knightwood.downloader.core.task.isDownloading
import com.github.knightwood.downloader.core.task.isPaused
import com.github.knightwood.downloader.ui.room.dao.DownloadTaskDao
import com.github.knightwood.downloader.ui.room.entity.DownloadTaskEntity
import com.github.knightwood.downloader.ui.room.mapper.toChunkEntity
import com.github.knightwood.downloader.ui.room.mapper.toDownloadParams
import com.github.knightwood.downloader.ui.room.mapper.toTaskEntity
import com.github.knightwood.downloader.ui.room.model.DownloadStatusType
import com.github.knightwood.downloader.ui.room.model.isRunning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * 下载任务仓库
 *
 * 职责：
 * 1. 把新任务写入数据库，并提交给 `DownloadService` 执行
 * 2. 订阅任务的状态流，把状态/进度回写到数据库（数据库是界面唯一数据源）
 * 3. 提供服务重连后的状态对齐：数据库里记录为“下载中”但服务中已不存在的任务
 *    说明进程被系统回收过，将其标记为“已暂停”，用户可手动重新下载
 * 4. 暂停、继续、停止、删除、重新下载
 *
 * 说明：`DownloadStatus.DOWNLOADING` 大约每200ms（分块下载）或1s（单连接）发射一次，
 * 直接落库会造成大量写操作，因此进度按 [PROGRESS_WRITE_INTERVAL] 节流，
 * 只有状态变化以及暂停/停止时才立即写入。
 */
class DownloadTaskRepository(
    private val dao: DownloadTaskDao,
    private val scope: CoroutineScope,
) {

    private val mutex = Mutex()

    /** 每个运行中任务的进度采集协程，key为任务id */
    private val collectors = ConcurrentHashMap<Int, Job>()

    @Volatile
    private var linker: DownloadServiceLinker? = null

    /** 已分配的最大任务id，避免每次提交都查询数据库 */
    private var lastTaskId = 0

    /** 已分配的最大排队序号 */
    private var lastQueueOrder = 0

    //<editor-fold desc="服务绑定">

    /**
     * 下载服务连接成功后调用
     *
     * 会重新对齐数据库中残留的“下载中”状态，并为服务中仍在执行的任务重新挂上进度采集。
     */
    fun attach(linker: DownloadServiceLinker) {
        this.linker = linker
        scope.launch {
            runCatching { syncWithService(linker) }
        }
    }

    /**
     * 下载服务断开后调用
     */
    fun detach() {
        linker = null
        collectors.values.forEach { it.cancel() }
        collectors.clear()
    }

    /**
     * 判断任务是否仍在下载服务中（即拥有运行时任务实例）
     */
    fun isLive(taskId: Int): Boolean = linker?.findTask(taskId) != null

    private suspend fun syncWithService(linker: DownloadServiceLinker) {
        val records = dao.getAll()
        // 与 submit 并发时取较大值, 避免回退后分配到重复的任务id
        lastTaskId = maxOf(lastTaskId, records.maxOfOrNull { it.taskId } ?: 0)
        lastQueueOrder = maxOf(lastQueueOrder, (records.maxOfOrNull { it.queueOrder } ?: -1) + 1)
        val now = System.currentTimeMillis()
        records.forEach { entity ->
            val task = linker.findTask(entity.taskId)
            if (task != null) {
                // 服务仍在下载（例如界面重建），重新采集进度
                observeTask(task)
            } else if (entity.status.isRunning) {
                // 进程被回收，运行状态已丢失
                dao.updateStatus(entity.taskId, DownloadStatusType.PAUSED, null, now)
            }
        }
    }

    //</editor-fold>

    //<editor-fold desc="任务提交">

    /**
     * 提交新的下载任务
     *
     * @return 分配的任务id
     */
    suspend fun submit(params: AndroidDownloadParams): Result<Int> = runCatching {
        mutex.withLock {
            val linker = linker ?: error("下载服务尚未连接，请稍后重试")
            if (params.fileEntity.url.isBlank()) error("下载地址不能为空")
            if (params.fileEntity.name.isBlank()) error("文件名不能为空")
            if (lastTaskId == 0) {
                lastTaskId = dao.getAll().maxOfOrNull { it.taskId } ?: 0
            }
            val taskId = lastTaskId + 1
            lastTaskId = taskId
            val queueOrder = lastQueueOrder++
            dao.saveTask(params.toTaskEntity(taskId, queueOrder), emptyList())
            val task = linker.submitTask(taskId, params)
            observeTask(task)
            taskId
        }
    }

    /**
     * 重新下载：重置进度并按原配置重新提交
     *
     * - 任务已暂停：直接继续
     * - 任务正在下载：忽略
     * - 其他情况（已完成/已停止/失败/排队中）：重置进度后重新提交，任务id保持不变
     */
    suspend fun retry(taskId: Int): Result<Unit> = runCatching {
        val linker = linker ?: error("下载服务尚未连接，请稍后重试")
        val entity = dao.findById(taskId) ?: error("任务记录不存在")
        val live = linker.findTask(taskId)
        when {
            live != null && live.isPaused -> scope.launch { live.resume() }
            live != null && live.isDownloading -> Unit
            else -> {
                val now = System.currentTimeMillis()
                dao.saveTask(entity.resetForRetry(lastQueueOrder++, now), emptyList())
                observeTask(linker.submitTask(taskId, entity.toDownloadParams()))
            }
        }
    }

    //</editor-fold>

    //<editor-fold desc="任务控制">

    /**
     * 暂停任务
     *
     * `DownloadTask.pause()` 会挂起直到任务进入暂停状态，此处不阻塞界面，由状态流回写数据库。
     */
    fun pause(taskId: Int) {
        val task = linker?.findTask(taskId) ?: return
        scope.launch { runCatching { task.pause() } }
    }

    /**
     * 继续下载
     */
    fun resume(taskId: Int) {
        val task = linker?.findTask(taskId) ?: return
        scope.launch { runCatching { task.resume() } }
    }

    /**
     * 停止任务
     *
     * 排队中的任务会被移出等待队列，下载中的任务会被取消下载。
     */
    suspend fun cancel(taskId: Int) {
        val linker = linker ?: return
        val task = linker.findTask(taskId)
        if (task == null) {
            val now = System.currentTimeMillis()
            dao.markEnded(taskId, DownloadStatusType.CANCELED, null, now, now)
            return
        }
        linker.cancelTask(taskId)
        if (!task.isDownloading && !task.isPaused) {
            // 排队中的任务只会被移出队列，任务状态不会变成取消，此处主动记录
            val now = System.currentTimeMillis()
            dao.markEnded(taskId, DownloadStatusType.CANCELED, null, now, now)
        }
    }

    /**
     * 删除任务记录（下载中的任务会先停止）
     */
    suspend fun delete(taskId: Int) {
        val linker = linker
        val task = linker?.findTask(taskId)
        if (linker != null && task != null) {
            runCatching { linker.cancelTask(taskId) }
        }
        collectors.remove(taskId)?.cancel()
        dao.deleteById(taskId)
    }

    /**
     * 清空已完成的任务记录
     */
    suspend fun clearFinished() {
        dao.deleteIn(DownloadStatusType.FINISHED)
    }

    //</editor-fold>

    //<editor-fold desc="状态/进度回写">

    private fun observeTask(task: DownloadTask) {
        collectors.remove(task.id)?.cancel()
        collectors[task.id] = scope.launch {
            var lastWriteTime = 0L
            var snapshot: DownloadStatus.DOWNLOADING? = null
            try {
                // stateFlow 是不会结束的热流, 到达终态后主动结束采集, 避免协程常驻
                task.stateFlow
                    .takeWhile { status -> !status.isTerminalState }
                    .collect { status ->
                        if (status is DownloadStatus.DOWNLOADING) {
                            snapshot = status
                            val now = System.currentTimeMillis()
                            if (now - lastWriteTime >= PROGRESS_WRITE_INTERVAL) {
                                lastWriteTime = now
                                writeProgress(task.id, status, now)
                            }
                        } else {
                            writeState(task.id, status, snapshot)
                        }
                    }
                writeState(task.id, task.stateFlow.value, snapshot)
            } finally {
                collectors.remove(task.id)
            }
        }
    }

    private suspend fun writeProgress(taskId: Int, status: DownloadStatus.DOWNLOADING, now: Long) {
        dao.updateProgress(
            taskId = taskId,
            status = DownloadStatusType.DOWNLOADING,
            totalSize = status.length,
            downloadedSize = status.progress,
            snapSpeed = status.snapSpeed,
            averageSpeed = status.averageSpeed,
            updateTime = now,
        )
    }

    private suspend fun writeState(
        taskId: Int,
        status: DownloadStatus,
        snapshot: DownloadStatus.DOWNLOADING?,
    ) {
        val now = System.currentTimeMillis()
        when (status) {
            DownloadStatus.IDLE -> dao.updateStatus(taskId, DownloadStatusType.IDLE, null, now)

            DownloadStatus.STARTING -> dao.markStarted(taskId, DownloadStatusType.STARTING, now, now)

            DownloadStatus.PAUSED -> {
                snapshot?.let { writeProgress(taskId, it, now) }
                dao.updateStatus(taskId, DownloadStatusType.PAUSED, null, now)
            }

            DownloadStatus.FINISHED -> {
                val total = snapshot?.length ?: dao.findById(taskId)?.size ?: 0L
                dao.updateProgress(
                    taskId = taskId,
                    status = DownloadStatusType.FINISHED,
                    totalSize = total,
                    downloadedSize = total,
                    snapSpeed = Double.NaN,
                    averageSpeed = snapshot?.averageSpeed ?: Double.NaN,
                    updateTime = now,
                )
                dao.markEnded(taskId, DownloadStatusType.FINISHED, null, now, now)
                dao.deleteChunks(taskId)
            }

            DownloadStatus.CANCELED -> {
                snapshot?.let {
                    writeProgress(taskId, it, now)
                    dao.replaceChunks(taskId, it.chunks.map { chunk -> chunk.toChunkEntity(taskId) })
                }
                dao.markEnded(taskId, DownloadStatusType.CANCELED, null, now, now)
            }

            is DownloadStatus.ERROR -> {
                snapshot?.let {
                    dao.replaceChunks(taskId, it.chunks.map { chunk -> chunk.toChunkEntity(taskId) })
                }
                dao.markEnded(taskId, DownloadStatusType.ERROR, status.e.message, now, now)
            }

            is DownloadStatus.DOWNLOADING -> Unit
        }
    }

    //</editor-fold>

    private fun DownloadTaskEntity.resetForRetry(
        queueOrder: Int,
        updateTime: Long,
    ): DownloadTaskEntity = copy(
        status = DownloadStatusType.IDLE,
        errorMessage = null,
        downloadedSize = 0,
        snapSpeed = Double.NaN,
        averageSpeed = Double.NaN,
        startTime = null,
        endTime = null,
        queueOrder = queueOrder,
        updateTime = updateTime,
    )

    private companion object {
        /** 进度落库的最小间隔，避免高频写数据库 */
        const val PROGRESS_WRITE_INTERVAL = 500L
    }
}

/** 是否为终态，终态之后不会再有状态变化 */
private val DownloadStatus.isTerminalState: Boolean
    get() = this is DownloadStatus.FINISHED ||
        this is DownloadStatus.CANCELED ||
        this is DownloadStatus.ERROR
