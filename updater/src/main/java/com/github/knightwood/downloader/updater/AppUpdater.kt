package com.github.knightwood.downloader.updater

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentManager
import com.github.knightwood.downloader.core.android.Downloader
import com.github.knightwood.downloader.core.android.IDownloadedHandler
import com.github.knightwood.downloader.core.android.model.AndroidDownloadParams
import com.github.knightwood.downloader.core.android.model.AndroidPath
import com.github.knightwood.downloader.core.android.service.DownloadServiceLinker
import com.github.knightwood.downloader.core.model.isTerminalState
import com.github.knightwood.downloader.core.task.DownloadTask
import com.github.knightwood.downloader.core.task.currentStatus
import com.github.knightwood.downloader.ui.util.OperateLocker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okio.Path
import okio.Path.Companion.toOkioPath

object AppUpdater {
    private val TAG = "AppUpdater"
    private val scope = CoroutineScope(Dispatchers.IO)
    private var _currTaskFlow: MutableStateFlow<DownloadTask?> = MutableStateFlow(null)
    val currTaskFlow: StateFlow<DownloadTask?> get() = _currTaskFlow
    val currTask: DownloadTask? get() = _currTaskFlow.value
    private var currTaskParamsCache: ApkUpdaterParams? = null
    private var currTaskConfCache: AndroidDownloadParams? = null
    private val locker = OperateLocker(scope)
    val isRunning: Boolean
        get() {
            val currTask = _currTaskFlow.value ?: return false
            return !currTask.currentStatus.isTerminalState
        }

    fun createApkDownloadFilePath(context: Context,name:String): AndroidPath.Jvm {
        val cacheDir = context.externalCacheDir ?: context.cacheDir
        val dirPath = cacheDir.toOkioPath().resolve("apks")
        if (!dirPath.toFile().exists()){
            dirPath.toFile().mkdirs()
        }
        return AndroidPath.Jvm(dirPath.resolve(name))
    }

    object TaskControl {
        //<editor-fold desc="任务状态控制">

        fun pause() {
            locker.launchTaskOp() {
                runCatching { currTask?.pause() }
                    .onFailure { Log.e(TAG, "暂停任务失败: ${it.message}") }
            }
        }

        fun resume() {
            locker.launchTaskOp() {
                runCatching { currTask?.resume() }
                    .onFailure { Log.e(TAG, "恢复任务失败: ${it.message}") }
            }
        }

        fun cancel() {
            locker.launchTaskOp() {
                runCatching {
                    val currTask = currTask ?: return@launchTaskOp
                    currTask.cancel() // 正在执行的,需要取消下载
                    clearTask()
                }.onFailure { Log.e(TAG, "取消任务失败: ${it.message}") }
            }
        }

        fun install(context: Context) {
            locker.launchTaskOp() {
                runCatching {
                    val path = currTaskConfCache?.taskParams?.path ?: return@launchTaskOp
                    ApkUtil.installApk(context, Downloader.authorities(context), path)
                }.onFailure { Log.e(TAG, "安装任务失败: ${it.message}") }
            }
        }

        fun retry() {
            locker.launchTaskOp() {
                runCatching {
                    val currTask = currTask ?: return@launchTaskOp
                    currTask.execute()
                }.onFailure { Log.e(TAG, "重试任务失败: ${it.message}") }
            }
        }

        //</editor-fold>
    }

    fun clearTask() {
        if (!isRunning) {
            _currTaskFlow.value = null
            currTaskParamsCache = null
            currTaskConfCache = null
        }
    }

    suspend fun secretDownload(
        context: ComponentActivity,
        paramProvider: () -> ApkUpdaterParams,
    ) {
        if (isRunning) return
        clearTask()
        val params = paramProvider()

        val linker = Downloader.getDownloadServiceLinker(context, context, false)
        val conf = params.mapToAndroidDownloadParam(context = context)
        val task = linker.runTaskDirectly(conf)
        _currTaskFlow.value = task
        currTaskParamsCache = params
        currTaskConfCache = conf
    }

    fun secretDownload(
        context: Context,
        linker: DownloadServiceLinker,
        paramProvider: () -> ApkUpdaterParams,
    ) {
        if (isRunning) return
        clearTask()
        val params = paramProvider()
        val conf = params.mapToAndroidDownloadParam(context = context)
        val task = linker.runTaskDirectly(conf)
        _currTaskFlow.value = task
        currTaskParamsCache = params
        currTaskConfCache = conf
    }

    /**
     * 显示更新对话框
     *
     * @param fm
     */
    fun display(fm: FragmentManager) {
        AppUpdateDialog.display(fm, currTaskParamsCache ?: return)
    }

    /**
     * 显示更新对话框
     *
     * @param fm
     */
    fun display(fm: FragmentManager, paramProvider: () -> ApkUpdaterParams?) {
        AppUpdateDialog.display(fm, paramProvider() ?: currTaskParamsCache ?: return)
    }

}
fun ApkUpdaterParams.mapToAndroidDownloadParam(context: Context): AndroidDownloadParams {
    val apk = this
    check(apk.url.isNotBlank()) { "apk url is blank" }
    check(apk.appName.isNotBlank()) { "apk name is blank" }
    check(apk.appName.endsWith(".apk")) { "apk name must end with .apk" }
    return AndroidDownloadParams().let {
        it.file {
            name = apk.appName
            url = apk.url
            size = apk.size
            md5 = apk.md5
        }
        it.task {
            if (apk.autoInstallAfterDownload) {
                postHandler = IDownloadedHandler { context, path, id, conf ->
                    ApkUtil.installApk(context, Downloader.authorities(context), path!!)
                }
            }
            enableNotification = apk.enableNotify
            mNotifier = ApkUpdateNotifierImpl()
            path = AppUpdater.createApkDownloadFilePath(context, apk.appName)
        }
    }
}
