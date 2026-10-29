package com.github.knightwood.downloader.ui.room.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * 下载任务记录及其分块进度
 *
 * Room 通过两次查询（任务 + 分块）组装，需在 DAO 中配合 `@Transaction` 使用，
 * 以保证查询期间数据一致。
 */
data class DownloadTaskWithChunks(
    @Embedded
    val task: DownloadTaskEntity,

    @Relation(
        parentColumn = DownloadTaskEntity.COLUMN_TASK_ID,
        entityColumn = DownloadChunkEntity.COLUMN_TASK_ID,
    )
    val chunks: List<DownloadChunkEntity> = emptyList(),
)
