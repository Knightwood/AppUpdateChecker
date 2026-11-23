package com.github.knightwood.downloader.core.android.model

import com.github.knightwood.downloader.core.android.IDownloadedHandler
import com.github.knightwood.downloader.core.executor.IDownloadExecutor
import com.github.knightwood.downloader.core.android.util.Constant
import com.github.knightwood.downloader.core.android.Downloader
import com.github.knightwood.downloader.core.android.service.INotifier
import com.github.knightwood.downloader.core.model.DownloadProgressRecord
import com.github.knightwood.downloader.core.util.HttpConstant
import com.github.knightwood.downloader.core.task.DownloadTask
import com.github.knightwood.downloader.core.util.DownloadFormat

data class AndroidDownloadParams(
    var fileEntity: FileEntity = FileEntity(),
    var taskParams: TaskParams = TaskParams(),
) {

    fun file(action: FileEntity.() -> Unit): AndroidDownloadParams {
        fileEntity = FileEntity().apply(action)
        return this
    }

    fun task(action: TaskParams.() -> Unit): AndroidDownloadParams {
        taskParams = TaskParams().apply(action)
        return this
    }
}

/**
 * 文件信息
 * @param url 文件下载地址
 * @param mimeType 文件类型
 * @param name 文件名
 * @param size 文件大小（可选）
 * @param description 文件描述（可选）
 * @param version 文件版本（可选）
 * @param versionCode 文件版本号（可选）
 * @param date 文件日期（可选）
 * @param fileUID 不同的任务实例可以下载同一个文件，使用此字段标识文件唯一性（可选）
 * @param md5 文件md5值（可选）
 * @property sizeText 文件大小文本，比如1MB，100MB
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
    var fileUID: String? = null,
    var md5: String? = null,
) {
    /** 人类可读的size 比如1MB */
    var sizeText: String? = null
        get() {
            if (size != null) {
                return DownloadFormat.formatByteSize(size)
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
     * 例如 /storage/emulated/0/Android/data/com.example.app/files/Download/example.apk
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
    var lastDownloadProgress: DownloadProgressRecord? = null

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

}

