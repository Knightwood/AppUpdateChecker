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
import com.github.knightwood.downloader.core.model.DownloadStatus
import com.github.knightwood.downloader.core.util.HttpConstant
import com.github.knightwood.downloader.core.task.DownloadTask

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
}

/**
 * 文件信息
 */
class FileEntity(
    var url: String = "",
    var mimeType: String = "tmp",
    var name: String = Constant.fileName(mimeType),
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

class TaskParams {
    /**
     * 是否启用状态栏通知
     *
     * 将覆盖[Downloader.enableNotification]
     */
    var enableNotification: Boolean? = null
        get() = field ?: Downloader.enableNotification

    /**
     * 记录文件存储位置
     */
    var path: AndroidPath? = null

    var isSupportChunkDownload: Boolean = false

    /**
     * 分块数量
     */
    var chunkCount: Int = 0

    /**
     * 分块大小,单位byte
     */
    var minChunkSize: Long = HttpConstant.MIN_CHUNK_SIZE

    /**
     * 上次的下载进度，用于执行旧任务时恢复进度，断点续传
     * 为了恢复任务进度，除了传递此参数，还可以创建任务后使用[DownloadTask.restoreProgress]恢复进度
     */
    var lastDownloadProgress : DownloadStatus.DOWNLOADING? = null

    /**
     * 提供自定义下载执行器
     */
    var downloadExecutor: IDownloadExecutor? = null
        get() = field ?: Downloader.downloadExecutor

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


    companion object {

        val updateApkConf = TaskParams().apply {
            postHandler = IDownloadedHandler { context, path, id, conf ->
                ApkUtil.installApk(context, authorities(context), path!!)
            }
            enableNotification = true
            mNotifier = ApkUpdateNotifierImpl()
        }
    }
}

