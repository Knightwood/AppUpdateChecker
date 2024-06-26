package com.f_libs.appupdate.manager

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.util.Log
import androidx.annotation.LayoutRes
import com.f_libs.appupdate.base.BaseHttpDownloadManager
import com.f_libs.appupdate.base.bean.DownloadStatus
import com.f_libs.appupdate.config.Constant
import com.f_libs.appupdate.listener.OnButtonClickListener
import com.f_libs.appupdate.listener.OnDownloadListener
import com.f_libs.appupdate.service.DownloadService
import com.f_libs.appupdate.util.ApkUtil
import com.f_libs.appupdate.util.LogUtil
import com.f_libs.appupdate.util.NotNullSingleVar
import com.f_libs.appupdate.util.NullableSingleVar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * @author KnightWood
 */
class DownloadManager private constructor(
    var config: DownloadConfig, val application: Application
) {
    /**
     * 是否正在下载
     */
    var downloading: Boolean = false

    /**
     * 是否正在执行静默下载。
     */
    var isBackDownload: Boolean = false

    /**
     * 下载状态流
     */
    var downloadStateFlow: MutableStateFlow<DownloadStatus> = MutableStateFlow(DownloadStatus.IDLE)

    /**
     * 百分比进度
     */
    val progressFlow: MutableStateFlow<Float> = MutableStateFlow(0f)

    init {
        downloadStateFlow.map {
            downloading = when (it) {
                is DownloadStatus.Done -> {
                    progressFlow.tryEmit(0f)
                    release()
                    false
                }

                is DownloadStatus.Cancel, is DownloadStatus.Error, is DownloadStatus.IDLE -> {
                    progressFlow.tryEmit(0f)
                    false
                }

                is DownloadStatus.Downloading -> {
                    progressFlow.tryEmit(((it.progress / it.max.toDouble() * 100.0).toFloat()))
                    true
                }

                is DownloadStatus.Start -> {
                    true
                }

                DownloadStatus.End -> {
                    progressFlow.tryEmit(0f)
                    false
                }
            }
        }
    }

    /**
     * test whether can download
     */
    fun canDownload(): Boolean {
        val params = checkParams()
        val versionCodeCheck: Boolean = if (config.apkVersionCode == Int.MAX_VALUE) {
            true
        } else {
            config.apkVersionCode > ApkUtil.getVersionCode(application)
        }
        return (params && versionCodeCheck && !downloading)

    }

    /**
     * download file without dialog
     */
    fun checkThenDownload(useBackDownload: Boolean = false) {
        if (canDownload()) {
            directDownload(useBackDownload)
        } else {
            Log.e(TAG, "download: cannot download")
        }
    }

    /**
     * with out check whether can download, so you need to check it yourself
     */
    fun directDownload(useBackDownload: Boolean = false) {
        if (useBackDownload) {
            if (config.apkMD5.isBlank() || config.apkMD5.isEmpty()) {
                throw IllegalArgumentException("apkMD5 can not be empty!")
            }
        }
        isBackDownload = useBackDownload
        DownloadService.startService(application)
    }

    private fun checkParams(): Boolean {
        if (config.apkUrl.isEmpty()) {
            LogUtil.e(TAG, "apkUrl can not be empty!")
            return false
        }
        if (config.apkName.isEmpty()) {
            LogUtil.e(TAG, "apkName can not be empty!")
            return false
        } else {
            if (!config.apkName.endsWith(Constant.APK_SUFFIX)) {
                config.apkName += ".apk"
            }
        }
        if (config.apkDescription.isEmpty()) {
            LogUtil.e(TAG, "apkDescription can not be empty!")
            return false
        }
        Constant.AUTHORITIES = "${application.packageName}.fileProvider"
        return true
    }

    /**
     * 取消下载，如果httpManager不存在，则直接执行then
     *
     * 在静默下载时，不可取消;不处于下载中，不可取消
     *
     * @param then
     */
    fun cancel(then: () -> Unit = {}) {
        if (!isBackDownload && downloading) {
            config.httpManager?.cancel() ?: then()
        }
    }

    /**
     * 取消下载
     */
    fun cancelDirectly() {
        config.httpManager?.cancel()
    }

    /**
     * release objects
     */
    fun release() {
        config.httpManager?.release()
        this.isBackDownload = false
        downloadStateFlow.tryEmit(DownloadStatus.End)
        config.onButtonClickListener = null
        config.onDownloadListeners.clear()
    }

    /**
     * 建议重新配置时，先通过[canModify]检查
     */
    fun reConfig(config: DownloadConfig) {
        this.config.httpManager?.release()
        this.config = config
        this.isBackDownload = false
        this.downloadStateFlow.tryEmit(DownloadStatus.IDLE)
    }

    fun registerButtonListener(onButtonClickListener: OnButtonClickListener) {
        config.onButtonClickListener = onButtonClickListener
    }

    fun registerDownloadListener(onDownloadListener: OnDownloadListener) {
        config.onDownloadListeners.add(onDownloadListener)
    }


    companion object {
        private const val TAG = "DownloadManager"

        @Volatile
        private var instance: DownloadManager? = null

        fun config(application: Application, block: DownloadConfig.() -> Unit): DownloadManager {
            val config = DownloadConfig(application)
            config.block()
            return getInstance(config, application)
        }

        fun isDownloading(): Boolean {
            return instance?.downloading ?: false
        }

        fun existInstance(): DownloadManager? = instance

        /**
         * 尽量不要自行调用，而是使用[config]方法
         *
         * @param config DownloadConfig?
         * @param application Application?
         * @return DownloadManager
         */
        fun getInstance(
            config: DownloadConfig? = null, application: Application? = null
        ): DownloadManager {
            val instanceInner = instance
            if (instanceInner != null) {
                if (config != null && instanceInner.canModify()) {
                    instanceInner.reConfig(config)
                }
            } else {
                if (config == null || application == null) throw IllegalArgumentException("config or application is null")
                synchronized(this) {
                    instance ?: DownloadManager(config, application).also {
                        instance = it
                    }
                }
            }
            return instance!!
        }
    }

    private fun canModify(): Boolean {
        if (
            downloadStateFlow.value == DownloadStatus.End
            || downloadStateFlow.value == DownloadStatus.IDLE
        ) {
            return true
        }
        return false
    }


    class DownloadConfig constructor(application: Application) {
        /**
         * Apk download url
         */
        var apkUrl = ""

        /**
         * Apk file name on disk
         */
        var apkName = ""

        /**
         * The apk versionCode that needs to be downloaded
         * 如果不设置此值，将不检查当前应用的versioncode，直接下载安装更新
         */
        var apkVersionCode = Int.MIN_VALUE

        /**
         * The versionName of the dialog reality
         */
        var apkVersionName = ""

        /**
         * The file path where the Apk is saved eg:
         * /storage/emulated/0/Android/data/ your packageName /cache
         */
        var downloadPath = ApkUtil.getDefaultCachePath(application)

        /**
         * Notification icon resource
         */
        var smallIcon = -1

        /**
         * New version description information
         */
        var apkDescription = ""

        /**
         * Apk Size,Unit MB
         */
        var apkSize = ""

        /**
         * Apk md5 file verification(32-bit) verification repeated download
         */
        var apkMD5 = ""

        /**
         * Apk download manager
         */
        var httpManager: BaseHttpDownloadManager? by NullableSingleVar()

        /**
         * The following are unimportant filed
         */

        /**
         * adapter above Android O notification
         */
        var notificationChannel: NotificationChannel? = null

        /**
         * download listeners
         */
        val onDownloadListeners: MutableList<OnDownloadListener> by NotNullSingleVar(mutableListOf())

        /**
         * dialog button click listener
         */
        var onButtonClickListener: OnButtonClickListener? = null

        /**
         * Whether to show the progress of the notification
         */
        var showNotification = true

        /**
         * Whether the installation page will pop up automatically after the
         * download is complete
         */
        var jumpInstallPage = true

        /**
         * Does the download start tip "Downloading a new version in the
         * background..."
         */
        var showBgdToast = true

        /**
         * Whether to force an upgrade
         */
        var forcedUpgrade = false

        /**
         * Notification id
         */
        var notifyId = Constant.DEFAULT_NOTIFY_ID

        /**
         * whether to tip to user "Currently the latest version!"
         */
        var showNewerToast = false

        fun enableLog(enable: Boolean): DownloadConfig {
            LogUtil.enable(enable)
            return this
        }

        fun registerDownloadListener(onDownloadListener: OnDownloadListener): DownloadConfig {
            this.onDownloadListeners.add(onDownloadListener)
            return this
        }

        fun registerButtonListener(onButtonClickListener: OnButtonClickListener): DownloadConfig {
            this.onButtonClickListener = onButtonClickListener
            return this
        }

        private fun getOneHttpManager(): BaseHttpDownloadManager {
            if (httpManager == null) {
                synchronized(this) {
                    if (httpManager == null) {
                        try {
                            httpManager = HttpDownloadManager(downloadPath)
                        } catch (e: Exception) {
                            Log.e(TAG, "getOneHttpManager: 重复赋值", e)
                        }
                    }
                }
            } else {
                httpManager?.path = downloadPath
            }
            return httpManager!!
        }

        /**
         * 真正的下载，而且可以重写设定好的下载路径
         *
         * @return Flow<DownloadStatus>
         */
        internal fun download(newCachePath: String? = null): Flow<DownloadStatus> {
            newCachePath?.let {
                downloadPath = it
            }
            return getOneHttpManager().download(apkUrl, apkName)
        }

        @Deprecated("未来将会移除")
        var viewConfig: DialogConfig = DialogConfig()

        /**
         * 配置视图
         */
        @Deprecated("未来将会移除，配置的是ColorfulOld类型的弹窗")
        fun configDialog(block: DialogConfig.() -> Unit): DownloadConfig {
            viewConfig.block()
            return this
        }
    }

    @Deprecated("未来将会移除")
    class DialogConfig() {
        /**
         * custom layout id
         */
        var layoutId: Int = -1


        /**
         * dialog background Image resource
         */
        var dialogImage = -1

        /**
         * dialog button background color
         */
        var dialogButtonColor = -1

        /**
         * dialog button text color
         */
        var dialogButtonTextColor = -1

        /**
         * dialog progress bar color and progress-text color
         */
        var dialogProgressBarColor = -1
    }

    /**
     * 兼容旧方式
     *
     * @constructor
     * @property config DownloadConfig
     * @property dialogConfig DialogConfig
     */
    class Builder constructor(activity: Activity) {
        val ctx = activity.application
        var config: DownloadManager.DownloadConfig =
            DownloadManager.DownloadConfig(application = ctx)
        var dialogConfig: DownloadManager.DialogConfig = DownloadManager.DialogConfig()

        fun apkUrl(apkUrl: String): Builder {
            config.apkUrl = apkUrl
            return this
        }

        fun downloadPath(path: String): Builder {
            config.downloadPath = path
            return this
        }

        fun apkName(apkName: String): Builder {
            config.apkName = apkName
            return this
        }

        fun apkVersionCode(apkVersionCode: Int): Builder {
            config.apkVersionCode = apkVersionCode
            return this
        }

        fun apkVersionName(apkVersionName: String): Builder {
            config.apkVersionName = apkVersionName
            return this
        }

        fun showNewerToast(showNewerToast: Boolean): Builder {
            config.showNewerToast = showNewerToast
            return this
        }

        fun smallIcon(smallIcon: Int): Builder {
            config.smallIcon = smallIcon
            return this
        }

        fun apkDescription(apkDescription: String): Builder {
            config.apkDescription = apkDescription
            return this
        }

        fun apkSize(apkSize: String): Builder {
            config.apkSize = apkSize
            return this
        }

        fun apkMD5(apkMD5: String): Builder {
            config.apkMD5 = apkMD5
            return this
        }

        fun httpManager(httpManager: BaseHttpDownloadManager): Builder {
            config.httpManager = httpManager
            return this
        }

        fun notificationChannel(notificationChannel: NotificationChannel): Builder {
            config.notificationChannel = notificationChannel
            return this
        }

        fun onButtonClickListener(onButtonClickListener: OnButtonClickListener): Builder {
            config.onButtonClickListener = onButtonClickListener
            return this
        }

        fun onDownloadListener(onDownloadListener: OnDownloadListener): Builder {
            config.onDownloadListeners.add(onDownloadListener)
            return this
        }

        fun showNotification(showNotification: Boolean): Builder {
            config.showNotification = showNotification
            return this
        }

        fun jumpInstallPage(jumpInstallPage: Boolean): Builder {
            config.jumpInstallPage = jumpInstallPage
            return this
        }

        fun showBgdToast(showBgdToast: Boolean): Builder {
            config.showBgdToast = showBgdToast
            return this
        }

        fun forcedUpgrade(forcedUpgrade: Boolean): Builder {
            config.forcedUpgrade = forcedUpgrade
            return this
        }

        fun notifyId(notifyId: Int): Builder {
            config.notifyId = notifyId
            return this
        }

        fun dialogImage(dialogImage: Int): Builder {
            dialogConfig.dialogImage = dialogImage
            return this
        }

        fun dialogButtonColor(dialogButtonColor: Int): Builder {
            dialogConfig.dialogButtonColor = dialogButtonColor
            return this
        }

        fun dialogButtonTextColor(dialogButtonTextColor: Int): Builder {
            dialogConfig.dialogButtonTextColor = dialogButtonTextColor
            return this
        }

        fun dialogProgressBarColor(dialogProgressBarColor: Int): Builder {
            dialogConfig.dialogProgressBarColor = dialogProgressBarColor
            return this
        }

        fun dialogLayout(@LayoutRes layout: Int): Builder {
            dialogConfig.layoutId = layout
            return this
        }

        fun enableLog(enable: Boolean): Builder {
            config.enableLog(enable)
            return this
        }

        fun build(): DownloadManager {
            config.viewConfig = dialogConfig
            return DownloadManager.getInstance(config, application = ctx)
        }
    }

}

