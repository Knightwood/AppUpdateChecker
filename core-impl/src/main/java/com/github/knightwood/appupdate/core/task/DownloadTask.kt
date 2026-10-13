package com.github.knightwood.appupdate.core.task

import com.github.knightwood.appupdate.core.base.bean.DownloadStatus
import com.github.knightwood.appupdate.core.base.bean.DownloadTaskConf
import com.github.knightwood.appupdate.core.service.mpl
import com.github.knightwood.appupdate.core.util.FileUtil
import com.github.knightwood.appupdate.core.util.LogUtil
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.supervisorScope
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File
import java.util.UUID

class DownloadTask(
    val id: String = UUID.randomUUID().toString(),
    val conf: DownloadTaskConf,
    val executor: IDownloadExecutor = DownloadExecutor(),
    val apkUrl: String,
    val produceFile: () -> File,
) {
    private val TAG = "DownloadTask#$id"
    val stateFlow: MutableStateFlow<DownloadStatus> = MutableStateFlow<DownloadStatus>(DownloadStatus.IDLE)
    val isDownloading get() = stateFlow.value is DownloadStatus.Downloading
    val progress: Flow<Double> = stateFlow
        .filter { it is DownloadStatus.Downloading }
        .map { status ->
            if (status is DownloadStatus.Downloading) {
                val max = status.max
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
            flow {
                emit(conf.md5?.let { findCache(it) })
            }.collect { cache ->
                if (cache == null) {
                    //删除不完整(上次未完成下载)的文件
                    produceFile().delete()
                    executor
                        .download(FileStorageImpl(produceFile), apkUrl)
                        .collect {
                            stateFlow.value = it
                        }
                } else {
                    LogUtil.d(TAG, "Apk already exist and install it directly.")
                    stateFlow.value = DownloadStatus.Done(cache)
                }
            }
        }
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
                        checkApkMd5(f, target)
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
    private fun checkApkMd5(file: File, target: String): Boolean {
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
