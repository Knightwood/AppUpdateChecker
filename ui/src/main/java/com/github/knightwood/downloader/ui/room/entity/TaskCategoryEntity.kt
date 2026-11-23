package com.github.knightwood.downloader.ui.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_category_table")
data class TaskCategoryEntity(
    @PrimaryKey
    val id: Int,
    val name: String,
    val createAt: Long,
)
