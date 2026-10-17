package com.github.knightwood.downloader.core.task

import com.github.knightwood.downloader.core.executor.DownloadExecutor
import com.github.knightwood.downloader.core.executor.FileStorageImpl
import com.github.knightwood.downloader.core.executor.IDownloadExecutor
import com.github.knightwood.downloader.core.model.TaskParams
import com.github.knightwood.downloader.core.model.DownloadStatus
import com.github.knightwood.downloader.core.util.FileUtil
import com.github.knightwood.downloader.core.util.LogUtil
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
import okio.Path.Companion.toOkioPath
import java.io.File

class DownloadTask(
    val id: Int,
    val conf: TaskParams,
    val executor: IDownloadExecutor = DownloadExecutor(),
    val produceFile: () -> File,
) {
    private val TAG = "DownloadTask#$id"
    private val _stateFlow: MutableStateFlow<DownloadStatus> = MutableStateFlow<DownloadStatus>(DownloadStatus.IDLE)
    val stateFlow: StateFlow<DownloadStatus> get() = _stateFlow

    //最后的下载状态
    private var lastDownloadingStatus: DownloadStatus.DOWNLOADING? = null

    //当前下载进度
    val progress: Flow<Double> = _stateFlow
        .filter { it is DownloadStatus.DOWNLOADING }
        .map { status ->
            if (status is DownloadStatus.DOWNLOADING) {
                val max = status.length
                val progress = status.progress
                val curr = (progress / max * 100.0)
                LogUtil.i(TAG, "downloading max: $max --- progress: $progress")
                curr
            } else {
                0.0
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
        val cache = conf.md5?.let { FileUtil.findCache(produceFile().toOkioPath(), it) }
        if (cache != null) {
            _stateFlow.value = DownloadStatus.FINISHED(cache)
            return
        }
        if (conf.deleteLastFile) {
            produceFile().delete()
        }
        executor.download(FileStorageImpl(produceFile), conf.url)
            .collect { state ->
                _stateFlow.value = state
                if (state is DownloadStatus.DOWNLOADING) {
                    lastDownloadingStatus = state
                }
            }
    }

    /**
     * 暂停下载,
     * 调用此方法后会立即挂起协程, 直到任务状态被更新到[DownloadStatus.PAUSED]
     */
    suspend fun pause() {
        executor.pause()
        _stateFlow.first { status ->
            status is DownloadStatus.PAUSED
        }
    }

    /**
     * 恢复任务,
     * 调用此方法后会立即挂起协程, 直到任务状态被更新到[DownloadStatus.DOWNLOADING]
     */
    suspend fun resume() {
        executor.resume()
        _stateFlow.first { status ->
            status is DownloadStatus.DOWNLOADING
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
     * @return 如果任务处于idle/starting状态，可能返回null
     */
    fun saveState(): DownloadStatus.DOWNLOADING? {
        if (isDownloading) {
            throw IllegalStateException("需停止/取消/暂停下载任务后调用此方法")
        }
        return lastDownloadingStatus
    }

    /**
     * 在执行[runOn]、[execute]之前调用此方法恢复任务进度
     * 只有分块下载时有用
     *
     * ```
     * val task = DownloadTask()
     * task.resumeFrom(state)
     *     .execute()
     * ```
     */
    fun restoreState(state: DownloadStatus.DOWNLOADING): DownloadTask {
        if (!isIdle) {
            throw IllegalStateException("only in IDLE state can be resumed.")
        }
        executor.restoreState(state)
        conf.deleteLastFile = false
        return this
    }
}

val DownloadTask.isIdle get() = stateFlow.value is DownloadStatus.IDLE
val DownloadTask.isDownloading get() = stateFlow.value is DownloadStatus.DOWNLOADING
val DownloadTask.isPaused get() = stateFlow.value is DownloadStatus.PAUSED
val DownloadTask.isFailed get() = stateFlow.value is DownloadStatus.ERROR
val DownloadTask.isCancelled get() = stateFlow.value is DownloadStatus.CANCELED
