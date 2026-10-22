package com.github.knightwood.downloader.core.android.model

import com.github.knightwood.downloader.core.android.IDownloadedHandler
import com.github.knightwood.downloader.core.executor.IDownloadExecutor
import com.github.knightwood.downloader.core.util.toDataUnit
import com.github.knightwood.downloader.core.android.util.Constant
import com.github.knightwood.downloader.core.android.Downloader
import com.github.knightwood.downloader.core.android.Downloader.authorities
import com.github.knightwood.downloader.core.android.service.ApkUpdateNotifierImpl
import com.github.knightwood.downloader.core.android.service.INotifier
import com.github.knightwood.downloader.core.android.util.ApkUtil
import com.github.knightwood.downloader.core.filestorage.FileStorage
import java.time.LocalDateTime


class AndroidDownloadParams() {

    var fileEntity: FileEntity = FileEntity()
    fun file(action: FileEntity.() -> Unit): AndroidDownloadParams {
        fileEntity = FileEntity().apply(action)
        return this
    }

    var taskParams: TaskParams = TaskParams()
    fun task(action: TaskParams.() -> Unit): AndroidDownloadParams {
        taskParams = TaskParams().apply(action)
        return this
    }

    var logConfig: LogConfig = LogConfig()
    fun log(action: LogConfig.() -> Unit): AndroidDownloadParams {
        logConfig = LogConfig().apply(action)
        return this
    }
}

/**
 * 文件信息
 */
class FileEntity(
    var id: Int = 0,
    var name: String = Constant.DEFAULT_FILE_NAME_PREFIX + "_" + LocalDateTime.now().toString(),
    var url: String? = null,
    /** 字节数 */
    var size: Long? = null,
    var description: String? = null,
    var version: String? = null,
    var versionCode: Int? = null,
    var date: String? = null,
    var md5: String? = null,
) {
    /** 人类可读的size 比如1MB */
    var sizeText: String? = null
        get() {
            if (size != null) {
                return size.toDataUnit
            } else {
                return field
            }
        }
}

class LogConfig(
    var enable: Boolean = false,
    var prefix: String? = null,
)

class TaskParams {
    /**
     * 是否启用状态栏通知
     *
     * 将覆盖[Downloader.enableNotification]
     */
    var enableNotification: Boolean? = null
        get() = field ?: Downloader.enableNotification

    /**
     * 提供下载通知
     *
     * 将覆盖[Downloader.mNotifier]
     */
    var mNotifier: INotifier? = null
        get() = field ?: Downloader.mNotifier

    /**
     * 下载完成后使用此接口实现处理文件
     *
     * 将覆盖[Downloader]中的全局配置
     */
    var postHandler: IDownloadedHandler? = null
        get() = field ?: Downloader.postHandler

    /**
     * 相对于externalCacheDir路径, 用于存储apk文件的路径
     */
    var saveRelativePath: String? = null

    /**
     * 提供自定义下载执行器
     */
    var downloadExecutor: IDownloadExecutor? = null
        get() = field ?: Downloader.downloadExecutor

    /**
     * 提供自定义文件处理
     */
    var fileStorage: FileStorage? = null


    companion object {

        val updateApkConf = TaskParams().apply {
            postHandler = IDownloadedHandler { context, path, id, conf ->
                ApkUtil.installApk(context, authorities(context), path.toFile())
            }
            enableNotification = true
            mNotifier = ApkUpdateNotifierImpl()
        }
    }
}

