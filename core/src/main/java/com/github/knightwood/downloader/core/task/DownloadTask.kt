package com.github.knightwood.downloader.core.task

import com.github.knightwood.downloader.core.executor.DownloadExecutorFactory
import com.github.knightwood.downloader.core.executor.IDownloadExecutor
import com.github.knightwood.downloader.core.filestorage.FileStorage
import com.github.knightwood.downloader.core.filestorage.FileStorageFactory
import com.github.knightwood.downloader.core.model.DownloadTaskConf
import com.github.knightwood.downloader.core.model.DownloadStatus
import com.github.knightwood.downloader.core.model.DownloadTaskSaveState
import com.github.knightwood.downloader.core.model.TaskQueueType
import com.github.knightwood.downloader.core.util.FileUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import org.slf4j.LoggerFactory

/**
 * 1. use [IDownloadExecutor] exec download
 * 2. collect download status and convert to a hot state flow
 * 3. provide pause/resume/cancel manage function
 * 3. provide save/restore downloadState ability(actually it is need IDownloadExecutor supported)
 */
open class DownloadTask(
    val id: Int,
    val conf: DownloadTaskConf,
    val executorProvider: () -> IDownloadExecutor = { DownloadExecutorFactory.create(conf) },
    val fileStorageProvider: () -> FileStorage = { FileStorageFactory.create(conf) },
    val fs: FileSystem = FileSystem.SYSTEM,
) {
    private val executor: IDownloadExecutor by lazy { executorProvider() }
    private val logger = LoggerFactory.getLogger("DownloadTask#$id")
    private val _stateFlow: MutableStateFlow<DownloadStatus> = MutableStateFlow<DownloadStatus>(DownloadStatus.IDLE)
    val stateFlow: StateFlow<DownloadStatus> get() = _stateFlow
//    val queueStateFlow: Flow<TaskStatus> = _stateFlow.map { value ->

//    }

    //当前下载进度
    val progress: Flow<Double> = _stateFlow
        .filter { it is DownloadStatus.DOWNLOADING }
        .map { status ->
            if (status is DownloadStatus.DOWNLOADING) {
                val max = status.length
                val progress = status.progress
                val curr = (progress / max * 100.0)
                logger.debug("downloading max: $max --- progress: $progress")
                curr
            } else {
                0.0
            }
        }

    //最后的下载进度
    private var currentDownloadingProgress: DownloadStatus.DOWNLOADING? = null

    constructor(saveState: DownloadTaskSaveState) : this(saveState.id, saveState.conf) {
        runCatching {
            restoreProgress(saveState.progress)
        }.onFailure {
            logger.error("restoreProgress failed", it)
        }
    }

    /**
     * 在指定协程作用域上开始新的协程执行下载
     */
    fun runOn(scope: CoroutineScope) {
        scope.launch {
            withContext(Dispatchers.IO) {
                execute()
            }
        }
    }

    /**
     * 调用此方法将挂起协程直到下载完成
     */
    suspend fun execute() {
        val cache = conf.md5?.let { FileUtil.findCache(conf.path, it) }
        if (cache != null) {
            _stateFlow.value = DownloadStatus.FINISHED(cache)
            return
        }
        if (conf.isDeleteLastFile) {
            fs.delete(conf.path)
        }
        executor.download(
            fileStorage = fileStorageProvider(),
            url = conf.url
        ).collect { state ->
            _stateFlow.value = state
            if (state is DownloadStatus.DOWNLOADING) {
                currentDownloadingProgress = state
            }
        }
    }

    /**
     * 暂停下载,
     * 调用此方法后会立即挂起协程, 直到任务状态被更新到[DownloadStatus.PAUSED]
     */
    suspend fun pause() {
        if (isDownloading) {
            executor.pause()
            _stateFlow.first { status ->
                status is DownloadStatus.PAUSED
            }
        }
    }

    /**
     * 恢复任务,
     * 调用此方法后会立即挂起协程, 直到任务状态被更新到[DownloadStatus.DOWNLOADING]
     */
    suspend fun resume() {
        if (isPaused) {
            executor.resume()
            _stateFlow.first { status ->
                status is DownloadStatus.DOWNLOADING
            }
        }
    }

    /**
     * 取消任务,
     * 调用此方法后会立即挂起协程, 直到任务状态被更新到[DownloadStatus.CANCELED]
     */
    suspend fun cancel() {
        executor.cancel()
        _stateFlow.first { status ->
            status is DownloadStatus.CANCELED
        }
    }

    /**
     * 需要保存下载进度时可调用此方法获取最后的下载进度
     * @return 如果任务处于idle/starting状态，下载进度可能为null
     */
    fun saveState(): DownloadTaskSaveState {
        if (isDownloading) {
            throw IllegalStateException("需停止/取消/暂停下载任务后调用此方法")
        }
        return DownloadTaskSaveState(
            id = id, conf = conf,
            progress = currentDownloadingProgress,
            finalState = _stateFlow.value
        )
    }

    /**
     * 在执行[runOn]、[execute]之前调用此方法恢复任务进度
     *
     * ```
     * val snap = oldTask.saveState() //从旧任务中保存快照
     * val task = DownloadTask(snap.id,snap.conf) //利用快照生成新任务
     * task.restoreProgress(snap.downloadProgress) //恢复任务进度
     *     .execute()
     * ```
     */
    fun restoreProgress(progress: DownloadStatus.DOWNLOADING?): DownloadTask {
        if (progress == null) {
            return this
        }
        if (!isIdle) {
            throw IllegalStateException("only in IDLE state can be resumed.")
        }
        if (executor.isSupportRestoreState) {
            executor.restoreState(progress)
            conf.isDeleteLastFile = false
        } else {
            throw IllegalStateException("doesn't support restore state.")
        }
        return this
    }
}

val DownloadTask.isIdle get() = stateFlow.value is DownloadStatus.IDLE
val DownloadTask.isDownloading get() = stateFlow.value is DownloadStatus.DOWNLOADING
val DownloadTask.isPaused get() = stateFlow.value is DownloadStatus.PAUSED
val DownloadTask.isFailed get() = stateFlow.value is DownloadStatus.ERROR
val DownloadTask.isCancelled get() = stateFlow.value is DownloadStatus.CANCELED

/**
 * 此方法根据任务当前状态映射成任务所处队列类型.
 *
 * 每个任务状态可以唯一对应一个队列类型, 以表示当前下载状态的任务处于什么类型的下载队列.
 * 映射结果并不100%准确, 比如IDLE/STARTING/DOWNLOADING等下载状态的任务都有可能处于等待下载队列.
 */
fun DownloadStatus.mapping2QueueType() =
    when (this) {
        DownloadStatus.IDLE -> TaskQueueType.QUEUED
        DownloadStatus.STARTING -> TaskQueueType.RUNNING
        is DownloadStatus.DOWNLOADING -> TaskQueueType.RUNNING
        DownloadStatus.PAUSED -> TaskQueueType.PAUSED
        DownloadStatus.CANCELED -> TaskQueueType.CANCELED
        is DownloadStatus.FINISHED -> TaskQueueType.FINISHED
        is DownloadStatus.ERROR -> TaskQueueType.FAILED
    }
