package com.github.knightwood.downloader.ui.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_task_table")
data class DownloadTaskEntity(
    @PrimaryKey val recordId: Long,
    @ColumnInfo val taskId: Int,
    @ColumnInfo val url: String,
    @ColumnInfo val totalSize: Long,
    @ColumnInfo val downloadedSize: Long,
    @ColumnInfo val startTime: Long,
    @ColumnInfo val endTime: Long,
)
