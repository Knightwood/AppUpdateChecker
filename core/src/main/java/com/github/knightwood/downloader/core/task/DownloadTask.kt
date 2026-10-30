package com.github.knightwood.downloader.core.task

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

import com.github.knightwood.downloader.core.executor.DownloadExecutorFactory
import com.github.knightwood.downloader.core.executor.IDownloadExecutor
import com.github.knightwood.downloader.core.filestorage.FileStorage
import com.github.knightwood.downloader.core.filestorage.FileStorageFactory
import com.github.knightwood.downloader.core.model.DownloadTaskConf
import com.github.knightwood.downloader.core.model.DownloadStatus
import com.github.knightwood.downloader.core.model.DownloadTaskSaveState
import com.github.knightwood.downloader.core.model.TaskQueueType
import com.github.knightwood.downloader.core.taskmgr.TaskScheduler
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
) {
    private val logger = LoggerFactory.getLogger("DownloadTask#$id")
    private val executor: IDownloadExecutor by lazy { executorProvider() }
    private val _stateFlow: MutableStateFlow<DownloadStatus> = MutableStateFlow(DownloadStatus.IDLE)
    val stateFlow: StateFlow<DownloadStatus> get() = _stateFlow

    //总的下载进度百分比（0.0% ~ 100.0%）
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

    //最新的下载进度
    private var _currentDownloadingProgress: MutableStateFlow<DownloadStatus.DOWNLOADING?> = MutableStateFlow(null)
    val currentDownloadingProgress: StateFlow<DownloadStatus.DOWNLOADING?> get() = _currentDownloadingProgress

    constructor(
        saveState: DownloadTaskSaveState,
        executorProvider: () -> IDownloadExecutor = { DownloadExecutorFactory.create(saveState.conf) },
        fileStorageProvider: () -> FileStorage = { FileStorageFactory.create(saveState.conf) },
    ) : this(saveState.id, saveState.conf, executorProvider, fileStorageProvider) {
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
        if (isCancelled) { //这是下载失败又重新下载的，需重置某些状态
            _stateFlow.value = DownloadStatus.IDLE
            //恢复之前取消任务时的下载进度
            if (_currentDownloadingProgress.value != null) {
                restoreProgress()
            }
        } else if (isFailed) {
            _stateFlow.value = DownloadStatus.IDLE
            _currentDownloadingProgress.value = null // 重置下载进度
        }
        executor.download(
            fileStorage = fileStorageProvider(),
            url = conf.url
        ).collect { state ->
            _stateFlow.value = state
            if (state is DownloadStatus.DOWNLOADING) {
                _currentDownloadingProgress.value = state
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
        } else {
            logger.info("current status is $currentStatus , can not pause")
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
        } else {
            logger.info("current status is $currentStatus , no need resume")
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
     * 获取最后的下载进度和状态
     *
     * @return 如果任务处于idle/starting状态，下载进度可能为null
     */
    suspend fun saveState(): DownloadTaskSaveState {
        val finalState = if (isDownloading) {
            // 如果不停止任务直接获取进度和状态, 最终停止任务时实际下载进度与保存的进度不一致
            // 所以保存进度时需要停止任务下载,并将最终状态指定为"下载中", 而不是停止任务下载达到的"取消"状态
            cancel()
            _currentDownloadingProgress.value!!
        } else {
            _stateFlow.value
        }
        return DownloadTaskSaveState(
            id = id, conf = conf,
            progress = _currentDownloadingProgress.value,
            finalState = finalState
        )
    }

    /**
     * 在执行[runOn]、[execute]之前调用此方法恢复任务进度
     *
     * * 为何仅提供进度恢复, 而不提供状态、配置恢复：
     *
     *  恢复任务实际上是重新建立任务,设置下载进度并添加到[TaskQueueType.QUEUED]队列等待下载。
     *  id、配置在生成任务实例时就已传入构造函数恢复，进度则需在[execute]方法执行前调用[restoreProgress]更新。
     *  至于状态，你上次下载到50%时取消下载或下载失败, 现在要从50%开始下载，新建立的任务，状态当然要从IDLE开始。
     *  若非要恢复上次的状态，你就要将任务状态从IDLE设置到取消状态，还下载个jb。
     *
     *
     * 使用示例：
     * ```
     * val snap = oldTask.saveState() //从旧任务中保存快照
     * val task = DownloadTask(snap.id,snap.conf) //利用快照生成新任务
     * task.restoreProgress(snap.downloadProgress) //恢复任务进度
     * task.execute()
     * ```
     */
    fun restoreProgress(progress: DownloadStatus.DOWNLOADING? = _currentDownloadingProgress.value): Boolean {
        if (progress == null) {
            return false
        }
        if (!isIdle) {
            logger.error("only in IDLE state can be resumed.")
            return false
        }
        if (executor.isSupportRestoreState) {
            executor.restoreState(progress)
            return true
        } else {
            logger.info("doesn't support restore state.")
            return false
        }
    }
}

val DownloadTask.currentStatus get() = stateFlow.value
val DownloadTask.isIdle get() = stateFlow.value is DownloadStatus.IDLE
val DownloadTask.isDownloading get() = stateFlow.value is DownloadStatus.DOWNLOADING
val DownloadTask.isPaused get() = stateFlow.value is DownloadStatus.PAUSED
val DownloadTask.isFailed get() = stateFlow.value is DownloadStatus.ERROR
val DownloadTask.isCancelled get() = stateFlow.value is DownloadStatus.CANCELED
val DownloadTask.isFinished get() = stateFlow.value is DownloadStatus.FINISHED

/**
 * 此方法根据任务当前状态映射成任务所处队列类型.
 *
 * 每个任务状态可以唯一对应一个队列类型, 以表示当前下载状态的任务处于什么类型的下载队列.
 * 映射结果并不100%准确，原因在于当任务不论处于什么状态，只要处于[TaskScheduler.pending]队列就得算作[TaskQueueType.QUEUED]
 * 比如：
 * 1. 新任务或是从记录恢复下载进度从而生成的任务, 初始状态都是[DownloadStatus.IDLE]
 * 2. 当前任务下载失败或取消下载，再次重新下载，其初始状态是FAILED/CANCELED
 * 要任务执行，就要加入[TaskScheduler.pending]队列，在下载开始之前不论任务是什么状态都得算作[TaskQueueType.QUEUED]。
 *
 * 所以映射结果无法包含[TaskQueueType.QUEUED]，且某些状态下映射结果是错误的，因为它们都得计入[TaskQueueType.QUEUED]。
 *
 */
fun DownloadStatus.mapping2QueueType() =
    when (this) {
        DownloadStatus.IDLE -> TaskQueueType.IDLE
        DownloadStatus.STARTING -> TaskQueueType.RUNNING
        is DownloadStatus.DOWNLOADING -> TaskQueueType.RUNNING
        DownloadStatus.PAUSED -> TaskQueueType.PAUSED
        DownloadStatus.CANCELED -> TaskQueueType.CANCELED
        DownloadStatus.FINISHED -> TaskQueueType.FINISHED
        is DownloadStatus.ERROR -> TaskQueueType.FAILED
    }
