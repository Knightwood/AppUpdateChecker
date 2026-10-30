package com.github.knightwood.downloader.ui.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.github.knightwood.downloader.ui.room.model.AndroidPathType
import com.github.knightwood.downloader.ui.room.model.DownloadStatusType

/**
 * 下载任务记录表
 *
 * 一条记录对应一个下载任务，字段来源：
 * - 文件信息：core-android 的 `AndroidDownloadParams.FileEntity`
 * - 任务配置：core 的 `DownloadTaskConf`（由 `AndroidDownloadParams.TaskParams` 生成）
 * - 存储位置：core-android 的 `AndroidPath`
 * - 状态进度：core 的 `DownloadStatus`，分块进度见 [DownloadChunkEntity]
 *
 * 注意：`DownloadTaskConf` 中的执行器、通知器、下载完成处理器都是运行时对象，
 * 无法持久化，恢复任务时由调用方重新注入。
 *
 * 表以 [taskId]（即 core 中 `DownloadTask.id`）为主键，与任务调度器中
 * `taskId -> DownloadTask` 的一对一关系保持一致。
 */
@Entity(
    tableName = DownloadTaskEntity.TABLE_NAME,
    indices = [
        Index(value = [DownloadTaskEntity.COLUMN_STATUS]),
        Index(value = [DownloadTaskEntity.COLUMN_QUEUE_ORDER]),
        Index(value = [DownloadTaskEntity.COLUMN_CREATE_TIME]),
    ]
)
data class DownloadTaskEntity(
    /**
     * 任务id，与 core 中 `DownloadTask.id` 一致，由提交任务的一方指定，全局唯一
     */
    @PrimaryKey
    @ColumnInfo(name = DownloadTaskEntity.COLUMN_TASK_ID)
    val taskId: Int,

    //<editor-fold desc="文件信息(FileEntity)">
    /** 下载地址 */
    @ColumnInfo(name = "url")
    val url: String,

    /** 文件类型，如 application/vnd.android.package-archive */
    @ColumnInfo(name = "mime_type")
    val mimeType: String? = null,

    /** 文件名称 */
    @ColumnInfo(name = "name")
    val name: String,

    /** 文件字节数，未探测到总长度时为null */
    @ColumnInfo(name = "size")
    val size: Long? = null,

    /** 文件描述 */
    @ColumnInfo(name = "description")
    val description: String? = null,

    /** 文件版本名 */
    @ColumnInfo(name = "version")
    val version: String? = null,

    /** 文件版本号 */
    @ColumnInfo(name = "version_code")
    val versionCode: Int? = null,

    /** 文件日期（字符串，格式由服务端决定） */
    @ColumnInfo(name = "date")
    val date: String? = null,

    /** 文件md5 */
    @ColumnInfo(name = "md5")
    val md5: String? = null,
    //</editor-fold>

    //<editor-fold desc="任务配置(DownloadTaskConf)">
    /**
     * 下载文件保存目录（okio Path 字符串）
     *
     * Android 平台读写文件通常使用 [AndroidPathType]，此时该字段仅为占位值
     */
    @ColumnInfo(name = "dir_path")
    val dirPath: String? = null,

    /** 是否启用分块下载 */
    @ColumnInfo(name = "is_support_chunk_download")
    val isSupportChunkDownload: Boolean = false,

    /** 分块数量 */
    @ColumnInfo(name = "chunk_count")
    val chunkCount: Int = 0,

    /** 最小分块大小，单位byte */
    @ColumnInfo(name = "min_chunk_size")
    val minChunkSize: Long = 0,
    //</editor-fold>

    //<editor-fold desc="文件存储位置(AndroidPath)">
    /** 存储位置类型 */
    @ColumnInfo(name = "path_type")
    val pathType: AndroidPathType = AndroidPathType.NONE,

    /** 存储位置值：JVM 类型为文件路径，MediaStore/DocumentFile 类型为 uri 字符串 */
    @ColumnInfo(name = "path_value")
    val pathValue: String? = null,
    //</editor-fold>

    //<editor-fold desc="通知配置(TaskParams)">
    /**
     * 是否显示下载状态通知
     *
     * null 表示未单独指定，使用 `Downloader.enableNotification` 全局配置
     */
    @ColumnInfo(name = "enable_notification")
    val enableNotification: Boolean? = null,
    //</editor-fold>

    //<editor-fold desc="下载状态/进度(DownloadStatus)">
    /** 当前下载状态 */
    @ColumnInfo(name = "status")
    val status: DownloadStatusType = DownloadStatusType.IDLE,

    /** 下载失败时的异常信息，仅 [DownloadStatusType.ERROR] 状态有值 */
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,

    /** 文件总长度，对应 `DownloadStatus.DOWNLOADING.length` */
    @ColumnInfo(name = "total_size")
    val totalSize: Long = 0,

    /** 已下载长度，对应 `DownloadStatus.DOWNLOADING.progress` */
    @ColumnInfo(name = "downloaded_size")
    val downloadedSize: Long = 0,

//    /** 瞬时下载速度，对应 `DownloadStatus.DOWNLOADING.snapSpeed` */
//    @ColumnInfo(name = "snap_speed")
//    val snapSpeed: Double = Double.NaN,
//
//    /** 平均下载速度，对应 `DownloadStatus.DOWNLOADING.averageSpeed` */
//    @ColumnInfo(name = "average_speed")
//    val averageSpeed: Double = Double.NaN,

    /**
     * 任务在等待队列中的顺序，对应 `TaskSchedulerSaveState.pending` 的下标
     *
     * -1 表示任务当前不在等待队列中
     */
    @ColumnInfo(name = "queue_order")
    val queueOrder: Int = -1,
    //</editor-fold>

    //<editor-fold desc="时间(毫秒时间戳)">
    /** 任务创建时间 */
    @ColumnInfo(name = "create_time")
    val createTime: Long = System.currentTimeMillis(),

    /** 任务开始下载时间，未开始下载过为null */
    @ColumnInfo(name = "start_time")
    val startTime: Long? = null,

    /** 任务结束时间（完成/取消/失败），未结束为null */
    @ColumnInfo(name = "end_time")
    val endTime: Long? = null,

    /** 最后更新时间（状态、进度变化时间） */
    @ColumnInfo(name = "update_time")
    val updateTime: Long = System.currentTimeMillis(),
    //</editor-fold>
) {
    companion object {
        const val TABLE_NAME = "download_task_table"
        const val COLUMN_TASK_ID = "task_id"
        const val COLUMN_STATUS = "status"
        const val COLUMN_QUEUE_ORDER = "queue_order"
        const val COLUMN_CREATE_TIME = "create_time"
    }
}
