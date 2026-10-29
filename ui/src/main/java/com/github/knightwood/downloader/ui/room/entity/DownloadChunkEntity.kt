package com.github.knightwood.downloader.ui.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 下载任务的分块下载进度表
 *
 * 对应 core 中的 `DownloadStatus.DOWNLOADING.chunks`（`List<ChunkProgress>`），
 * 仅分块下载的任务才有记录，用于恢复分块下载的断点进度。
 *
 * 主键为 (task_id, chunk_index)，一个任务的一个分块只有一条记录；
 * 任务被删除时，其分块记录随外键级联删除。
 */
@Entity(
    tableName = DownloadChunkEntity.TABLE_NAME,
    primaryKeys = [
        DownloadChunkEntity.COLUMN_TASK_ID,
        DownloadChunkEntity.COLUMN_CHUNK_INDEX,
    ],
    foreignKeys = [
        ForeignKey(
            entity = DownloadTaskEntity::class,
            parentColumns = [DownloadTaskEntity.COLUMN_TASK_ID],
            childColumns = [DownloadChunkEntity.COLUMN_TASK_ID],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = [DownloadChunkEntity.COLUMN_TASK_ID])]
)
data class DownloadChunkEntity(
    /** 所属任务id，关联 [DownloadTaskEntity.taskId] */
    @ColumnInfo(name = DownloadChunkEntity.COLUMN_TASK_ID)
    val taskId: Int,

    /** 分块序号，对应 `ChunkProgress.index` */
    @ColumnInfo(name = DownloadChunkEntity.COLUMN_CHUNK_INDEX)
    val chunkIndex: Int,

    /** 分块在下载文件中的起始偏移，对应 `ChunkProgress.start` */
    @ColumnInfo(name = "start_offset")
    val startOffset: Long,

    /** 分块在下载文件中的结束偏移，对应 `ChunkProgress.end` */
    @ColumnInfo(name = "end_offset")
    val endOffset: Long,

    /** 已下载到的绝对偏移，对应 `ChunkProgress.downloaded` */
    @ColumnInfo(name = "downloaded")
    val downloaded: Long,
) {
    companion object {
        const val TABLE_NAME = "download_chunk_table"
        const val COLUMN_TASK_ID = "task_id"
        const val COLUMN_CHUNK_INDEX = "chunk_index"
    }
}
