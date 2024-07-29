package com.f_libs.appupdate.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import com.f_libs.appupdate.R
import com.f_libs.appupdate.base.bean.DownloadStatus
import com.f_libs.appupdate.config.Constant
import com.f_libs.appupdate.manager.DownloadManager
import com.f_libs.appupdate.util.ApkUtil
import com.f_libs.appupdate.util.FileUtil
import com.f_libs.appupdate.util.LogUtil
import com.f_libs.appupdate.util.NotificationUtil
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ProjectName: AppUpdate PackageName: com.azhon.appupdate.service
 * FileName: DownloadService CreateDate: 2022/4/7 on 11:42 Desc:
 *
 * 静默下载策略：启动静默下载配置，构造应用描述信息，启动服务。 服务启动后，查找是否有下载好的apk，如果有，则结束下载。 如果没有，则启动下载。
 * 调启下载界面后，如果已存在相同版本和md5的安装包，则直接可以显示下载完成并进行安装
 *
 * 静默下载的文件专门存放在一个目录下，以md5值为文件名。 普通下载的文件存在另一个文件夹下。
 *
 * @author knightwood
 * @author azhon
 */

class DownloadService : Service() {
    private val scope: CoroutineScope =
        CoroutineScope(Dispatchers.IO) + SupervisorJob() + CoroutineName(Constant.COROUTINE_NAME)

    private lateinit var manager: DownloadManager
    private var lastProgress = 0

    private val isBackDownload
        get() = manager.isBackDownload
    private val config: DownloadManager.DownloadConfig
        get() = manager.config

    companion object {
        private const val TAG = "DownloadService"

        fun startService(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                context.startForegroundService(intent)
//            } else {
            context.startService(intent)
//            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            return START_NOT_STICKY
        }
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
//            startForeground(Constant.DEFAULT_NOTIFY_ID, NotificationUtil.getNotification(this))
        init()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun init() {
        manager = try {
            DownloadManager.getInstance()
        } catch (e: IllegalArgumentException) {
            LogUtil.e(TAG, "An exception occurred by DownloadManager=null,please check your code!")
            return
        }
        FileUtil.createDirDirectory(config.downloadPath)

        val enable = NotificationUtil.notificationEnable(this@DownloadService)
        LogUtil.d(
            TAG,
            if (enable) "Notification switch status: opened" else "Notification switch status: closed"
        )

        //查找有无静默下载的文件
        ApkUtil.findBackDownloadApk(this.applicationContext, config.apkMD5)?.let {
            val status = checkOrDownload(it, false)
            if (status is DownloadStatus.IDLE) {
                return@let null
            } else {
                return@let it
            }
        } ?: let {
            //如果没有静默下载的文件
            val file = File(config.downloadPath, config.apkName)
            if (file.exists() && isBackDownload) {
                //默认下载目录可能有下载好的文件,而且当前是静默下载
                //将下载好的文件移动到静默下载目录
                if (checkApkMd5(file)) {
                    FileUtil.moveTo(
                        file, ApkUtil.getDefaultBackDownloadCachePath(
                            this.applicationContext, true
                        )
                    )?.let {
                        val statue = DownloadStatus.Done(it)
                        scope.launch {
                            withContext(Dispatchers.Main) {
                                downloadSuccess(statue)//install apk
                            }
                        }
                    }

                } else {
                    download()
                }
            } else {
                //没有下载好的文件，或现在不是静默下载
                checkOrDownload(file, true)
            }
        }
    }

    /**
     * 检查文件是否存在，如果存在则直接安装 如果不存在，则判断是否需要下载
     */
    private fun checkOrDownload(file: File, download: Boolean): DownloadStatus {
        if (checkApkMd5(file)) {
            LogUtil.d(TAG, "Apk already exist and install it directly.")
            val statue = DownloadStatus.Done(file)
            scope.launch {
                withContext(Dispatchers.Main) {
                    downloadSuccess(statue)//install apk
                }
            }
            return statue
        } else {
            if (download) {
                LogUtil.d(TAG, "Apk don't exist will start download.")
                download()
                return DownloadStatus.Start
            } else {
                return DownloadStatus.IDLE
            }
        }
    }

    /**
     * Check whether the Apk has been downloaded, don't download again
     */
    private fun checkApkMd5(file: File): Boolean {
        if (config.apkMD5.isBlank()) {
            return false
        }
        if (file.exists()) {
            return FileUtil.md5(file).equals(config.apkMD5, ignoreCase = true)
        }
        return false
    }

    @Synchronized
    private fun download() {
        if (manager.downloading) {
            LogUtil.e(TAG, "Currently downloading, please download again!")
            return
        }
        scope.launch {
            withContext(Dispatchers.Main) {
                config.download(
                    if (isBackDownload) ApkUtil.getDefaultBackDownloadCachePath(applicationContext)
                    else null
                )
                    .collect {
                        when (it) {
                            is DownloadStatus.Start -> downloadStart(it)
                            is DownloadStatus.Downloading -> currentDownloading(it)
                            is DownloadStatus.Done -> downloadSuccess(it)
                            is DownloadStatus.Cancel -> this@DownloadService.downloadCancel(it)
                            is DownloadStatus.Error -> downloadError(it)
                            else -> {}
                        }

                    }
            }
        }
    }

    private suspend fun notifyListener(status: DownloadStatus) {
        manager.downloadStateFlow.emit(status)
        //静默下载不通知进度和下载状态
        when (status) {
            is DownloadStatus.Start -> {
                config.onDownloadListeners.forEach {
                    it.start()
                }
            }

            is DownloadStatus.Downloading -> {
                config.onDownloadListeners.forEach {
                    it.downloading(status.max, status.progress)
                }
            }

            is DownloadStatus.Done -> {
                config.onDownloadListeners.forEach { it.done(status.apk) }
            }

            is DownloadStatus.Cancel -> {
                config.onDownloadListeners.forEach { it.cancel() }
            }

            is DownloadStatus.Error -> {
                config.onDownloadListeners.forEach { it.error(status.e) }
            }

            DownloadStatus.IDLE, DownloadStatus.End -> {}
        }
    }

    private suspend fun downloadStart(downloadStatus: DownloadStatus.Start) {
        LogUtil.i(TAG, "download start")
        notifyListener(downloadStatus)
        if (config.showBgdToast && !isBackDownload) {
            Toast.makeText(this, R.string.app_update_background_downloading, Toast.LENGTH_SHORT)
                .show()
        }
        if (config.showNotification && !isBackDownload) {
            NotificationUtil.showNotification(
                this@DownloadService, config.smallIcon,
                resources.getString(R.string.app_update_start_download),
                resources.getString(R.string.app_update_start_download_hint)
            )
        }
    }

    private suspend fun currentDownloading(status: DownloadStatus.Downloading) {
        notifyListener(status)
        if (config.showNotification) {
            val max: Int = status.max
            val progress: Int = status.progress
            val curr = (progress / max.toDouble() * 100.0).toInt()
            if (curr == lastProgress) return
            LogUtil.i(TAG, "downloading max: $max --- progress: $progress")
            lastProgress = curr
            val content = if (curr < 0) "" else "$curr%"
            if (!isBackDownload) {
                NotificationUtil.showProgressNotification(
                    this@DownloadService, config.smallIcon,
                    resources.getString(R.string.app_update_start_downloading),
                    content, if (max == -1) -1 else 100, curr
                )
            }
        }
    }

    private suspend fun downloadSuccess(status: DownloadStatus.Done) {
        val apk = status.apk
        LogUtil.d(TAG, "apk downloaded to ${apk.path}")
        //检查hash值
        if (config.apkMD5.isNotBlank() && !FileUtil.md5(apk)
                .equals(config.apkMD5, ignoreCase = true)
        ) {
            LogUtil.e(TAG, "Apk downloaded but md5 is wrong, delete it!")
            apk.delete()
            val errStatus = DownloadStatus.Error(IllegalStateException("md5 error"))
            downloadError(errStatus, false)
            return
        }
        if (isBackDownload) {
            FileUtil.rename(apk, config.apkMD5 + ".apk")
            manager.isBackDownload = false
        } else {
            //If it is android Q (api=29) and above, (showNotification=false) will also send a
            // download completion notification
            if (config.showNotification || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                NotificationUtil.showDoneNotification(
                    this@DownloadService, config.smallIcon,
                    resources.getString(R.string.app_update_download_completed),
                    resources.getString(R.string.app_update_click_hint),
                    Constant.AUTHORITIES!!, apk
                )
            }
            if (config.jumpInstallPage) {
                ApkUtil.installApk(this@DownloadService, Constant.AUTHORITIES!!, apk)
            }
        }
        notifyListener(status)
        clearState()
    }

    private suspend fun downloadCancel(downloadStatus: DownloadStatus.Cancel) {
        LogUtil.i(TAG, "download cancel")
        if (config.showNotification && !isBackDownload) {
            NotificationUtil.cancelNotification(this@DownloadService)
        }
        notifyListener(downloadStatus)

        clearState()
    }

    private suspend fun downloadError(
        status: DownloadStatus.Error,
        continueDownload: Boolean = true,
    ) {
        LogUtil.e(TAG, "download error: ${status.e}")
        if (config.showNotification && !isBackDownload && continueDownload) {
            NotificationUtil.showErrorNotification(
                this@DownloadService, config.smallIcon,
                resources.getString(R.string.app_update_download_error),
                resources.getString(R.string.app_update_continue_downloading),
            )
        }
        notifyListener(status)
        //静默下载时，如果出错，不再进行重试，直接结束
        if (manager.isBackDownload) {
            clearState()
        }
    }

    /**
     * 清除状态，关闭资源
     */
    private fun clearState() {
        scope.launch {
            delay(500)
            manager.release()
            scope.cancel("DownloadManager has close")
            stopSelf()
        }
    }
}