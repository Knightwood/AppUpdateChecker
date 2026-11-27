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
import com.github.knightwood.downloader.core.util.FileUtil
import com.github.knightwood.downloader.updater.model.ApkDownloadParams
import com.github.knightwood.downloader.updater.util.ApkUtil
import com.github.knightwood.downloader.updater.util.OperateLocker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okio.Path
import okio.Path.Companion.toOkioPath

object AppUpdater {
    private const val TAG = "AppUpdater"
    private val scope = CoroutineScope(Dispatchers.IO)
    private var _currUpdateTaskHolderFlow: MutableStateFlow<AppUpdateTaskHolder?> = MutableStateFlow(null)
    val currTaskFlow: Flow<DownloadTask?> get() = _currUpdateTaskHolderFlow.map { it?.currTask }
    val currTaskHolder get() = _currUpdateTaskHolderFlow.value
    val currTask get() = _currUpdateTaskHolderFlow.value?.currTask
    val isRunning: Boolean
        get() {
            return _currUpdateTaskHolderFlow.value?.isRunning ?: false
        }

    fun clearTask() {
        if (!isRunning) {
            scope.launch {
                _currUpdateTaskHolderFlow.value?.clear()
                _currUpdateTaskHolderFlow.value = null
            }
        }
    }

    object TaskControl {
        fun pause() {
            currTaskHolder?.let {
                scope.launch {
                    it.taskControl.pause()
                }
            } ?: Log.w(TAG, "currTaskHolder is null")
        }

        fun resume() {
            currTaskHolder?.let {
                scope.launch {
                    it.taskControl.resume()
                }
            } ?: Log.w(TAG, "currTaskHolder is null")
        }

        fun cancel() {
            currTaskHolder?.let {
                scope.launch {
                    it.taskControl.cancel()
                }
            } ?: Log.w(TAG, "currTaskHolder is null")
        }

        fun install(context: Context) {
            currTaskHolder?.let {
                scope.launch {
                    it.taskControl.install(context)
                }
            } ?: Log.w(TAG, "currTaskHolder is null")
        }

        fun retry() {
            currTaskHolder?.let {
                scope.launch {
                    it.taskControl.retry(scope)
                }
            } ?: Log.w(TAG, "currTaskHolder is null")
        }
    }

    //<editor-fold desc="发起更新&显示弹窗">
    suspend fun secretDownload(
        context: ComponentActivity,
        paramProvider: () -> ApkDownloadParams,
    ) {
        if (isRunning) return
        clearTask()
        val params = paramProvider()

        val linker = Downloader.getDownloadServiceLinker(context, context, false)
        val task = AppUpdateTaskHolder.obtain(context, params).attachTask(linker)
        _currUpdateTaskHolderFlow.value = task
    }

    fun secretDownload(
        context: Context,
        linker: DownloadServiceLinker,
        paramProvider: () -> ApkDownloadParams,
    ) {
        if (isRunning) return
        clearTask()
        val params = paramProvider()
        val task = AppUpdateTaskHolder.obtain(context, params).attachTask(linker)
        _currUpdateTaskHolderFlow.value = task

    }

    /**
     * 显示更新对话框，显示弹窗以展示下载进度
     * 如果当前没有正在下载的更新，则不会显示弹窗
     *
     * @param fm
     */
    fun display(fm: FragmentManager) {
        val taskHolder = currTaskHolder ?: return
        AppUpdateDialog.display(fm, taskHolder.apkDownloadParams)
    }

    /**
     * 显示更新对话框，用于当前无下载任务，显示弹窗以提供下载功能
     *
     * 在显示更新对话框之前你可能需要检查将要更新的版本是否比当前版本新
     * ```
     * val response = Repo.checkUpdateVersion() // 请求网络获取最新版本信息
     * // 判断版本是否需要更新并显示更新对话框
     * if (response.data.versionCode > ApkUtil.getVersionCode(context))
     *     AppUpdater.display(fm, paramProvider)
     * }
     * ```
     *
     * @param fm
     * @param paramProvider 提供app下载更新配置
     */
    fun display(fm: FragmentManager, paramProvider: () -> ApkDownloadParams) {
        AppUpdateDialog.display(fm, paramProvider())
    }

    //</editor-fold>

    //<editor-fold desc="工具方法">
    /**
     * Returns the cache directory for storing downloaded APK files.
     *
     * @param context
     * @return the path
     */
    fun getCacheDir(context: Context): Path {
        val cacheDir = context.externalCacheDir ?: context.cacheDir
        val dirPath = cacheDir.toOkioPath().resolve("apks")
        Log.d(TAG, "cacheDir: ${dirPath.toFile().absolutePath}")
        return dirPath
    }

    /**
     * 删除apk下载缓存目录
     *
     * @param context
     */
    fun rmApkCache(context: Context) {
        val dirPath = getCacheDir(context)
        if (dirPath.toFile().exists()) {
            dirPath.toFile().deleteRecursively()
        }
    }

    /**
     * Creates a new apk download file path
     *
     * @param context
     * @param name
     * @return the jvm
     */
    fun createApkDownloadFilePath(context: Context, name: String): AndroidPath.Jvm {
        val dirPath = getCacheDir(context)
        if (!dirPath.toFile().exists()) {
            dirPath.toFile().mkdirs()
        }
        return AndroidPath.Jvm(dirPath.resolve(name))
    }

    internal fun internalInstallApk(path: AndroidPath?, context: Context, md5: String? = null) {
        if (path == null) return
        if (md5 != null && path is AndroidPath.Jvm) {
            val b = FileUtil.checkFileMd5(path.path.toFile(), md5)
            if (!b) {
                Log.e(TAG, "文件md5校验失败")
                return
            }
        }

        ApkUtil.installApk(context, Downloader.authorities(context), path)
    }

    fun mapToAndroidDownloadParam(apkDownloadParams: ApkDownloadParams, context: Context): AndroidDownloadParams {
        check(apkDownloadParams.url.isNotBlank()) { "apk url is blank" }
        check(apkDownloadParams.appName.isNotBlank()) { "apk name is blank" }
        check(apkDownloadParams.appName.endsWith(".apk")) { "apk name must end with .apk" }
        return AndroidDownloadParams().let {
            it.file {
                name = apkDownloadParams.appName
                url = apkDownloadParams.url
                size = apkDownloadParams.size
                md5 = apkDownloadParams.md5
            }
            it.task {
                if (apkDownloadParams.autoInstallAfterDownload) {
                    postHandler = IDownloadedHandler { context, path, id, conf ->
                        internalInstallApk(path, context, conf.fileEntity.md5)
                    }
                }
                enableNotification = apkDownloadParams.enableNotify
                mNotifier = apkDownloadParams.mNotifier ?: ApkUpdateNotifierImpl()
                path = createApkDownloadFilePath(context, apkDownloadParams.appName)
            }
        }
    }
    //</editor-fold>

}


class AppUpdateTaskHolder(
    val apkDownloadParams: ApkDownloadParams,
    val androidDownloadParams: AndroidDownloadParams,
) {
    private val TAG = "AppUpdateTask"
    var currTask: DownloadTask? = null
    private val locker: OperateLocker = OperateLocker()
    val taskControl = TaskControl()
    val isRunning: Boolean
        get() {
            val currTask = this.currTask ?: return false
            return !currTask.currentStatus.isTerminalState
        }

    suspend fun clear() {
        currTask?.cancel()
        currTask = null
    }

    fun attachTask(linker: DownloadServiceLinker): AppUpdateTaskHolder {
        currTask = linker.runTaskDirectly(androidDownloadParams)
        return this
    }

    inner class TaskControl {
        //<editor-fold desc="任务状态控制">

        suspend fun pause() {
            locker.launchTaskOp() {
                runCatching { currTask?.pause() ?: throw IllegalStateException("currTask is null") }
                    .onFailure { Log.e(TAG, "暂停任务失败: ${it.message}") }
            }
        }

        suspend fun resume() {
            locker.launchTaskOp() {
                runCatching { currTask?.resume() ?: throw IllegalStateException("currTask is null") }
                    .onFailure { Log.e(TAG, "恢复任务失败: ${it.message}") }
            }
        }

        suspend fun cancel() {
            locker.launchTaskOp() {
                runCatching {
                    currTask?.cancel() ?: throw IllegalStateException("currTask is null")
                }.onFailure { Log.e(TAG, "取消任务失败: ${it.message}") }
            }
        }

        suspend fun install(context: Context) {
            locker.launchTaskOp() {
                runCatching {
                    val path = androidDownloadParams.taskParams.path
                    AppUpdater.internalInstallApk(path, context, androidDownloadParams.fileEntity.md5)
                }.onFailure { Log.e(TAG, "安装任务失败: ${it.message}") }
            }
        }

        suspend fun retry(scope: CoroutineScope) {
            locker.launchTaskOp() {
                runCatching {
                    currTask?.runOn(scope) ?: throw IllegalStateException("currTask is null")
                }.onFailure { Log.e(TAG, "重试任务失败: ${it.message}") }
            }
        }

        //</editor-fold>
    }

    companion object {
        fun obtain(
            context: Context,
            apkDownloadParams: ApkDownloadParams,
        ): AppUpdateTaskHolder {
            val androidDownloadParams = AppUpdater.mapToAndroidDownloadParam(apkDownloadParams, context)
            return AppUpdateTaskHolder(apkDownloadParams, androidDownloadParams)
        }
    }
}
