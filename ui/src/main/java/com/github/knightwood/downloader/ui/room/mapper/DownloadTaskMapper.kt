package com.github.knightwood.downloader.ui.room.mapper

import android.net.Uri
import com.github.knightwood.downloader.core.android.model.AndroidDownloadParams
import com.github.knightwood.downloader.core.android.model.AndroidPath
import com.github.knightwood.downloader.core.android.model.FileEntity
import com.github.knightwood.downloader.core.android.model.TaskParams
import com.github.knightwood.downloader.core.android.util.Constant
import com.github.knightwood.downloader.core.model.ChunkProgress
import com.github.knightwood.downloader.core.model.DownloadStatus
import com.github.knightwood.downloader.core.model.DownloadTaskConf
import com.github.knightwood.downloader.core.model.DownloadTaskSaveState
import com.github.knightwood.downloader.ui.room.entity.DownloadChunkEntity
import com.github.knightwood.downloader.ui.room.entity.DownloadTaskEntity
import com.github.knightwood.downloader.ui.room.entity.DownloadTaskWithChunks
import com.github.knightwood.downloader.ui.room.model.AndroidPathType
import com.github.knightwood.downloader.ui.room.model.DownloadStatusType
import okio.Path.Companion.toPath

/**
 * 数据库记录与 core / core-android 模型互转
 *
 * 数据库只保存可序列化的数据，运行时对象（下载执行器、通知器、下载完成处理器等）
 * 以及 `DownloadTaskConf.path` 这类平台相关属性不写入数据库，
 * 恢复任务时由调用方重新构建。
 */

//<editor-fold desc="AndroidPath <-> 数据表列">

/**
 * 将下载文件存储位置拆分为 (类型, 值) 两列
 */
fun AndroidPath?.toPathColumns(): Pair<AndroidPathType, String?> = when (this) {
    null -> AndroidPathType.NONE to null
    is AndroidPath.Jvm -> AndroidPathType.JVM to path.toString()
    is AndroidPath.MediaStore -> AndroidPathType.MEDIA_STORE to uri.toString()
    is AndroidPath.DocumentFile -> AndroidPathType.DOCUMENT_FILE to uri.toString()
}

/**
 * 将数据表中的类型、值两列还原为 [AndroidPath]
 *
 * 类型为 [AndroidPathType.NONE] 或值为空时返回null，表示使用默认存储
 */
fun DownloadTaskEntity.toAndroidPath(): AndroidPath? = when (pathType) {
    AndroidPathType.NONE -> null
    AndroidPathType.JVM -> pathValue?.toPath()?.let { AndroidPath.Jvm(it) }
    AndroidPathType.MEDIA_STORE -> pathValue?.let { AndroidPath.MediaStore(Uri.parse(it)) }
    AndroidPathType.DOCUMENT_FILE -> pathValue?.let { AndroidPath.DocumentFile(Uri.parse(it)) }
}

//</editor-fold>

//<editor-fold desc="分块进度 <-> ChunkProgress">

fun DownloadChunkEntity.toChunkProgress(): ChunkProgress = ChunkProgress(
    index = chunkIndex,
    start = startOffset,
    end = endOffset,
    downloaded = downloaded,
)

fun ChunkProgress.toChunkEntity(taskId: Int): DownloadChunkEntity = DownloadChunkEntity(
    taskId = taskId,
    chunkIndex = index,
    startOffset = start,
    endOffset = end,
    downloaded = downloaded,
)

/**
 * 将下载中状态里的分块进度转换为分块记录
 */
fun DownloadStatus.DOWNLOADING.toChunkEntities(taskId: Int): List<DownloadChunkEntity> =
    chunks.map { it.toChunkEntity(taskId) }

//</editor-fold>

//<editor-fold desc="记录 -> core / core-android 模型">

/**
 * 还原下载任务配置
 *
 * `DownloadTaskConf.path` 在 Android 平台不可用，此处只还原目录与文件名
 */
fun DownloadTaskEntity.toTaskConf(): DownloadTaskConf = DownloadTaskConf(
    url = this@toTaskConf.url,
    md5 = this@toTaskConf.md5,
    dirPath = this@toTaskConf.dirPath?.toPath() ?: Constant.emptyPath,
    name = this@toTaskConf.name,
    isSupportChunkDownload = this@toTaskConf.isSupportChunkDownload,
    chunkCount = this@toTaskConf.chunkCount,
    minChunkSize = this@toTaskConf.minChunkSize,
)

/**
 * 还原下载任务参数，用于重新提交到下载服务
 */
fun DownloadTaskEntity.toDownloadParams(): AndroidDownloadParams = AndroidDownloadParams().apply {
    fileEntity = FileEntity(
        url = this@toDownloadParams.url,
        mimeType = this@toDownloadParams.mimeType ?: "tmp",
        name = this@toDownloadParams.name,
        size = this@toDownloadParams.size,
        description = this@toDownloadParams.description,
        version = this@toDownloadParams.version,
        versionCode = this@toDownloadParams.versionCode,
        date = this@toDownloadParams.date,
        md5 = this@toDownloadParams.md5,
    )
    taskParams = TaskParams().apply {
        enableNotification = this@toDownloadParams.enableNotification
        path = this@toDownloadParams.toAndroidPath()
        isSupportChunkDownload = this@toDownloadParams.isSupportChunkDownload
        this.chunkCount = this@toDownloadParams.chunkCount
        this.minChunkSize = this@toDownloadParams.minChunkSize
    }
}

/**
 * 将记录中的总长度、已下载长度、速度组装为下载中状态，用于断点续传
 */
fun DownloadTaskEntity.toDownloadingStatus(
    chunks: List<DownloadChunkEntity> = emptyList(),
): DownloadStatus.DOWNLOADING = DownloadStatus.DOWNLOADING(
    length = totalSize,
    progress = downloadedSize,
    snapSpeed = Double.NaN,
    averageSpeed = Double.NaN,
    chunks = chunks.map { it.toChunkProgress() },
)

/**
 * 还原下载状态，[chunks] 为分块进度记录
 */
fun DownloadTaskEntity.toDownloadStatus(
    chunks: List<DownloadChunkEntity> = emptyList(),
): DownloadStatus = when (status) {
    DownloadStatusType.IDLE -> DownloadStatus.IDLE
    DownloadStatusType.STARTING -> DownloadStatus.STARTING
    DownloadStatusType.DOWNLOADING -> toDownloadingStatus(chunks)
    DownloadStatusType.PAUSED -> DownloadStatus.PAUSED
    DownloadStatusType.FINISHED -> DownloadStatus.FINISHED
    DownloadStatusType.CANCELED -> DownloadStatus.CANCELED
    DownloadStatusType.ERROR -> DownloadStatus.ERROR(
        IllegalStateException(errorMessage ?: "download failed")
    )
}

/**
 * 还原任务快照，可直接用于生成新的 `DownloadTask` 并恢复进度
 *
 * 任务当前状态可能为暂停/取消/失败，但只要保存过下载进度，
 * 就把进度放入 `progress` 字段，以便调用 `DownloadTask.restoreProgress`
 */
fun DownloadTaskWithChunks.toSaveState(): DownloadTaskSaveState {
    val finalState = task.toDownloadStatus(chunks)
    val progress = if (finalState is DownloadStatus.DOWNLOADING || task.downloadedSize > 0) {
        task.toDownloadingStatus(chunks)
    } else {
        null
    }
    return DownloadTaskSaveState(
        id = task.taskId,
        conf = task.toTaskConf(),
        progress = progress,
        finalState = finalState,
    )
}

//</editor-fold>

//<editor-fold desc="core / core-android 模型 -> 记录">

/**
 * 由 Android 任务参数生成 core 的下载任务配置
 *
 * 与 `DownloadService` 内部生成任务的逻辑保持一致：
 * Android 平台的文件位置记录在 [TaskParams.path] 中，`dirPath` 无实际意义。
 */
fun AndroidDownloadParams.toDownloadTaskConf(): DownloadTaskConf = DownloadTaskConf(
    url = fileEntity.url,
    md5 = fileEntity.md5,
    name = fileEntity.name,
    dirPath = Constant.emptyPath,
    isSupportChunkDownload = taskParams.isSupportChunkDownload,
    chunkCount = taskParams.chunkCount,
    minChunkSize = taskParams.minChunkSize,
)

/**
 * 将新建任务的参数转换为数据库记录
 *
 * 新任务还没有任何下载进度，状态为 [DownloadStatus.IDLE]。
 *
 * @param queueOrder 任务在等待队列中的顺序，-1表示不在等待队列中
 */
fun AndroidDownloadParams.toTaskEntity(
    taskId: Int,
    queueOrder: Int = -1,
    createTime: Long = System.currentTimeMillis(),
): DownloadTaskEntity = DownloadTaskSaveState(
    id = taskId,
    conf = toDownloadTaskConf(),
    progress = null,
    finalState = DownloadStatus.IDLE,
).toTaskEntity(params = this, queueOrder = queueOrder, createTime = createTime)

/**
 * 将任务快照转换为数据库记录
 *
 * @param params 任务在 core-android 中的参数，提供文件信息、存储位置和通知配置，
 * 为null时只使用快照中的任务配置
 * @param queueOrder 任务在等待队列中的顺序，-1表示不在等待队列中
 */
fun DownloadTaskSaveState.toTaskEntity(
    params: AndroidDownloadParams? = null,
    queueOrder: Int = -1,
    createTime: Long = System.currentTimeMillis(),
    startTime: Long? = null,
    endTime: Long? = null,
    updateTime: Long = System.currentTimeMillis(),
): DownloadTaskEntity {
    val fileEntity = params?.fileEntity
    val taskParams = params?.taskParams
    val downloading = progress
    val (pathType, pathValue) = taskParams?.path.toPathColumns()
    return DownloadTaskEntity(
        taskId = id,
        url = conf.url,
        mimeType = fileEntity?.mimeType,
        name = fileEntity?.name ?: conf.name,
        size = fileEntity?.size,
        description = fileEntity?.description,
        version = fileEntity?.version,
        versionCode = fileEntity?.versionCode,
        date = fileEntity?.date,
        md5 = fileEntity?.md5 ?: conf.md5,
        dirPath = conf.dirPath.toString(),
        isSupportChunkDownload = taskParams?.isSupportChunkDownload ?: conf.isSupportChunkDownload,
        chunkCount = taskParams?.chunkCount ?: conf.chunkCount,
        minChunkSize = taskParams?.minChunkSize ?: conf.minChunkSize,
        pathType = pathType,
        pathValue = pathValue,
        // TaskParams 未单独指定时读取到的是 Downloader 全局配置，这里记录的是实际生效值
        enableNotification = taskParams?.enableNotification,
        status = DownloadStatusType.of(finalState),
        errorMessage = (finalState as? DownloadStatus.ERROR)?.e?.message,
        totalSize = downloading?.length ?: 0,
        downloadedSize = downloading?.progress ?: 0,
//        snapSpeed = downloading?.snapSpeed ?: Double.NaN,
//        averageSpeed = downloading?.averageSpeed ?: Double.NaN,
        queueOrder = queueOrder,
        createTime = createTime,
        startTime = startTime,
        endTime = endTime,
        updateTime = updateTime,
    )
}

/**
 * 将任务快照中的分块进度转换为分块记录
 */
fun DownloadTaskSaveState.toChunkEntities(): List<DownloadChunkEntity> =
    progress?.toChunkEntities(id) ?: emptyList()

//</editor-fold>
