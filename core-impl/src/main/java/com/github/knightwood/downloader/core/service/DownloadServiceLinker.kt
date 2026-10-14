package com.github.knightwood.downloader.core.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.widget.Toast
import com.github.knightwood.downloader.core.R
import com.github.knightwood.downloader.core.model.TaskDesc
import com.github.knightwood.downloader.core.model.Constant
import com.github.knightwood.downloader.core.task.DownloadTask
import com.github.knightwood.downloader.core.util.ApkUtil
import com.github.knightwood.downloader.core.util.FileUtil
import com.github.knightwood.downloader.core.util.LogUtil
import com.github.knightwood.downloader.core.util.NotificationUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

abstract class DownloadServiceLinker : Binder() {
    abstract val service: DownloadService

    abstract fun submitTask(
        id: Int,
        conf: TaskDesc,
    ):DownloadTask

    abstract fun cancelTask(id: Int)

    abstract fun stopService()

}
//
//class mpl : Service() {
//
//    private lateinit var manager: DownloadManager
//    private var lastProgress = 0
//
//    private val isBackDownload
//        get() = manager.isBackDownload
//    private val config: DownloadManager.DownloadConfig
//        get() = manager.config
//
//    companion object {
//        private const val TAG = "DownloadService"
//
//        fun startService(context: Context) {
//            val intent = Intent(context, DownloadService::class.java)
////            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
////                context.startForegroundService(intent)
////            } else {
//            context.startService(intent)
////            }
//        }
//    }
//
//
//    private fun init() {
//        manager = try {
//            DownloadManager.getInstance()
//        } catch (e: IllegalArgumentException) {
//            LogUtil.e(TAG, "An exception occurred by DownloadManager=null,please check your code!")
//            return
//        }
//        FileUtil.createDirDirectory(config.downloadPath)
//
//        val enable = NotificationUtil.notificationEnable(this@DownloadService)
//        LogUtil.d(
//            TAG,
//            if (enable) "Notification switch status: opened" else "Notification switch status: closed"
//        )
//
//        //查找有无静默下载的文件
//        ApkUtil.findBackDownloadApk(this.applicationContext, config.apkMD5)?.let {
//            val status = checkOrDownload(it, false)
//            if (status is TaskState.IDLE) {
//                return@let null
//            } else {
//                return@let it
//            }
//        } ?: let {
//            //如果没有静默下载的文件
//            val file = File(config.downloadPath, config.apkName)
//            if (file.exists() && isBackDownload) {
//                //默认下载目录可能有下载好的文件,而且当前是静默下载
//                //将下载好的文件移动到静默下载目录
//                if (checkApkMd5(file)) {
//                    FileUtil.moveTo(
//                        file, ApkUtil.getDefaultBackDownloadCachePath(
//                            this.applicationContext, true
//                        )
//                    )?.let {
//                        val statue = TaskState.FINISHED(it)
//                        scope.launch {
//                            withContext(Dispatchers.Main) {
//                                downloadSuccess(statue)//install apk
//                            }
//                        }
//                    }
//
//                } else {
//                    download()
//                }
//            } else {
//                //没有下载好的文件，或现在不是静默下载
//                checkOrDownload(file, true)
//            }
//        }
//    }
//
//*
//     * 检查文件是否存在，如果存在则直接安装 如果不存在，则判断是否需要下载
//
//
//    private fun checkOrDownload(file: File, download: Boolean): TaskState {
//        if (checkApkMd5(file)) {
//            LogUtil.d(TAG, "Apk already exist and install it directly.")
//            val statue = TaskState.FINISHED(file)
//            scope.launch {
//                withContext(Dispatchers.Main) {
//                    downloadSuccess(statue)//install apk
//                }
//            }
//            return statue
//        } else {
//            if (download) {
//                LogUtil.d(TAG, "Apk don't exist will start download.")
//                download()
//                return TaskState.STARTING
//            } else {
//                return TaskState.IDLE
//            }
//        }
//    }
//
//*
//     * Check whether the Apk has been downloaded, don't download again
//
//
//    private fun checkApkMd5(file: File): Boolean {
//        if (config.apkMD5.isBlank()) {
//            return false
//        }
//        if (file.exists()) {
//            return FileUtil.md5(file).equals(config.apkMD5, ignoreCase = true)
//        }
//        return false
//    }
//
//    @Synchronized
//    private fun download() {
//        if (manager.downloading) {
//            LogUtil.e(TAG, "Currently downloading, please download again!")
//            return
//        }
//        scope.launch {
//            withContext(Dispatchers.Main) {
//                config.download(
//                    if (isBackDownload) ApkUtil.getDefaultBackDownloadCachePath(applicationContext)
//                    else null
//                )
//                    .collect {
//                        when (it) {
//                            is TaskState.STARTING -> downloadStart(it)
//                            is TaskState.DOWNLOADING -> currentDownloading(it)
//                            is TaskState.FINISHED -> downloadSuccess(it)
//                            is TaskState.DROPPED -> this@DownloadService.downloadCancel(it)
//                            is TaskState.ERROR -> downloadError(it)
//                            else -> {}
//                        }
//
//                    }
//            }
//        }
//    }
//
//    private suspend fun notifyListener(status: TaskState) {
//        manager.downloadStateFlow.emit(status)
//        //静默下载不通知进度和下载状态
//        when (status) {
//            is TaskState.STARTING -> {
//                config.onDownloadListeners.forEach {
//                    it.start()
//                }
//            }
//
//            is TaskState.DOWNLOADING -> {
//                config.onDownloadListeners.forEach {
//                    it.downloading(status.length, status.progress)
//                }
//            }
//
//            is TaskState.FINISHED -> {
//                config.onDownloadListeners.forEach { it.done(status.path) }
//            }
//
//            is TaskState.DROPPED -> {
//                config.onDownloadListeners.forEach { it.cancel() }
//            }
//
//            is TaskState.ERROR -> {
//                config.onDownloadListeners.forEach { it.error(status.e) }
//            }
//
//            TaskState.IDLE, TaskState.End -> {}
//        }
//    }
//
//    private suspend fun downloadStart(taskState: TaskState.STARTING) {
//        LogUtil.i(TAG, "download start")
//        notifyListener(taskState)
//        if (config.showBgdToast && !isBackDownload) {
//            Toast.makeText(this, R.string.app_update_background_downloading, Toast.LENGTH_SHORT)
//                .show()
//        }
//        if (config.showNotification && !isBackDownload) {
//            NotificationUtil.showNotification(
//                this@DownloadService, config.smallIcon,
//                ApkUtil.getAppName(this@DownloadService),
//                resources.getString(R.string.app_update_start_download) + "/n" +
//                        resources.getString(R.string.app_update_start_download_hint)
//            )
//        }
//    }
//
//    private suspend fun currentDownloading(status: TaskState.DOWNLOADING) {
//        notifyListener(status)
//        if (config.showNotification) {
//            val max: Int = status.length
//            val progress: Int = status.progress
//            val curr = (progress / max.toDouble() * 100.0).toInt()
//            if (curr == lastProgress) return
//            LogUtil.i(TAG, "downloading max: $max --- progress: $progress")
//            lastProgress = curr
//            val content = if (curr < 0) "" else "$curr%"
//            if (!isBackDownload) {
//                NotificationUtil.showProgressNotification(
//                    this@DownloadService, config.smallIcon,
//                    ApkUtil.getAppName(this@DownloadService),
//                    resources.getString(R.string.app_update_start_downloading) + "\n" + content,
//                    if (max == -1) -1 else 100, curr
//                )
//            }
//        }
//    }
//
//    private suspend fun downloadSuccess(status: TaskState.FINISHED) {
//        val apk = status.path
//        LogUtil.d(TAG, "apk downloaded to ${apk.path}")
//        //检查hash值
//        if (config.apkMD5.isNotBlank() && !FileUtil.md5(apk)
//                .equals(config.apkMD5, ignoreCase = true)
//        ) {
//            LogUtil.e(TAG, "Apk downloaded but md5 is wrong, delete it!")
//            apk.delete()
//            val errStatus = TaskState.ERROR(IllegalStateException("md5 error"))
//            downloadError(errStatus, false)
//            return
//        }
//        if (isBackDownload) {
//            FileUtil.rename(apk, config.apkMD5 + ".apk")
//            manager.isBackDownload = false
//        } else {
//            //If it is android Q (api=29) and above, (showNotification=false) will also send a
//            // download completion notification
//            if (config.showNotification || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                NotificationUtil.showDoneNotification(
//                    this@DownloadService, config.smallIcon,
//                    ApkUtil.getAppName(this@DownloadService),
//                    resources.getString(R.string.app_update_download_completed) + "\n" +
//                            resources.getString(R.string.app_update_click_hint),
//                    Constant.AUTHORITIES!!, apk
//                )
//            }
//            if (config.jumpInstallPage) {
//                ApkUtil.installApk(this@DownloadService, Constant.AUTHORITIES!!, apk)
//            }
//        }
//        notifyListener(status)
//        clearState()
//    }
//
//    private suspend fun downloadCancel(taskState: TaskState.DROPPED) {
//        LogUtil.i(TAG, "download cancel")
//        if (config.showNotification && !isBackDownload) {
//            NotificationUtil.cancelNotification(this@DownloadService)
//        }
//        notifyListener(taskState)
//
//        clearState()
//    }
//
//    private suspend fun downloadError(
//        status: TaskState.ERROR,
//        continueDownload: Boolean = true,
//    ) {
//        LogUtil.e(TAG, "download error: ${status.e}")
//        if (config.showNotification && !isBackDownload && continueDownload) {
//            NotificationUtil.showErrorNotification(
//                this@DownloadService, config.smallIcon,
//                ApkUtil.getAppName(this@DownloadService),
//                resources.getString(R.string.app_update_download_error) + "\n" + resources.getString(R.string.app_update_continue_downloading),
//            )
//        }
//        notifyListener(status)
//        //静默下载时，如果出错，不再进行重试，直接结束
//        if (manager.isBackDownload) {
//            clearState()
//        }
//    }
//
//*
//     * 清除状态，关闭资源
//
//
//    private fun clearState() {
//        scope.launch {
//            delay(500)
//            manager.release()
//            scope.cancel("DownloadManager has close")
//            stopSelf()
//        }
//    }
//}
