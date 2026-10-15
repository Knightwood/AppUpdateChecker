package com.github.knightwood.downloader.core.task

import com.github.knightwood.downloader.core.executor.DownloadExecutor
import com.github.knightwood.downloader.core.executor.FileStorageImpl
import com.github.knightwood.downloader.core.executor.IDownloadExecutor
import com.github.knightwood.downloader.core.model.TaskParams
import com.github.knightwood.downloader.core.model.TaskState
import com.github.knightwood.downloader.core.util.FileUtil
import com.github.knightwood.downloader.core.util.LogUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.supervisorScope
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File
import java.util.UUID

class DownloadTask(
    val id: Int,
    val conf: TaskParams,
    val executor: IDownloadExecutor = DownloadExecutor(),
    val produceFile: () -> File,
) {
    private val TAG = "DownloadTask#$id"
    val stateFlow: MutableStateFlow<TaskState> = MutableStateFlow<TaskState>(TaskState.IDLE)
    val isDownloading get() = stateFlow.value is TaskState.DOWNLOADING
    val progress: Flow<Double> = stateFlow
        .filter { it is TaskState.DOWNLOADING }
        .map { status ->
            if (status is TaskState.DOWNLOADING) {
                val max = status.length
                val progress = status.progress
                val curr = (progress / max * 100.0)
                LogUtil.i(TAG, "downloading max: $max --- progress: $progress")
                curr
            } else {
                0.0
            }
        }

    suspend fun execute() {
        supervisorScope {
            val cache = conf.md5?.let { findCache(it) }
            if (cache == null) {
                //删除不完整(上次未完成下载)的文件
                produceFile().delete()
                executor
                    .download(FileStorageImpl(produceFile), conf.url)
                    .collect { state ->
                        if (state is TaskState.FINISHED) {
                            stateFlow.value = conf.md5?.let { md5: String ->
                                val b = checkFileMd5(state.path.toFile(), md5)
                                if (!b) {
                                    TaskState.ERROR(IllegalStateException("MD5 not equal"))
                                } else {
                                    state
                                }
                            } ?: state
                        } else {
                            stateFlow.value = state
                        }
                    }
            } else {
                LogUtil.d(TAG, "Apk already exist and install it directly.")
                stateFlow.value = TaskState.FINISHED(cache)
            }
        }
    }

    fun pause() {
        executor.pause()
    }

    fun resume() {
        executor.resume()
    }

    fun cancel() {
        executor.release()
    }

    //<editor-fold desc="工具">
    /**
     * 从下载文件保存路径的父路径查找与下载文件具有相同md5的文件
     */
    private fun findCache(target: String): Path? {
        return runCatching {
            produceFile().toOkioPath().parent?.let { parent ->
                FileSystem.SYSTEM.list(parent).first { path ->
                    val f = path.toFile()
                    if (f.exists() && f.isFile) {
                        checkFileMd5(f, target)
                    } else {
                        false
                    }
                }
            }
        }
            .onFailure {
                LogUtil.e(TAG, "findCache: $it")
            }
            .getOrNull()
    }

    /**
     * Check whether the Apk has been downloaded, don't download again
     */
    private fun checkFileMd5(file: File, target: String): Boolean {
        if (target.isBlank()) {
            return false
        }
        if (file.exists()) {
            return FileUtil.md5(file).equals(target, ignoreCase = true)
        }
        return false
    }
    //</editor-fold>
}
